package com.antigravity.companalysis.analytics;

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

import com.antigravity.companalysis.reconciliation.ConsolidatedParameter;
import com.antigravity.companalysis.reconciliation.ReconciliationService;
import com.antigravity.companalysis.shared.ParameterType;

/**
 * The floor-rise engine: across a session's projects, it ranks each project's consolidated
 * {@link ParameterType#FLOOR_RISE_PREMIUM} (the per-floor surcharge) and measures every project's gap over
 * the cheapest. Surfaces who charges the steepest premium to go higher — a competitive lever the agent can
 * narrate. Deterministic (ADR-0001); the figures come straight from the reconciled anchor.
 *
 * <p>Needs ≥2 projects carrying a numeric floor-rise premium; otherwise {@link #analyze} returns empty and
 * the tool abstains. Pure core has no Spring/DB dependency.
 */
@Service
@Transactional(readOnly = true)
public class FloorRisePremiumAnalyzer {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ReconciliationService reconciliation;

    public FloorRisePremiumAnalyzer(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    /** Rank floor-rise premiums across a whole session (labels supplied by the caller). */
    public List<FloorRisePremium> compare(UUID sessionId, Map<UUID, String> labels) {
        return analyze(reconciliation.consolidate(sessionId), labels);
    }

    // ── pure core (no Spring / DB) ───────────────────────────────────────────────

    public static List<FloorRisePremium> analyze(List<ConsolidatedParameter> consolidated,
                                                 Map<UUID, String> labels) {
        Map<UUID, ConsolidatedParameter> byEntity = new LinkedHashMap<>();
        for (ConsolidatedParameter c : consolidated) {
            if (c.parameter() == ParameterType.FLOOR_RISE_PREMIUM && AnalyticsValues.numeric(c) != null) {
                byEntity.put(c.entityId(), c);
            }
        }
        if (byEntity.size() < 2) {
            return List.of(); // need at least two projects with a floor-rise premium to rank
        }

        List<BigDecimal> values = byEntity.values().stream().map(AnalyticsValues::numeric).toList();
        BigDecimal lowest = values.stream().min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal highest = values.stream().max(Comparator.naturalOrder()).orElseThrow();

        List<FloorRisePremium> out = new ArrayList<>();
        byEntity.forEach((id, c) -> {
            BigDecimal v = AnalyticsValues.numeric(c);
            BigDecimal deltaPct = lowest.signum() == 0
                    ? null
                    : v.subtract(lowest).divide(lowest, 6, RoundingMode.HALF_UP)
                            .multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            out.add(new FloorRisePremium(id, labels.getOrDefault(id, id.toString()), v, c.unit(),
                    c.anchorTier(), v.compareTo(lowest) == 0, v.compareTo(highest) == 0, deltaPct));
        });
        out.sort(Comparator.comparing(FloorRisePremium::premium).reversed());
        return out;
    }
}
