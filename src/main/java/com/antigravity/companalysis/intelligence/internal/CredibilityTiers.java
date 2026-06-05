package com.antigravity.companalysis.intelligence.internal;

import com.gamma.agentkernel.tool.CredibilityTier;

/**
 * Maps CxO's {@link com.antigravity.companalysis.shared.CredibilityTier provenance tier} onto the
 * kernel's {@link CredibilityTier} for the {@link com.gamma.agentkernel.tool.Evidence} a tool emits.
 *
 * <h3>R1 reshape evidence — read before changing this (do NOT "fix" it here)</h3>
 * This mapping is the deliberate exercise the kernel's headline {@code 0.x} decision was waiting for
 * (R1 §5, ADR-0004). Two facts it establishes:
 *
 * <ul>
 *   <li><b>The vocabulary fits.</b> Every CxO tier name (AUTHORITATIVE / USER_PROVIDED / INDICATIVE /
 *       ASSUMPTION) is also a kernel enum constant, so the map is 1:1 by name and the
 *       {@code Evidence.tierLabel} escape hatch is <em>not</em> needed for vocabulary. (We still pass the
 *       CxO name as {@code tierLabel} so provenance is exact and auditable.)</li>
 *   <li><b>The ordering does NOT fit.</b> CxO ranks trust as
 *       {@code AUTHORITATIVE(3) > USER_PROVIDED(2) > INDICATIVE(1) > ASSUMPTION(0)} (see
 *       {@code shared.CredibilityTier#rank()}). The kernel enum's <em>declaration order</em> (which
 *       {@code UccConfidenceEstimator} reads via {@code ordinal()}, lower = more trusted) is
 *       {@code AUTHORITATIVE < OFFICIAL < INDICATIVE < DERIVED < USER_PROVIDED < ASSUMPTION} — i.e. the
 *       kernel treats INDICATIVE as <em>more</em> credible than USER_PROVIDED, the <em>reverse</em> of CxO.</li>
 * </ul>
 *
 * <p><b>Conclusion (banked for R1.5):</b> ring-1 should not bake a single universal trust ordering into
 * the enum's declaration order. The {@code 1.0} reshape candidate is to keep the enum as a vocabulary but
 * let each app supply its own rank/Comparator (as CxO's {@code rank()} already does), rather than promote
 * the enum to an app-extensible interface (the vocabulary itself fit). CxO selects its "best" observation
 * by its own {@code rank()} — see {@link ComparisonGridTool} — so this app never relies on the kernel's
 * ordinal ordering, which keeps it correct today while the ring-1 decision is pending.
 */
final class CredibilityTiers {

    private CredibilityTiers() {
    }

    /** Map a CxO provenance tier to the kernel enum (1:1 by name; ordering intentionally not relied on). */
    static CredibilityTier toKernel(com.antigravity.companalysis.shared.CredibilityTier tier) {
        return switch (tier) {
            case AUTHORITATIVE -> CredibilityTier.AUTHORITATIVE;
            case USER_PROVIDED -> CredibilityTier.USER_PROVIDED;
            case INDICATIVE -> CredibilityTier.INDICATIVE;
            case ASSUMPTION -> CredibilityTier.ASSUMPTION;
        };
    }
}
