package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.analytics.BhkSupplyGap;
import com.antigravity.companalysis.analytics.BhkSupplyGapAnalyzer;
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
 * The kernel {@link Tool} that surfaces the {@code analytics} BHK-supply-gap engine: it reconciles the
 * session, then runs the (pure) {@link BhkSupplyGapAnalyzer} to count, for every BHK configuration in the
 * market, how many projects offer it and which do not. Emits one {@link Evidence} per project's BHK anchor
 * (locator only; ADR-0008).
 *
 * <p>Abstains ({@code noData}) when nothing is observed, or when fewer than two projects carry a BHK
 * configuration (no market gap can be measured).
 */
@Component
public class BhkSupplyGapTool implements Tool {

    public static final String ID = "bhk-supply-gap";

    private static final ToolSpec SPEC = new ToolSpec(ID, 1,
            "For every BHK configuration in the market, count how many projects offer it and name those "
                    + "that do not — surfacing the supply gaps.",
            Duration.ofSeconds(10));

    private final ReconciliationService reconciliation;
    private final CatalogService catalog;

    public BhkSupplyGapTool(ReconciliationService reconciliation, CatalogService catalog) {
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

        List<BhkSupplyGap> gaps = BhkSupplyGapAnalyzer.analyze(consolidated,
                AnalyticsTools.labels(catalog, sessionId));
        if (gaps.isEmpty()) {
            return ToolResult.noData(); // fewer than two projects with a BHK configuration
        }

        List<Evidence> evidence = new ArrayList<>();
        for (ConsolidatedParameter c : consolidated) {
            if (c.parameter() == ParameterType.BHK_CONFIG) {
                evidence.add(AnalyticsTools.anchorEvidence(c));
            }
        }
        return ToolResult.of(gaps, evidence);
    }
}
