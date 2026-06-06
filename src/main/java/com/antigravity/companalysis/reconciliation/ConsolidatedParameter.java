package com.antigravity.companalysis.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;

/**
 * The deterministic consolidation of one entity's many observations of a single {@link ParameterType}
 * into a canonical figure (ADR-0002). Produced by {@link ReconciliationService}; consumed by the
 * analytics engines and the agent's tools.
 *
 * <p><b>Credibility, not arithmetic, decides the number.</b> The consolidation is computed entirely from
 * the observations at the highest {@link CredibilityTier#rank()} ({@code anchorTier}); lower-tier
 * observations appear in {@link #contributions()} with {@code usedInAnchor=false} so the provenance is
 * complete, but they never move the value. This is the place CxO's tier <em>ordering</em> is exercised in
 * consolidation (R1.5 reshape evidence): it relies on {@code rank()}, never on the kernel enum's ordinal.
 *
 * @param value      the canonical numeric value ({@code null} for {@link ConsolidationMethod#RANGE} —
 *                   the band has no single trusted point — and for {@link ConsolidationMethod#SINGLE}
 *                   non-numeric parameters)
 * @param low        lowest top-tier numeric value ({@code null} when not numeric)
 * @param high       highest top-tier numeric value ({@code null} when not numeric)
 * @param spreadPct  relative disagreement across the top tier, {@code (high-low)/mean*100}
 *                   ({@code null} when not numeric or only one top-tier value)
 * @param displayValue a human-facing rendering for any parameter (numeric point, band, or raw text)
 */
public record ConsolidatedParameter(UUID entityId, ParameterType parameter, ConsolidationMethod method,
                                    BigDecimal value, BigDecimal low, BigDecimal high, BigDecimal spreadPct,
                                    String unit, CredibilityTier anchorTier, String displayValue,
                                    List<Contribution> contributions) {

    public ConsolidatedParameter {
        contributions = (contributions == null) ? List.of() : List.copyOf(contributions);
    }

    /** True when the top tier held several values that disagreed beyond tolerance (a {@link ConsolidationMethod#RANGE}). */
    public boolean isBand() {
        return method == ConsolidationMethod.RANGE;
    }

    /**
     * One source's observation that fed (or was considered for) this consolidation.
     *
     * @param usedInAnchor whether this observation was at the top credibility tier and therefore shaped
     *                     the consolidated value; {@code false} observations are kept for provenance only.
     */
    public record Contribution(SourceSystem source, CredibilityTier tier, BigDecimal value,
                               String displayValue, String unit, double confidence, String sourceRef,
                               boolean usedInAnchor) {
    }
}
