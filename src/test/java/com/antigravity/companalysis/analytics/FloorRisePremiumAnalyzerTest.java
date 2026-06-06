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
 * Pure-core tests of the floor-rise engine: it ranks each project's per-floor surcharge highest-first and
 * measures every project's gap over the cheapest. Runs over the real {@link ReconciliationService}
 * consolidation (no Spring/DB).
 */
class FloorRisePremiumAnalyzerTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final Map<UUID, String> LABELS = Map.of(A, "Lodha", B, "Godrej", C, "Hiranandani");

    @Test
    void ranksHighestFirstWithGapOverTheLowest() {
        List<ConsolidatedParameter> consolidated = ReconciliationService.consolidateObservations(List.of(
                floorRise(A, "200"), floorRise(B, "250"), floorRise(C, "300")));

        List<FloorRisePremium> out = FloorRisePremiumAnalyzer.analyze(consolidated, LABELS);

        assertThat(out).extracting(FloorRisePremium::label)
                .containsExactly("Hiranandani", "Godrej", "Lodha"); // 300, 250, 200

        FloorRisePremium top = out.get(0);
        assertThat(top.highest()).isTrue();
        assertThat(top.lowest()).isFalse();
        assertThat(top.deltaVsLowestPct()).isEqualByComparingTo("50"); // (300-200)/200

        FloorRisePremium bottom = out.get(2);
        assertThat(bottom.lowest()).isTrue();
        assertThat(bottom.deltaVsLowestPct()).isEqualByComparingTo("0");
        assertThat(out.get(1).deltaVsLowestPct()).isEqualByComparingTo("25"); // (250-200)/200
    }

    @Test
    void abstainsWhenOnlyOneProjectHasAFloorRisePremium() {
        List<ConsolidatedParameter> consolidated =
                ReconciliationService.consolidateObservations(List.of(floorRise(A, "200")));

        assertThat(FloorRisePremiumAnalyzer.analyze(consolidated, LABELS)).isEmpty();
    }

    private static ObservationView floorRise(UUID entity, String value) {
        return new ObservationView(UUID.randomUUID(), entity, ParameterType.FLOOR_RISE_PREMIUM, value,
                "INR_PER_SQFT_PER_FLOOR", AreaBasis.CARPET, new BigDecimal(value), "INR_PER_SQFT_PER_FLOOR",
                SourceSystem.DEVELOPER_SITE, CredibilityTier.INDICATIVE, new BigDecimal("0.8"),
                "devsite:floorrise", null, Instant.now());
    }
}
