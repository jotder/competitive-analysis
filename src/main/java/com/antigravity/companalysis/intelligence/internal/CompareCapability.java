package com.antigravity.companalysis.intelligence.internal;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilitySpec;
import com.gamma.agentkernel.error.AgentError;
import com.gamma.agentkernel.model.ModelTier;
import com.gamma.agentkernel.tool.Tool;
import com.gamma.agentkernel.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code compare} capability: answer a comparison question over a session's grid. R1.1 is the
 * kernel-integration slice, so narration is <b>deterministic and model-free</b> — it invokes the
 * {@link ComparisonGridTool} and templates a summary in which <em>every figure carries its credibility
 * tier label</em> (CxO ADR-0001). A real Gemini narrator replaces the template in R1.3, behind the same
 * capability contract. With no data the capability returns UNAVAILABLE rather than invent figures.
 *
 * <p>Dispatched by the kernel's {@code SyncOrchestrator} (auto-wired by {@code agent-kernel-spring}):
 * the orchestrator estimates confidence and abstains below threshold, so a model-unavailable or
 * data-less request degrades safely.
 */
@Component
public class CompareCapability implements Capability {

    public static final String ID = "compare";

    private static final CapabilitySpec SPEC = new CapabilitySpec(ID, 1,
            "Summarize a comparison session's grid, stating each figure's credibility tier.",
            ModelTier.SMALL, 0.5, Duration.ofSeconds(15),
            Set.of("sessionId"), Set.of(ComparisonGridTool.ID));

    @Override
    public CapabilitySpec spec() {
        return SPEC;
    }

    @Override
    public AgentResult run(AgentRequest request, AgentContext ctx) throws AgentError {
        String sessionId = request.context("sessionId");
        if (sessionId == null) {
            return AgentResult.unavailable(ID, "no sessionId in request context");
        }
        Tool grid = ctx.tools().get(ComparisonGridTool.ID).orElse(null);
        if (grid == null) {
            return AgentResult.unavailable(ID, "comparison-grid tool not available");
        }

        ToolResult result = grid.invoke(Map.of("sessionId", sessionId), ctx);
        if (!result.hasData() || !(result.value() instanceof ComparisonGrid g)) {
            return AgentResult.unavailable(ID, "no comparison data for session " + sessionId);
        }

        return AgentResult.draft(ID, SPEC.version(), narrate(g), result.evidence(), List.of(),
                "deterministic grid summary (R1.1 — no model yet)", 0.0,
                ctx.effectiveTier(SPEC.defaultTier()), Map.of("grid", g));
    }

    /** Deterministic, tier-labelled summary — no model, no arithmetic on the figures. */
    private static String narrate(ComparisonGrid grid) {
        Map<String, java.util.List<ComparisonGrid.Cell>> byEntity = new LinkedHashMap<>();
        for (ComparisonGrid.Cell c : grid.cells()) {
            byEntity.computeIfAbsent(c.entityLabel(), k -> new java.util.ArrayList<>()).add(c);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Comparison \"").append(grid.title()).append('"');
        if (grid.microMarket() != null && !grid.microMarket().isBlank()) {
            sb.append(" in ").append(grid.microMarket());
        }
        sb.append(" across ").append(byEntity.size()).append(" developer(s):");
        byEntity.forEach((label, cells) -> {
            sb.append("\n- ").append(label).append(':');
            for (ComparisonGrid.Cell c : cells) {
                sb.append("\n    • ").append(c.parameter()).append(": ").append(c.value());
                if (c.unit() != null && !c.unit().isBlank()) {
                    sb.append(' ').append(c.unit());
                }
                sb.append(" (per ").append(c.tierLabel()).append(')');
            }
        });
        return sb.toString();
    }
}
