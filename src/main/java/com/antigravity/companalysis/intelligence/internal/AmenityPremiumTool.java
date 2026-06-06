package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.analytics.AmenityPremium;
import com.antigravity.companalysis.analytics.AmenityPremiumAnalyzer;
import com.antigravity.companalysis.catalog.CatalogService;
import com.antigravity.companalysis.reconciliation.ConsolidatedParameter;
import com.antigravity.companalysis.reconciliation.ReconciliationService;
import com.antigravity.companalysis.shared.ParameterType;
import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.error.AgentError;
import com.gamma.agentkernel.tool.Evidence;
import com.gamma.agentkernel.tool.Tool;
import com.gamma.agentkernel.tool.ToolResult;
import com.gamma.agentkernel.tool.ToolSpec;

/**
 * The kernel {@link Tool} that surfaces the {@code analytics} amenity-premium engine: it reconciles the
 * session, then runs the (pure) {@link AmenityPremiumAnalyzer} to compare each project's price against its
 * amenity set, relative to the cheapest comparable project. Emits one {@link Evidence} per comparable
 * project's price and amenity anchors (locators only; ADR-0008).
 *
 * <p>Abstains ({@code noData}) when there is nothing observed, or when fewer than two projects carry both a
 * price and an amenity list (no comparison is possible).
 */
@Component
public class AmenityPremiumTool implements Tool {

    public static final String ID = "amenity-premium";

    private static final ToolSpec SPEC = new ToolSpec(ID, 1,
            "Compare each project's price against its amenity set, relative to the cheapest comparable "
                    + "project, and name the amenities the premium buys.",
            Duration.ofSeconds(10));

    private final ReconciliationService reconciliation;
    private final CatalogService catalog;

    public AmenityPremiumTool(ReconciliationService reconciliation, CatalogService catalog) {
        this.reconciliation = reconciliation;
        this.catalog = catalog;
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public ToolResult invoke(Map<String, Object> args, AgentContext ctx) throws AgentError {
        UUID sessionId = ToolArgs.sessionId(args);
        if (sessionId == null) {
            return ToolResult.noData();
        }

        List<ConsolidatedParameter> consolidated = reconciliation.consolidate(sessionId);
        if (consolidated.isEmpty()) {
            return ToolResult.noData();
        }

        List<AmenityPremium> premiums = AmenityPremiumAnalyzer.analyze(consolidated,
                AnalyticsTools.labels(catalog, sessionId));
        if (premiums.isEmpty()) {
            return ToolResult.noData(); // fewer than two priced, amenity-bearing projects
        }

        List<Evidence> evidence = new ArrayList<>();
        for (ConsolidatedParameter c : consolidated) {
            if (c.parameter() == ParameterType.PRICE || c.parameter() == ParameterType.AMENITIES) {
                evidence.add(AnalyticsTools.anchorEvidence(c));
            }
        }
        return ToolResult.of(premiums, evidence);
    }
}
