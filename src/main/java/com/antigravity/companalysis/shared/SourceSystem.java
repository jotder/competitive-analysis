package com.antigravity.companalysis.shared;

/**
 * Where an observation came from, with its default credibility tier. Only RERA is authoritative;
 * listing portals and developer sites are indicative (advertised); user input and what-if assumptions
 * carry their own tiers.
 */
public enum SourceSystem {

    RERA(CredibilityTier.AUTHORITATIVE),
    DEVELOPER_SITE(CredibilityTier.INDICATIVE),
    MAGICBRICKS(CredibilityTier.INDICATIVE),
    USER(CredibilityTier.USER_PROVIDED),
    ASSUMPTION(CredibilityTier.ASSUMPTION);

    private final CredibilityTier defaultTier;

    SourceSystem(CredibilityTier defaultTier) {
        this.defaultTier = defaultTier;
    }

    public CredibilityTier defaultTier() {
        return defaultTier;
    }
}
