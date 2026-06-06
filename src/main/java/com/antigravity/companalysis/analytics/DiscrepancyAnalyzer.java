package com.antigravity.companalysis.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.antigravity.companalysis.reconciliation.ConsolidatedParameter;
import com.antigravity.companalysis.reconciliation.ConsolidatedParameter.Contribution;
import com.antigravity.companalysis.reconciliation.ReconciliationService;

/**
 * The RERA-vs-listing discrepancy engine: for each reconciled parameter it measures how far every
 * <em>lower-credibility</em> observation sits from the trusted anchor, and flags the material ones
 * (≥ {@value #MATERIAL_PCT_LITERAL}%). This is a second, independent exercise of CxO's credibility
 * <em>ordering</em> — the anchor is whatever the reconciliation chose as most credible (by CxO
 * {@code rank()}), and discrepancies are everything ranked below it that disagrees.
 *
 * <p>Pure and deterministic (ADR-0001): {@link #analyze(List)} has no Spring/DB dependency. Reads the
 * {@code reconciliation} module's output; never the LLM.
 */
@Service
@Transactional(readOnly = true)
public class DiscrepancyAnalyzer {

    static final String MATERIAL_PCT_LITERAL = "10";
    /** Minimum absolute relative gap (percent) to report as a discrepancy. */
    private static final BigDecimal MATERIAL_PCT = new BigDecimal(MATERIAL_PCT_LITERAL);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    private final ReconciliationService reconciliation;

    public DiscrepancyAnalyzer(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    /** Find material discrepancies across a whole session. */
    public List<Discrepancy> findDiscrepancies(UUID sessionId) {
        return analyze(reconciliation.consolidate(sessionId));
    }

    // ── pure core (no Spring / DB) ───────────────────────────────────────────────

    public static List<Discrepancy> analyze(List<ConsolidatedParameter> consolidated) {
        List<Discrepancy> out = new ArrayList<>();
        for (ConsolidatedParameter c : consolidated) {
            BigDecimal anchor = anchorValue(c);
            if (anchor == null || anchor.signum() == 0) {
                continue; // non-numeric or no usable anchor — nothing to measure against
            }
            String anchorRef = anchorRef(c);
            for (Contribution k : c.contributions()) {
                if (k.usedInAnchor() || k.value() == null) {
                    continue; // only lower-credibility numeric observations can be discrepant
                }
                BigDecimal deltaPct = k.value().subtract(anchor)
                        .divide(anchor, 6, RoundingMode.HALF_UP)
                        .multiply(HUNDRED)
                        .setScale(2, RoundingMode.HALF_UP);
                if (deltaPct.abs().compareTo(MATERIAL_PCT) >= 0) {
                    out.add(new Discrepancy(c.entityId(), c.parameter(), c.anchorTier(), anchor, anchorRef,
                            k.source(), k.tier(), k.value(), k.sourceRef(), c.unit(), deltaPct));
                }
            }
        }
        return out;
    }

    /** The numeric figure to measure against: the consolidated point, or a band's midpoint for RANGE. */
    private static BigDecimal anchorValue(ConsolidatedParameter c) {
        if (c.value() != null) {
            return c.value();
        }
        if (c.low() != null && c.high() != null) {
            return c.low().add(c.high()).divide(TWO, 4, RoundingMode.HALF_UP);
        }
        return null;
    }

    private static String anchorRef(ConsolidatedParameter c) {
        return c.contributions().stream()
                .filter(Contribution::usedInAnchor)
                .map(Contribution::sourceRef)
                .findFirst()
                .orElse(null);
    }
}
