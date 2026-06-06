package com.antigravity.companalysis.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
 * Unit-tests the pure discrepancy engine (no Spring/DB), driven by the real reconciliation core so the
 * two deterministic engines are exercised together: a lower-credibility source is flagged only when it
 * differs from the trusted anchor by ≥10%.
 */
class DiscrepancyAnalyzerTest {

    private static final UUID ENTITY = UUID.randomUUID();

    @Test
    void flagsMaterialGapAgainstAnchorButIgnoresSmallOnes() {
        List<ConsolidatedParameter> consolidated = ReconciliationService.consolidateObservations(List.of(
                // CARPET_AREA: RERA carpet 900 (anchor) vs portal super-builtup 1200 → +33.33% (flagged)
                obs(ParameterType.CARPET_AREA, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE, "900"),
                obs(ParameterType.CARPET_AREA, SourceSystem.MAGICBRICKS, CredibilityTier.INDICATIVE, "1200"),
                // PRICE: user 35600 (anchor) vs listing 38900 → +9.27% (within tolerance, not flagged)
                obs(ParameterType.PRICE, SourceSystem.USER, CredibilityTier.USER_PROVIDED, "35600"),
                obs(ParameterType.PRICE, SourceSystem.MAGICBRICKS, CredibilityTier.INDICATIVE, "38900")));

        List<Discrepancy> discrepancies = DiscrepancyAnalyzer.analyze(consolidated);

        assertThat(discrepancies).hasSize(1);
        Discrepancy d = discrepancies.get(0);
        assertThat(d.parameter()).isEqualTo(ParameterType.CARPET_AREA);
        assertThat(d.anchorTier()).isEqualTo(CredibilityTier.AUTHORITATIVE);
        assertThat(d.anchorValue()).isEqualByComparingTo("900");
        assertThat(d.otherSource()).isEqualTo(SourceSystem.MAGICBRICKS);
        assertThat(d.otherTier()).isEqualTo(CredibilityTier.INDICATIVE);
        assertThat(d.otherValue()).isEqualByComparingTo("1200");
        assertThat(d.deltaPct()).isEqualByComparingTo("33.33");
    }

    @Test
    void noDiscrepancyWhenOnlyAnchorTierObserved() {
        List<ConsolidatedParameter> consolidated = ReconciliationService.consolidateObservations(List.of(
                obs(ParameterType.CARPET_AREA, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE, "900")));

        assertThat(DiscrepancyAnalyzer.analyze(consolidated)).isEmpty();
    }

    private static ObservationView obs(ParameterType parameter, SourceSystem source, CredibilityTier tier,
                                       String normalizedValue) {
        return new ObservationView(UUID.randomUUID(), ENTITY, parameter, normalizedValue, "u", AreaBasis.CARPET,
                new BigDecimal(normalizedValue), "u", source, tier, new BigDecimal("1.0"),
                source.name().toLowerCase() + ":ref", null, Instant.now());
    }
}
