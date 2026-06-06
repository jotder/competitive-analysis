package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.antigravity.companalysis.reconciliation.ConsolidatedParameter;
import com.antigravity.companalysis.reconciliation.ConsolidatedParameter.Contribution;
import com.antigravity.companalysis.reconciliation.ReconciliationService;
import com.antigravity.companalysis.shared.ParameterType;
import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.error.AgentError;
import com.gamma.agentkernel.tool.Evidence;
import com.gamma.agentkernel.tool.Tool;
import com.gamma.agentkernel.tool.ToolResult;
import com.gamma.agentkernel.tool.ToolSpec;

/**
 * The kernel {@link Tool} that exposes the {@code reconciliation} module's credibility consolidation
 * (ADR-0002) to the agent. For a session — optionally narrowed to one {@code parameter} — it returns the
 * {@link ConsolidatedParameter}s and emits one provenance-tagged {@link Evidence} per contributing
 * observation (source locator only; ADR-0008), so the narration can state how each figure was anchored and
 * which lower-credibility sources were set aside. Deterministic: the LLM never consolidates (ADR-0001).
 */
@Component
public class ReconciliationTool implements Tool {

    public static final String ID = "get-parameter-detail";

    private static final ToolSpec SPEC = new ToolSpec(ID, 1,
            "Consolidate a session's observations into the canonical value per (entity, parameter) by "
                    + "credibility (ANCHOR/WEIGHTED/RANGE, never flat-average), with full provenance.",
            Duration.ofSeconds(10));

    private final ReconciliationService reconciliation;

    public ReconciliationTool(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
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
        ParameterType parameter = ToolArgs.parameter(args);

        List<ConsolidatedParameter> consolidated = (parameter == null)
                ? reconciliation.consolidate(sessionId)
                : reconciliation.consolidate(sessionId, parameter);
        if (consolidated.isEmpty()) {
            return ToolResult.noData();
        }

        List<Evidence> evidence = new ArrayList<>();
        for (ConsolidatedParameter c : consolidated) {
            for (Contribution k : c.contributions()) {
                evidence.add(new Evidence(k.displayValue(), CredibilityTiers.toKernel(k.tier()),
                        k.tier().name(), k.sourceRef(), k.confidence(), null));
            }
        }
        return ToolResult.of(consolidated, evidence);
    }
}
