package com.antigravity.companalysis.analytics;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One project's standing in the amenity-vs-price comparison: its consolidated price, its amenity set, and
 * how that price compares with the cheapest comparable project (the {@code baseline}). Deterministically
 * computed by {@link AmenityPremiumAnalyzer}; the agent narrates whether the premium is justified but never
 * computes it (ADR-0001).
 *
 * @param baseline        true for the cheapest comparable project — the reference everything else is measured
 *                        against ({@code pricePremiumPct == 0}, {@code extraAmenities} empty)
 * @param pricePremiumPct signed relative price gap vs the baseline, {@code (price-basePrice)/basePrice*100}
 * @param extraAmenities  amenities this project offers that the baseline does not (case-insensitive, sorted)
 */
public record AmenityPremium(UUID entityId, String label, BigDecimal price, String priceUnit,
                             int amenityCount, List<String> amenities, boolean baseline,
                             BigDecimal pricePremiumPct, List<String> extraAmenities) {

    public AmenityPremium {
        amenities = (amenities == null) ? List.of() : List.copyOf(amenities);
        extraAmenities = (extraAmenities == null) ? List.of() : List.copyOf(extraAmenities);
    }
}
