package com.antigravity.companalysis.intelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.antigravity.companalysis.catalog.CatalogService;
import com.antigravity.companalysis.catalog.CatalogService.EntityView;
import com.antigravity.companalysis.catalog.CatalogService.ObservationView;
import com.antigravity.companalysis.catalog.CatalogService.SessionView;
import com.antigravity.companalysis.intelligence.internal.AmenityPremiumCapability;
import com.antigravity.companalysis.intelligence.internal.AmenityPremiumTool;
import com.antigravity.companalysis.intelligence.internal.BhkSupplyGapCapability;
import com.antigravity.companalysis.intelligence.internal.BhkSupplyGapTool;
import com.antigravity.companalysis.intelligence.internal.ComparisonGridTool;
import com.antigravity.companalysis.intelligence.internal.DiscrepancyTool;
import com.antigravity.companalysis.intelligence.internal.DocumentSearchTool;
import com.antigravity.companalysis.intelligence.internal.FloorRiseCapability;
import com.antigravity.companalysis.intelligence.internal.FloorRiseTool;
import com.antigravity.companalysis.intelligence.internal.ReconciliationTool;
import com.antigravity.companalysis.reconciliation.ReconciliationService;
import com.antigravity.companalysis.shared.AreaBasis;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.spring.AgentKernelAutoConfiguration;

/**
 * Proves the analytics backfill end-to-end: the three cross-entity engines (amenity-premium, floor-rise,
 * BHK-supply-gap) dispatch through {@link CxoAgent} over the auto-wired orchestrator, with the real
 * {@link ReconciliationService} in the path and a (mocked) two-project catalog. No database/Docker.
 */
class MarketAnalyticsCapabilitiesIntegrationTest {

    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID LODHA = UUID.randomUUID();
    private static final UUID GODREJ = UUID.randomUUID();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentKernelAutoConfiguration.class))
            .withBean(CatalogService.class, MarketAnalyticsCapabilitiesIntegrationTest::stubbedCatalog)
            .withBean(ReconciliationService.class)
            .withBean(ComparisonGridTool.class)
            .withBean(ReconciliationTool.class)
            .withBean(DiscrepancyTool.class)
            .withBean(AmenityPremiumTool.class)
            .withBean(FloorRiseTool.class)
            .withBean(BhkSupplyGapTool.class)
            .withBean(DocumentSearchTool.class)
            .withBean(AmenityPremiumCapability.class)
            .withBean(FloorRiseCapability.class)
            .withBean(BhkSupplyGapCapability.class)
            .withBean(CxoAgent.class);

    @Test
    void measuresAmenityPremiumOverTheCheapestProject() {
        runner.run(ctx -> {
            AgentResult result = ctx.getBean(CxoAgent.class)
                    .analyzeAmenityPremium(SESSION, "what does the premium buy?");

            assertThat(result.status()).isEqualTo(AgentResult.Status.OK);
            assertThat(result.answer())
                    .contains("baseline: Lodha Amara")
                    .contains("Godrej Emerald")
                    .contains("+20%")
                    .contains("Clubhouse")
                    .contains("Concierge");
            assertThat(result.evidence()).isNotEmpty();
        });
    }

    @Test
    void ranksFloorRisePremium() {
        runner.run(ctx -> {
            AgentResult result = ctx.getBean(CxoAgent.class)
                    .analyzeFloorRise(SESSION, "who charges most to go higher?");

            assertThat(result.status()).isEqualTo(AgentResult.Status.OK);
            assertThat(result.answer())
                    .contains("Godrej Emerald")
                    .contains("+50% vs lowest")
                    .contains("lowest");
            assertThat(result.evidence()).isNotEmpty();
        });
    }

    @Test
    void mapsBhkSupplyGaps() {
        runner.run(ctx -> {
            AgentResult result = ctx.getBean(CxoAgent.class)
                    .analyzeBhkSupplyGap(SESSION, "where are the supply gaps?");

            assertThat(result.status()).isEqualTo(AgentResult.Status.OK);
            assertThat(result.answer())
                    .contains("2BHK: offered by 1/2")
                    .contains("missing from Godrej Emerald")
                    .contains("3BHK: offered by 2/2")
                    .contains("4BHK: offered by 1/2");
            assertThat(result.evidence()).isNotEmpty();
        });
    }

    @Test
    void abstainsWhenASingleProjectCannotBeCompared() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AgentKernelAutoConfiguration.class))
                .withBean(CatalogService.class, MarketAnalyticsCapabilitiesIntegrationTest::singleProjectCatalog)
                .withBean(ReconciliationService.class)
                .withBean(ComparisonGridTool.class)
                .withBean(ReconciliationTool.class)
                .withBean(DiscrepancyTool.class)
                .withBean(AmenityPremiumTool.class)
                .withBean(FloorRiseTool.class)
                .withBean(BhkSupplyGapTool.class)
                .withBean(DocumentSearchTool.class)
                .withBean(AmenityPremiumCapability.class)
                .withBean(FloorRiseCapability.class)
                .withBean(BhkSupplyGapCapability.class)
                .withBean(CxoAgent.class)
                .run(ctx -> {
                    CxoAgent agent = ctx.getBean(CxoAgent.class);
                    assertThat(agent.analyzeAmenityPremium(SESSION, "?").status())
                            .isEqualTo(AgentResult.Status.UNAVAILABLE);
                    assertThat(agent.analyzeFloorRise(SESSION, "?").status())
                            .isEqualTo(AgentResult.Status.UNAVAILABLE);
                    assertThat(agent.analyzeBhkSupplyGap(SESSION, "?").status())
                            .isEqualTo(AgentResult.Status.UNAVAILABLE);
                });
    }

    // ── stubbed catalog ──────────────────────────────────────────────────────────

    private static CatalogService stubbedCatalog() {
        CatalogService catalog = mock(CatalogService.class);
        when(catalog.getSession(SESSION)).thenReturn(new SessionView(SESSION, "Thane West showdown",
                "Thane West", List.of(LODHA, GODREJ), Instant.now()));
        when(catalog.entities(anyCollection())).thenReturn(List.of(
                new EntityView(LODHA, "Lodha", "Amara", "Thane West", "RERA-L", false),
                new EntityView(GODREJ, "Godrej", "Emerald", "Thane West", "RERA-G", false)));
        when(catalog.baseObservations(anyCollection())).thenReturn(List.of(
                price(LODHA, "30000"), amenities(LODHA, "Gym, Pool"),
                floorRise(LODHA, "200"), bhk(LODHA, "2BHK, 3BHK"),
                price(GODREJ, "36000"), amenities(GODREJ, "Pool, Gym, Clubhouse, Concierge"),
                floorRise(GODREJ, "300"), bhk(GODREJ, "3BHK, 4BHK")));
        return catalog;
    }

    private static CatalogService singleProjectCatalog() {
        CatalogService catalog = mock(CatalogService.class);
        when(catalog.getSession(SESSION)).thenReturn(new SessionView(SESSION, "Solo", "Thane West",
                List.of(LODHA), Instant.now()));
        when(catalog.entities(anyCollection())).thenReturn(List.of(
                new EntityView(LODHA, "Lodha", "Amara", "Thane West", "RERA-L", false)));
        when(catalog.baseObservations(anyCollection())).thenReturn(List.of(
                price(LODHA, "30000"), amenities(LODHA, "Gym, Pool"),
                floorRise(LODHA, "200"), bhk(LODHA, "2BHK, 3BHK")));
        return catalog;
    }

    private static ObservationView price(UUID entity, String value) {
        return obs(entity, ParameterType.PRICE, value, "INR_PER_CARPET_SQFT", new BigDecimal(value),
                SourceSystem.USER, CredibilityTier.USER_PROVIDED);
    }

    private static ObservationView floorRise(UUID entity, String value) {
        return obs(entity, ParameterType.FLOOR_RISE_PREMIUM, value, "INR_PER_SQFT_PER_FLOOR",
                new BigDecimal(value), SourceSystem.DEVELOPER_SITE, CredibilityTier.INDICATIVE);
    }

    private static ObservationView amenities(UUID entity, String csv) {
        return obs(entity, ParameterType.AMENITIES, csv, null, null,
                SourceSystem.DEVELOPER_SITE, CredibilityTier.INDICATIVE);
    }

    private static ObservationView bhk(UUID entity, String csv) {
        return obs(entity, ParameterType.BHK_CONFIG, csv, null, null,
                SourceSystem.DEVELOPER_SITE, CredibilityTier.INDICATIVE);
    }

    private static ObservationView obs(UUID entity, ParameterType parameter, String rawValue, String unit,
                                       BigDecimal normalized, SourceSystem source, CredibilityTier tier) {
        return new ObservationView(UUID.randomUUID(), entity, parameter, rawValue, unit, AreaBasis.CARPET,
                normalized, unit, source, tier, new BigDecimal("0.9"), source.name().toLowerCase() + ":ref",
                null, Instant.now());
    }
}
