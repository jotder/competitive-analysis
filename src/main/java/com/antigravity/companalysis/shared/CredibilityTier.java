package com.antigravity.companalysis.shared;

/**
 * How much a value can be trusted (ADR-0002). {@code rank()} orders tiers for grid/consolidation:
 * higher = more trustworthy.
 */
public enum CredibilityTier {

    AUTHORITATIVE(3),
    USER_PROVIDED(2),
    INDICATIVE(1),
    ASSUMPTION(0);

    private final int rank;

    CredibilityTier(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
