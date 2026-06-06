package com.antigravity.companalysis.reconciliation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.antigravity.companalysis.catalog.CatalogService;
import com.antigravity.companalysis.catalog.CatalogService.ObservationView;
import com.antigravity.companalysis.catalog.CatalogService.SessionView;
import com.antigravity.companalysis.reconciliation.ConsolidatedParameter.Contribution;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;

/**
 * Consolidates a session's append-only observations into one canonical {@link ConsolidatedParameter} per
 * (entity, parameter), <b>by credibility — never by flat-averaging</b> (ADR-0002).
 *
 * <p>The policy is deliberately simple and deterministic so it is unit-testable and the LLM never touches
 * it (ADR-0001):
 * <ol>
 *   <li>Take the observations at the highest {@link CredibilityTier#rank()} (the <em>anchor tier</em>).
 *       Lower-tier observations are kept as provenance but never move the number.</li>
 *   <li>One anchor value (or several that agree) → {@link ConsolidationMethod#ANCHOR}.</li>
 *   <li>Several anchor-tier numeric values that disagree within {@value #TOLERANCE_PCT_LITERAL}% →
 *       {@link ConsolidationMethod#WEIGHTED} (confidence-weighted mean, still within the tier).</li>
 *   <li>Disagree by more → {@link ConsolidationMethod#RANGE} (a band, no single trusted point).</li>
 *   <li>Non-numeric parameter → {@link ConsolidationMethod#SINGLE} (the most-credible value verbatim).</li>
 * </ol>
 *
 * <p>Selecting the anchor tier uses CxO's {@code rank()} (where {@code USER_PROVIDED > INDICATIVE}), which
 * is the reverse of the kernel enum's ordinal ordering — the consolidation that confirms the R1.5 reshape
 * finding (see {@code intelligence.internal.CredibilityTiers}). Pure: {@link #consolidateObservations} has
 * no Spring/DB dependency and is tested directly.
 */
@Service
@Transactional(readOnly = true)
public class ReconciliationService {

    static final String TOLERANCE_PCT_LITERAL = "5";
    /** Max relative spread (percent) among anchor-tier values still treated as WEIGHTED rather than RANGE. */
    private static final BigDecimal TOLERANCE_PCT = new BigDecimal(TOLERANCE_PCT_LITERAL);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CatalogService catalog;

    public ReconciliationService(CatalogService catalog) {
        this.catalog = catalog;
    }

    /** Consolidate every (entity, parameter) for a session's factual observations. */
    public List<ConsolidatedParameter> consolidate(UUID sessionId) {
        SessionView session = catalog.getSession(sessionId);
        return consolidateObservations(catalog.baseObservations(session.entityIds()));
    }

    /** Consolidate a session's factual observations for a single parameter. */
    public List<ConsolidatedParameter> consolidate(UUID sessionId, ParameterType parameter) {
        return consolidate(sessionId).stream().filter(c -> c.parameter() == parameter).toList();
    }

    // ── pure core (no Spring / DB) ───────────────────────────────────────────────

    /** Group by (entity, parameter) preserving encounter order, then reconcile each group. */
    public static List<ConsolidatedParameter> consolidateObservations(List<ObservationView> observations) {
        Map<UUID, Map<ParameterType, List<ObservationView>>> grouped = new LinkedHashMap<>();
        for (ObservationView o : observations) {
            grouped.computeIfAbsent(o.entityId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(o.parameter(), k -> new ArrayList<>())
                    .add(o);
        }
        List<ConsolidatedParameter> out = new ArrayList<>();
        grouped.forEach((entityId, byParam) ->
                byParam.forEach((parameter, group) -> out.add(reconcileOne(entityId, parameter, group))));
        return out;
    }

    static ConsolidatedParameter reconcileOne(UUID entityId, ParameterType parameter,
                                              List<ObservationView> group) {
        int topRank = group.stream().mapToInt(o -> o.tier().rank()).max().orElseThrow();
        List<ObservationView> top = group.stream().filter(o -> o.tier().rank() == topRank).toList();
        CredibilityTier anchorTier = top.get(0).tier();
        List<Contribution> contributions = contributions(group, topRank);

        boolean numeric = top.stream().allMatch(o -> o.normalizedValue() != null);
        if (!numeric) {
            ObservationView best = top.stream()
                    .max(Comparator.comparingDouble(ReconciliationService::confidence))
                    .orElse(top.get(0));
            return new ConsolidatedParameter(entityId, parameter, ConsolidationMethod.SINGLE,
                    null, null, null, null, unitOf(best), anchorTier, display(best), contributions);
        }

        BigDecimal low = top.stream().map(ObservationView::normalizedValue).min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal high = top.stream().map(ObservationView::normalizedValue).max(Comparator.naturalOrder()).orElseThrow();
        String unit = unitOf(top.get(0));

        if (top.size() == 1 || low.compareTo(high) == 0) {
            return new ConsolidatedParameter(entityId, parameter, ConsolidationMethod.ANCHOR,
                    low, low, high, BigDecimal.ZERO.setScale(2), unit, anchorTier, plain(low), contributions);
        }

        BigDecimal mean = weightedMean(top);
        BigDecimal spreadPct = high.subtract(low)
                .divide(mean, 6, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);

        if (spreadPct.compareTo(TOLERANCE_PCT) <= 0) {
            BigDecimal value = mean.setScale(2, RoundingMode.HALF_UP);
            return new ConsolidatedParameter(entityId, parameter, ConsolidationMethod.WEIGHTED,
                    value, low, high, spreadPct, unit, anchorTier, plain(value), contributions);
        }
        return new ConsolidatedParameter(entityId, parameter, ConsolidationMethod.RANGE,
                null, low, high, spreadPct, unit, anchorTier, plain(low) + "–" + plain(high), contributions);
    }

    private static List<Contribution> contributions(List<ObservationView> group, int topRank) {
        List<Contribution> out = new ArrayList<>();
        for (ObservationView o : group) {
            out.add(new Contribution(o.source(), o.tier(), o.normalizedValue(), display(o), unitOf(o),
                    confidence(o), o.sourceRef(), o.tier().rank() == topRank));
        }
        return out;
    }

    private static BigDecimal weightedMean(List<ObservationView> top) {
        BigDecimal num = BigDecimal.ZERO;
        BigDecimal den = BigDecimal.ZERO;
        for (ObservationView o : top) {
            BigDecimal w = (o.confidence() == null || o.confidence().signum() <= 0) ? BigDecimal.ONE : o.confidence();
            num = num.add(o.normalizedValue().multiply(w));
            den = den.add(w);
        }
        if (den.signum() == 0) { // all weights zero — fall back to a simple mean
            BigDecimal sum = top.stream().map(ObservationView::normalizedValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.divide(new BigDecimal(top.size()), 4, RoundingMode.HALF_UP);
        }
        return num.divide(den, 4, RoundingMode.HALF_UP);
    }

    private static double confidence(ObservationView o) {
        return o.confidence() == null ? 1.0 : o.confidence().doubleValue();
    }

    private static String unitOf(ObservationView o) {
        return o.normalizedUnit() != null ? o.normalizedUnit() : o.rawUnit();
    }

    private static String display(ObservationView o) {
        return o.normalizedValue() != null ? plain(o.normalizedValue()) : o.rawValue();
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
