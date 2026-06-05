package com.antigravity.companalysis.intelligence;

import com.antigravity.companalysis.catalog.CatalogService;
import com.antigravity.companalysis.catalog.CatalogService.EntityView;
import com.antigravity.companalysis.catalog.CatalogService.ObservationView;
import com.antigravity.companalysis.catalog.CatalogService.SessionView;
import com.antigravity.companalysis.intelligence.internal.CompareCapability;
import com.antigravity.companalysis.intelligence.internal.ComparisonGridTool;
import com.antigravity.companalysis.shared.AreaBasis;
import com.antigravity.companalysis.shared.CredibilityTier;
import com.antigravity.companalysis.shared.ParameterType;
import com.antigravity.companalysis.shared.SourceSystem;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.observe.AuditSink;
import com.gamma.agentkernel.observe.RingBufferAuditSink;
import com.gamma.agentkernel.orchestrate.SyncOrchestrator;
import com.gamma.agentkernel.spring.AgentKernelAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves R1.1's integration: the {@code agent-kernel-spring} auto-configuration assembles a
 * {@link SyncOrchestrator} from CxO's {@link CompareCapability} bean, and {@link CxoAgent} dispatches a
 * comparison end-to-end — reading the (mocked) catalog through {@link ComparisonGridTool}, producing a
 * deterministic tier-labelled answer and provenance-tagged evidence. No database/Docker: the catalog is a
 * Mockito stub and the context is an app-free {@link ApplicationContextRunner}.
 *
 * <p>The seeded PRICE conflict (an INDICATIVE listing vs a USER_PROVIDED value) pins the headline R1
 * finding: the tool selects by <b>CxO's {@code CredibilityTier.rank()}</b> (USER_PROVIDED &gt; INDICATIVE),
 * which is the <em>reverse</em> of the kernel enum's ordinal ordering — so the app is correct today and
 * the kernel's ordering is the documented reshape candidate (R1.5).
 */
class CxoAgentIntegrationTest {

    private static final UUID SESSION = UUID.randomUUID();
    private static final UUID ENTITY = UUID.randomUUID();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentKernelAutoConfiguration.class))
            .withBean(CatalogService.class, CxoAgentIntegrationTest::stubbedCatalog)
            .withBean(ComparisonGridTool.class)
            .withBean(CompareCapability.class)
            .withBean(CxoAgent.class);

    @Test
    void dispatchesCompareThroughTheAutoWiredOrchestrator() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(SyncOrchestrator.class).hasSingleBean(CxoAgent.class);

            AgentResult result = ctx.getBean(CxoAgent.class).compare(SESSION, "compare price and area");

            assertThat(result.status()).isEqualTo(AgentResult.Status.OK);
            assertThat(result.confidence()).isEqualTo(1.0); // default estimator: validated OK -> 1.0

            assertThat(result.answer())
                    .contains("Lodha Amara")
                    .contains("PRICE: 35600")              // USER_PROVIDED value chosen by CxO rank()
                    .contains("(per USER_PROVIDED)")        // ...over the INDICATIVE listing
                    .contains("CARPET_AREA: 900")
                    .contains("(per AUTHORITATIVE)")
                    .doesNotContain("38900");               // the indicative price lost the rank() contest

            // Evidence is provenance-tagged with the kernel tier (mapped from CxO's vocabulary).
            assertThat(result.evidence()).anySatisfy(e ->
                    assertThat(e.tier()).isEqualTo(com.gamma.agentkernel.tool.CredibilityTier.USER_PROVIDED));
            assertThat(result.evidence()).anySatisfy(e ->
                    assertThat(e.tier()).isEqualTo(com.gamma.agentkernel.tool.CredibilityTier.AUTHORITATIVE));

            // The orchestrator audited the completion to the auto-configured sink (keys only; ADR-0008).
            assertThat((RingBufferAuditSink) ctx.getBean(AuditSink.class)).extracting(RingBufferAuditSink::size)
                    .isEqualTo(1);
        });
    }

    @Test
    void abstainsWhenSessionHasNoObservations() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AgentKernelAutoConfiguration.class))
                .withBean(CatalogService.class, CxoAgentIntegrationTest::emptyCatalog)
                .withBean(ComparisonGridTool.class)
                .withBean(CompareCapability.class)
                .withBean(CxoAgent.class)
                .run(ctx -> {
                    AgentResult result = ctx.getBean(CxoAgent.class).compare(SESSION, "compare");
                    assertThat(result.status()).isEqualTo(AgentResult.Status.UNAVAILABLE);
                });
    }

    // ── stubbed catalog (stands in for the JPA-backed service) ────────────────────────

    private static CatalogService stubbedCatalog() {
        CatalogService catalog = mock(CatalogService.class);
        when(catalog.getSession(SESSION)).thenReturn(
                new SessionView(SESSION, "Thane West showdown", "Thane West", List.of(ENTITY), Instant.now()));
        when(catalog.entities(anyCollection())).thenReturn(List.of(
                new EntityView(ENTITY, "Lodha", "Amara", "Thane West", "RERA123", false)));
        when(catalog.baseObservations(anyCollection())).thenReturn(List.of(
                obs(ParameterType.PRICE, SourceSystem.MAGICBRICKS, CredibilityTier.INDICATIVE,
                        "38900", "INR_PER_CARPET_SQFT", "0.6", "magicbricks:listing/1"),
                obs(ParameterType.PRICE, SourceSystem.USER, CredibilityTier.USER_PROVIDED,
                        "35600", "INR_PER_CARPET_SQFT", "0.9", "user:form/price"),
                obs(ParameterType.CARPET_AREA, SourceSystem.RERA, CredibilityTier.AUTHORITATIVE,
                        "900", "SQFT", "1.0", "rera:reg/RERA123")));
        return catalog;
    }

    private static CatalogService emptyCatalog() {
        CatalogService catalog = mock(CatalogService.class);
        when(catalog.getSession(SESSION)).thenReturn(
                new SessionView(SESSION, "Empty", "Thane West", List.of(ENTITY), Instant.now()));
        when(catalog.entities(anyCollection())).thenReturn(List.of(
                new EntityView(ENTITY, "Lodha", "Amara", "Thane West", "RERA123", false)));
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
