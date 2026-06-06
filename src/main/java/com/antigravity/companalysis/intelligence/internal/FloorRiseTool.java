package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.analytics.FloorRisePremium;
import com.antigravity.companalysis.analytics.FloorRisePremiumAnalyzer;
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
 * The kernel {@link Tool} that surfaces the {@code analytics} floor-rise engine: it reconciles the session,
 * then runs the (pure) {@link FloorRisePremiumAnalyzer} to rank each project's per-floor surcharge and
 * measure its gap over the cheapest. Emits one {@link Evidence} per project's floor-rise anchor (locator
 * only; ADR-0008).
 *
 * <p>Abstains ({@code noData}) when nothing is observed, or when fewer than two projects carry a numeric
 * floor-rise premium (no ranking is possible).
 */
@Component
public class FloorRiseTool implements Tool {

    public static final String ID = "floor-rise-premium";

    private static final ToolSpec SPEC = new ToolSpec(ID, 1,
            "Rank each project's per-floor surcharge (floor-rise premium) and measure every project's gap "
                    + "over the cheapest.",
            Duration.ofSeconds(10));

    private final ReconciliationService reconciliation;
    private final CatalogService catalog;

    public FloorRiseTool(ReconciliationService reconciliation, CatalogService catalog) {
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

        List<FloorRisePremium> premiums = FloorRisePremiumAnalyzer.analyze(consolidated,
                AnalyticsTools.labels(catalog, sessionId));
        if (premiums.isEmpty()) {
            return ToolResult.noData(); // fewer than two projects with a floor-rise premium
        }

        List<Evidence> evidence = new ArrayList<>();
        for (ConsolidatedParameter c : consolidated) {
            if (c.parameter() == ParameterType.FLOOR_RISE_PREMIUM) {
                evidence.add(AnalyticsTools.anchorEvidence(c));
            }
        }
        return ToolResult.of(premiums, evidence);
    }
}
