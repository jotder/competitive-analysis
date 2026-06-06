package com.antigravity.companalysis.intelligence.internal;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilitySpec;
import com.gamma.agentkernel.error.AgentError;
import com.gamma.agentkernel.model.ModelProvider;
import com.gamma.agentkernel.model.ModelRequest;
import com.gamma.agentkernel.model.ModelResponse;
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
 * The {@code compare} capability: answer a comparison question over a session's grid. The grid is always
 * computed deterministically by the {@link ComparisonGridTool} (the LLM never calculates — ADR-0001); the
 * <em>narration</em> is produced by Gemini when a model is available (R1.3), grounded strictly on that
 * grid, and falls back to a deterministic tier-labelled template when no model is configured. Either way
 * <em>every figure carries its credibility tier</em>. With no data the capability returns UNAVAILABLE
 * rather than invent figures.
 *
 * <p>Dispatched by the kernel's {@code SyncOrchestrator}/{@code StreamingOrchestrator} (auto-wired by
 * {@code agent-kernel-spring}): the orchestrator estimates confidence and abstains below threshold, so a
 * model-unavailable or data-less request degrades safely. The model is reached through the read-only
 * {@link AgentContext#models()} router, so this capability stays provider-agnostic.
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

        ModelTier tier = ctx.effectiveTier(SPEC.defaultTier());
        String facts = narrate(g);
        ModelProvider model = ctx.models().providerFor(tier);
        if (model.available()) {
            try {
                ModelResponse response = model.generate(
                        ModelRequest.text(tier, SYSTEM_PROMPT, userPrompt(request.userText(), facts)));
                if (response.text() != null && !response.text().isBlank()) {
                    return AgentResult.draft(ID, SPEC.version(), response.text(), result.evidence(), List.of(),
                            "Gemini narration grounded on the deterministic grid", 0.0, tier, Map.of("grid", g));
                }
            } catch (RuntimeException e) {
                // model failed at call time — degrade to the deterministic template rather than error out
            }
        }
        return AgentResult.draft(ID, SPEC.version(), facts, result.evidence(), List.of(),
                "deterministic grid summary (no model configured)", 0.0, tier, Map.of("grid", g));
    }

    private static final String SYSTEM_PROMPT =
            "You are a real-estate competitive-analysis assistant. Answer ONLY from the grid facts you are "
                    + "given. Never invent, estimate, or alter any number, unit, or credibility tier. State "
                    + "each figure's credibility tier exactly as provided. Be concise.";

    private static String userPrompt(String question, String facts) {
        String q = (question == null || question.isBlank()) ? "Summarize the comparison." : question.trim();
        return "Question: " + q + "\n\nGrid facts (authoritative — do not change any number or tier):\n" + facts;
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
