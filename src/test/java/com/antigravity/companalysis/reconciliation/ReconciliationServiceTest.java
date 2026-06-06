package com.antigravity.companalysis.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.antigravity.companalysis.catalog.CatalogService.ObservationView;
import com.antigravity.companalysis.shared.AreaBasis;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;

/**
 * Unit-tests the pure reconciliation core (no Spring/DB) — the deterministic credibility consolidation of
 * ADR-0002. The headline test pins the R1.5 reshape evidence: consolidation chooses the anchor by CxO's
 * {@code rank()} ({@code USER_PROVIDED > INDICATIVE}), even when the lower-ranked source has higher
 * confidence, and it <b>never flat-averages across tiers</b>.
 */
class ReconciliationServiceTest {

    private static final UUID ENTITY = UUID.randomUUID();

    @Test
    void anchorsOnCxoRankNotConfidenceAndNeverAveragesAcrossTiers() {
        // INDICATIVE has the HIGHER confidence; USER_PROVIDED outranks it. Rank must win.
        ConsolidatedParameter price = only(ReconciliationService.consolidateObservations(List.of(
                obs(ParameterType.PRICE, SourceSystem.MAGICBRICKS, CredibilityTier.INDICATIVE, "38900", "0.95"),
                obs(ParameterType.PRICE, SourceSystem.USER, CredibilityTier.USER_PROVIDED, "35600", "0.60"))));

        assertThat(price.method()).isEqualTo(ConsolidationMethod.ANCHOR);
        assertThat(price.anchorTier()).isEqualTo(CredibilityTier.USER_PROVIDED);
        assertThat(price.value()).isEqualByComparingTo("35600");      // the user value, verbatim
        assertThat(price.displayValue()).isEqualTo("35600");
        assertThat(price.value()).isNotEqualByComparingTo("37250");   // NOT the average of the two tiers
        assertThat(price.contributions()).anySatisfy(c -> {
            assertThat(c.tier()).isEqualTo(CredibilityTier.INDICATIVE);
            assertThat(c.usedInAnchor()).isFalse();                   // the listing was set aside, not blended
        });
    }

    @Test
    void weightedMeanWithinTopTierWhenValuesDisagreeSlightly() {
        ConsolidatedParameter c = only(ReconciliationService.consolidateObservations(List.of(
                obs(ParameterType.PRICE, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE, "1000", "1.0"),
                obs(ParameterType.PRICE, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE, "1020", "1.0"))));

        assertThat(c.method()).isEqualTo(ConsolidationMethod.WEIGHTED);
        assertThat(c.value()).isEqualByComparingTo("1010.00");        // mean, within the AUTHORITATIVE tier
        assertThat(c.low()).isEqualByComparingTo("1000");
        assertThat(c.high()).isEqualByComparingTo("1020");
        assertThat(c.spreadPct()).isEqualByComparingTo("1.98");       // (20/1010)*100
    }

    @Test
    void rangeBandWhenTopTierDisagreesMaterially() {
        ConsolidatedParameter c = only(ReconciliationService.consolidateObservations(List.of(
                obs(ParameterType.PRICE, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE, "1000", "1.0"),
                obs(ParameterType.PRICE, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE, "1200", "1.0"))));

        assertThat(c.method()).isEqualTo(ConsolidationMethod.RANGE);
        assertThat(c.value()).isNull();                               // a band has no single trusted point
        assertThat(c.low()).isEqualByComparingTo("1000");
        assertThat(c.high()).isEqualByComparingTo("1200");
        assertThat(c.displayValue()).isEqualTo("1000–1200");
    }

    @Test
    void singleForNonNumericParameterTakesMostCredibleVerbatim() {
        ConsolidatedParameter c = only(ReconciliationService.consolidateObservations(List.of(
                rawObs(ParameterType.BHK_CONFIG, SourceSystem.MAGICBRICKS, CredibilityTier.INDICATIVE, "2,3 BHK"),
                rawObs(ParameterType.BHK_CONFIG, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE, "2,3,4 BHK"))));

        assertThat(c.method()).isEqualTo(ConsolidationMethod.SINGLE);
        assertThat(c.anchorTier()).isEqualTo(CredibilityTier.AUTHORITATIVE);
        assertThat(c.displayValue()).isEqualTo("2,3,4 BHK");
        assertThat(c.value()).isNull();
    }

    @Test
    void singleAuthoritativeValueAnchorsWithZeroSpread() {
        ConsolidatedParameter c = only(ReconciliationService.consolidateObservations(List.of(
                obs(ParameterType.CARPET_AREA, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE, "900", "1.0"))));

        assertThat(c.method()).isEqualTo(ConsolidationMethod.ANCHOR);
        assertThat(c.value()).isEqualByComparingTo("900");
        assertThat(c.spreadPct()).isEqualByComparingTo("0");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private static ConsolidatedParameter only(List<ConsolidatedParameter> all) {
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private static ObservationView obs(ParameterType parameter, SourceSystem source, CredibilityTier tier,
                                       String normalizedValue, String confidence) {
        return new ObservationView(UUID.randomUUID(), ENTITY, parameter, normalizedValue, "u", AreaBasis.CARPET,
                new BigDecimal(normalizedValue), "u", source, tier, new BigDecimal(confidence),
                source.name().toLowerCase() + ":ref", null, Instant.now());
    }

    private static ObservationView rawObs(ParameterType parameter, SourceSystem source, CredibilityTier tier,
                                          String rawValue) {
        return new ObservationView(UUID.randomUUID(), ENTITY, parameter, rawValue, null, null, null, null,
                source, tier, new BigDecimal("1.0"), source.name().toLowerCase() + ":ref", null, Instant.now());
    }
}
