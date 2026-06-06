package com.antigravity.companalysis.analytics;

import java.math.BigDecimal;
import java.util.UUID;

import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;

/**
 * A material gap between what an authoritative source records and what a less-credible source advertises
 * for the same (entity, parameter) — e.g. RERA carpet area vs a portal's super-builtup, or a listing price
 * that overstates the user-confirmed figure. Deterministically detected by {@link DiscrepancyAnalyzer}
 * against the reconciled anchor; the agent narrates it but never computes it (ADR-0001).
 *
 * @param anchorValue  the trusted (anchor-tier) value the discrepancy is measured against
 * @param otherValue   the lower-credibility value that disagrees
 * @param deltaPct     signed relative gap {@code (otherValue-anchorValue)/anchorValue*100} — positive when
 *                     the less-credible source overstates
 */
public record Discrepancy(UUID entityId, ParameterType parameter,
                          CredibilityTier anchorTier, BigDecimal anchorValue, String anchorSourceRef,
                          SourceSystem otherSource, CredibilityTier otherTier, BigDecimal otherValue,
                          String otherSourceRef, String unit, BigDecimal deltaPct) {
}
