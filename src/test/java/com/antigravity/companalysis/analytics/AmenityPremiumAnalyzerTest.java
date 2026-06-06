package com.antigravity.companalysis.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.antigravity.companalysis.catalog.CatalogService.ObservationView;
import com.antigravity.companalysis.reconciliation.ConsolidatedParameter;
import com.antigravity.companalysis.reconciliation.ReconciliationService;
import com.antigravity.companalysis.shared.AreaBasis;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;

/**
 * Pure-core tests of the amenity-premium engine: it pairs each project's reconciled price with its amenity
 * set and measures the premium over the cheapest comparable project, naming the amenities that premium buys.
 * Runs over the real {@link ReconciliationService} consolidation (no Spring/DB).
 */
class AmenityPremiumAnalyzerTest {

    private static final UUID CHEAPER = UUID.randomUUID();
    private static final UUID PRICIER = UUID.randomUUID();
    private static final Map<UUID, String> LABELS =
            Map.of(CHEAPER, "Lodha Amara", PRICIER, "Godrej Emerald");

    @Test
    void measuresPremiumAndNamesTheExtraAmenitiesOverTheCheapestBaseline() {
        List<ConsolidatedParameter> consolidated = ReconciliationService.consolidateObservations(List.of(
                price(CHEAPER, "30000"), amenities(CHEAPER, "Gym, Pool"),
                price(PRICIER, "36000"), amenities(PRICIER, "Pool, Gym, Clubhouse, Concierge")));

        List<AmenityPremium> out = AmenityPremiumAnalyzer.analyze(consolidated, LABELS);

        assertThat(out).hasSize(2);
        AmenityPremium baseline = out.get(0); // sorted by price ascending
        assertThat(baseline.label()).isEqualTo("Lodha Amara");
        assertThat(baseline.baseline()).isTrue();
        assertThat(baseline.pricePremiumPct()).isEqualByComparingTo("0");
        assertThat(baseline.extraAmenities()).isEmpty();
        assertThat(baseline.amenities()).containsExactly("Gym", "Pool");

        AmenityPremium premium = out.get(1);
        assertThat(premium.label()).isEqualTo("Godrej Emerald");
        assertThat(premium.baseline()).isFalse();
        assertThat(premium.pricePremiumPct()).isEqualByComparingTo("20"); // (36000-30000)/30000
        assertThat(premium.amenityCount()).isEqualTo(4);
        assertThat(premium.extraAmenities()).containsExactly("Clubhouse", "Concierge");
    }

    @Test
    void abstainsWhenOnlyOneProjectIsComparable() {
        List<ConsolidatedParameter> consolidated = ReconciliationService.consolidateObservations(List.of(
                price(CHEAPER, "30000"), amenities(CHEAPER, "Gym, Pool")));

        assertThat(AmenityPremiumAnalyzer.analyze(consolidated, LABELS)).isEmpty();
    }

    // ── fixtures ──────────────────────────────────────────────────────────────────

    private static ObservationView price(UUID entity, String value) {
        return new ObservationView(UUID.randomUUID(), entity, ParameterType.PRICE, value,
                "INR_PER_CARPET_SQFT", AreaBasis.CARPET, new BigDecimal(value), "INR_PER_CARPET_SQFT",
                SourceSystem.USER, CredibilityTier.USER_PROVIDED, new BigDecimal("0.9"), "user:form/price",
                null, Instant.now());
    }

    private static ObservationView amenities(UUID entity, String csv) {
        return new ObservationView(UUID.randomUUID(), entity, ParameterType.AMENITIES, csv, null,
                AreaBasis.CARPET, null, null, SourceSystem.DEVELOPER_SITE, CredibilityTier.INDICATIVE,
                new BigDecimal("0.8"), "devsite:amenities", null, Instant.now());
    }
}
