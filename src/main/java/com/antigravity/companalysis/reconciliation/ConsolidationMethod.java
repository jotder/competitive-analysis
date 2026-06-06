package com.antigravity.companalysis.reconciliation;

/**
 * How a parameter's many observations were consolidated into one canonical figure (ADR-0002 —
 * <b>reconcile, never flat-average</b>). Consolidation always works <em>within the most-credible tier
 * only</em>; lower-tier observations are recorded as context but never move the number.
 */
public enum ConsolidationMethod {

    /** Exactly one observation at the top credibility tier (or all top-tier values agree). */
    ANCHOR,

    /** Several top-tier numeric observations that disagree only slightly — combined by a
     *  confidence-weighted mean <em>within that tier</em> (never across tiers). */
    WEIGHTED,

    /** Several top-tier numeric observations that disagree materially — reported as a [low, high]
     *  band with no single trusted point. */
    RANGE,

    /** A non-numeric parameter (e.g. possession date, BHK config): the most-credible value is taken
     *  as-is, with no arithmetic. */
    SINGLE
}
