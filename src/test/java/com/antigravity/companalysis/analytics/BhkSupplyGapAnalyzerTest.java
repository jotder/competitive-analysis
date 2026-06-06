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
 * Pure-core tests of the BHK-supply-gap engine: for every configuration in the market it counts how many
 * projects offer it (rarest first) and names those that do not, matching case- and space-insensitively.
 * Runs over the real {@link ReconciliationService} consolidation (no Spring/DB).
 */
class BhkSupplyGapAnalyzerTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();
    private static final Map<UUID, String> LABELS = Map.of(A, "Lodha", B, "Godrej", C, "Hiranandani");

    @Test
    void countsSupplyPerConfigurationRarestFirst() {
        List<ConsolidatedParameter> consolidated = ReconciliationService.consolidateObservations(List.of(
                bhk(A, "2BHK, 3BHK"), bhk(B, "3BHK, 4BHK"), bhk(C, "3 BHK")));

        List<BhkSupplyGap> out = BhkSupplyGapAnalyzer.analyze(consolidated, LABELS);

        // supplyCount asc, then configuration name: 2BHK(1), 4BHK(1), 3BHK(3)
        assertThat(out).extracting(BhkSupplyGap::configuration).containsExactly("2BHK", "4BHK", "3BHK");

        BhkSupplyGap twoBhk = out.get(0);
        assertThat(twoBhk.supplyCount()).isEqualTo(1);
        assertThat(twoBhk.marketSize()).isEqualTo(3);
        assertThat(twoBhk.offeredBy()).containsExactly("Lodha");
        assertThat(twoBhk.missingFrom()).containsExactlyInAnyOrder("Godrej", "Hiranandani");
        assertThat(twoBhk.isGap()).isTrue();

        BhkSupplyGap threeBhk = out.get(2);
        assertThat(threeBhk.supplyCount()).isEqualTo(3); // matched across "3BHK" and "3 BHK"
        assertThat(threeBhk.missingFrom()).isEmpty();
        assertThat(threeBhk.isGap()).isFalse();
    }

    @Test
    void abstainsWhenOnlyOneProjectHasABhkConfiguration() {
        List<ConsolidatedParameter> consolidated =
                ReconciliationService.consolidateObservations(List.of(bhk(A, "2BHK, 3BHK")));

        assertThat(BhkSupplyGapAnalyzer.analyze(consolidated, LABELS)).isEmpty();
    }

    private static ObservationView bhk(UUID entity, String csv) {
        return new ObservationView(UUID.randomUUID(), entity, ParameterType.BHK_CONFIG, csv, null,
                AreaBasis.CARPET, null, null, SourceSystem.DEVELOPER_SITE, CredibilityTier.INDICATIVE,
                new BigDecimal("0.8"), "devsite:config", null, Instant.now());
    }
}
