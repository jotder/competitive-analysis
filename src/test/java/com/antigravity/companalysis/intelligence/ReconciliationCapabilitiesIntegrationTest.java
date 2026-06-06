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
import com.antigravity.companalysis.intelligence.internal.AmenityPremiumTool;
import com.antigravity.companalysis.intelligence.internal.AuditDiscrepanciesCapability;
import com.antigravity.companalysis.intelligence.internal.BhkSupplyGapTool;
import com.antigravity.companalysis.intelligence.internal.ComparisonGridTool;
import com.antigravity.companalysis.intelligence.internal.DiscrepancyTool;
import com.antigravity.companalysis.intelligence.internal.DocumentSearchTool;
import com.antigravity.companalysis.intelligence.internal.ExplainParameterCapability;
import com.antigravity.companalysis.intelligence.internal.FloorRiseTool;
import com.antigravity.companalysis.intelligence.internal.ReconciliationTool;
import com.antigravity.companalysis.reconciliation.ReconciliationService;
import com.antigravity.companalysis.shared.AreaBasis;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.observe.AuditSink;
import com.gamma.agentkernel.observe.RingBufferAuditSink;
import com.gamma.agentkernel.spring.AgentKernelAutoConfiguration;

/**
 * Proves R1.2's integration: the {@code agent-kernel-spring} auto-configuration assembles the orchestrator
 * from CxO's reconciliation/analytics capabilities, and {@link CxoAgent} dispatches them end-to-end over a
 * (mocked) catalog — with the real {@link ReconciliationService} and discrepancy engine in the path. No
 * database/Docker. The seeded data pins the credibility behaviour: a USER_PROVIDED price anchors over a
 * higher-confidence INDICATIVE listing (R1.5 evidence), and a portal's inflated area is flagged against the
 * RERA anchor.
 */
class ReconciliationCapabilitiesIntegrationTest {

    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID ENTITY = UUID.randomUUID();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentKernelAutoConfiguration.class))
            .withBean(CatalogService.class, ReconciliationCapabilitiesIntegrationTest::stubbedCatalog)
            .withBean(ReconciliationService.class)
            .withBean(ComparisonGridTool.class)
            .withBean(ReconciliationTool.class)
            .withBean(DiscrepancyTool.class)
            .withBean(AmenityPremiumTool.class)
            .withBean(FloorRiseTool.class)
            .withBean(BhkSupplyGapTool.class)
            .withBean(DocumentSearchTool.class)
            .withBean(ExplainParameterCapability.class)
            .withBean(AuditDiscrepanciesCapability.class)
            .withBean(CxoAgent.class);

    @Test
    void explainsReconciliationAnchoringOnCxoRank() {
        runner.run(ctx -> {
            AgentResult result = ctx.getBean(CxoAgent.class)
                    .explainParameter(SESSION, ParameterType.PRICE, "how did you get the price?");

            assertThat(result.status()).isEqualTo(AgentResult.Status.OK);
            assertThat(result.confidence()).isEqualTo(1.0);
            assertThat(result.answer())
                    .contains("PRICE: 35600")                     // user value anchored
                    .contains("ANCHOR, per USER_PROVIDED")
                    .contains("USER 35600")
                    .contains("(USER_PROVIDED, used)")
                    .contains("MAGICBRICKS 38900")                // listing kept as provenance...
                    .contains("(INDICATIVE)")                     // ...but set aside (no ", used")
                    .doesNotContain("(INDICATIVE, used)");

            assertThat(result.evidence()).anySatisfy(e ->
                    assertThat(e.tier()).isEqualTo(com.gamma.agentkernel.tool.CredibilityTier.USER_PROVIDED));
            assertThat((RingBufferAuditSink) ctx.getBean(AuditSink.class))
                    .extracting(RingBufferAuditSink::size).isEqualTo(1);
        });
    }

    @Test
    void auditsDiscrepanciesAgainstTheAuthoritativeAnchor() {
        runner.run(ctx -> {
            AgentResult result = ctx.getBean(CxoAgent.class)
                    .auditDiscrepancies(SESSION, "anything fishy?");

            assertThat(result.status()).isEqualTo(AgentResult.Status.OK);
            assertThat(result.answer())
                    .contains("Found 1 material discrepancy")     // only CARPET_AREA; PRICE gap is 9.27% < 10%
                    .contains("CARPET_AREA")
                    .contains("MAGICBRICKS")
                    .contains("AUTHORITATIVE anchor 900")
                    .contains("+33.33%");
            assertThat(result.evidence()).isNotEmpty();
        });
    }

    @Test
    void abstainsWhenNothingToReconcile() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AgentKernelAutoConfiguration.class))
                .withBean(CatalogService.class, ReconciliationCapabilitiesIntegrationTest::emptyCatalog)
                .withBean(ReconciliationService.class)
                .withBean(ComparisonGridTool.class)
                .withBean(ReconciliationTool.class)
                .withBean(DiscrepancyTool.class)
                .withBean(AmenityPremiumTool.class)
                .withBean(FloorRiseTool.class)
                .withBean(BhkSupplyGapTool.class)
                .withBean(DocumentSearchTool.class)
                .withBean(ExplainParameterCapability.class)
                .withBean(AuditDiscrepanciesCapability.class)
                .withBean(CxoAgent.class)
                .run(ctx -> {
                    assertThat(ctx.getBean(CxoAgent.class).explainParameter(SESSION, null, "?").status())
                            .isEqualTo(AgentResult.Status.UNAVAILABLE);
                    assertThat(ctx.getBean(CxoAgent.class).auditDiscrepancies(SESSION, "?").status())
                            .isEqualTo(AgentResult.Status.UNAVAILABLE);
                });
    }

    // ── stubbed catalog ──────────────────────────────────────────────────────────

    private static CatalogService stubbedCatalog() {
        CatalogService catalog = mock(CatalogService.class);
        when(catalog.getSession(SESSION)).thenReturn(
                new SessionView(SESSION, "Thane West showdown", "Thane West", List.of(ENTITY), Instant.now()));
        when(catalog.entities(anyCollection())).thenReturn(List.of(
                new EntityView(ENTITY, "Lodha", "Amara", "Thane West", "RERA123", false)));
        when(catalog.baseObservations(anyCollection())).thenReturn(List.of(
                obs(ParameterType.PRICE, SourceSystem.MAGICBRICKS, CredibilityTier.INDICATIVE,
                        "38900", "INR_PER_CARPET_SQFT", "0.95", "magicbricks:listing/1"),
                obs(ParameterType.PRICE, SourceSystem.USER, CredibilityTier.USER_PROVIDED,
                        "35600", "INR_PER_CARPET_SQFT", "0.60", "user:form/price"),
                obs(ParameterType.CARPET_AREA, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE,
                        "900", "SQFT", "1.0", "rera:reg/RERA123"),
                obs(ParameterType.CARPET_AREA, SourceSystem.MAGICBRICKS, CredibilityTier.INDICATIVE,
                        "1200", "SQFT", "0.80", "magicbricks:listing/area")));
        return catalog;
    }

    private static CatalogService emptyCatalog() {
        CatalogService catalog = mock(CatalogService.class);
        when(catalog.getSession(SESSION)).thenReturn(
                new SessionView(SESSION, "Empty", "Thane West", List.of(ENTITY), Instant.now()));
        when(catalog.baseObservations(anyCollection())).thenReturn(List.of());
        return catalog;
    }

    private static ObservationView obs(ParameterType parameter, SourceSystem source, CredibilityTier tier,
                                       String normalizedValue, String normalizedUnit, String confidence,
                                       String sourceRef) {
        return new ObservationView(UUID.randomUUID(), ENTITY, parameter, normalizedValue, normalizedUnit,
                AreaBasis.CARPET, new BigDecimal(normalizedValue), normalizedUnit, source, tier,
                new BigDecimal(confidence), sourceRef, null, Instant.now());
    }
}
