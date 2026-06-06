package com.antigravity.companalysis.analytics;

import java.math.BigDecimal;
import java.util.UUID;

import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;

/**
 * One project's {@link ParameterType#FLOOR_RISE_PREMIUM} (the surcharge per floor as a unit rises) and how
 * it ranks against the cheapest comparable project. Deterministically computed by
 * {@link FloorRisePremiumAnalyzer} from the reconciled anchor; the agent narrates the competitive picture,
 * never the figure (ADR-0001).
 *
 * @param premium          the consolidated per-floor surcharge (anchor value)
 * @param tier             the credibility tier the figure was anchored at
 * @param lowest           true if this is the lowest floor-rise premium in the comparison
 * @param highest          true if this is the highest
 * @param deltaVsLowestPct relative gap over the lowest project, {@code (premium-lowest)/lowest*100}
 *                         ({@code null} when the lowest is zero)
 */
public record FloorRisePremium(UUID entityId, String label, BigDecimal premium, String unit,
                               CredibilityTier tier, boolean lowest, boolean highest,
                               BigDecimal deltaVsLowestPct) {
}
