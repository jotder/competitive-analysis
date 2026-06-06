package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.agent.Capability;
import com.gamma.agentkernel.agent.CapabilitySpec;
import com.gamma.agentkernel.error.AgentError;
import com.gamma.agentkernel.model.ModelTier;
import com.gamma.agentkernel.tool.Evidence;
import com.gamma.agentkernel.tool.Tool;
import com.gamma.agentkernel.tool.ToolResult;

/**
 * The {@code search-documents} capability: answer a question with <em>qualitative</em> grounding pulled from
 * the document corpus (brochures, RERA filings, listings) via the {@link DocumentSearchTool} (R1.4;
 * ADR-0013). It surfaces the relevant passages with their citations and never extracts or asserts a figure —
 * numbers come only from the reconciliation/analytics engines (ADR-0001; "RAG never supplies numbers").
 * Abstains (UNAVAILABLE) when there is no question or no grounding is found (including when RAG is disabled).
 */
@Component
public class DocumentSearchCapability implements Capability {

    public static final String ID = "search-documents";

    private static final CapabilitySpec SPEC = new CapabilitySpec(ID, 1,
            "Surface qualitative document context for a question, with citations (grounding only — never figures).",
            ModelTier.SMALL, 0.5, Duration.ofSeconds(15),
            Set.of(), Set.of(DocumentSearchTool.ID));

    @Override
    public CapabilitySpec spec() {
        return SPEC;
    }

    @Override
    public AgentResult run(AgentRequest request, AgentContext ctx) throws AgentError {
        String query = request.userText();
        if (query == null || query.isBlank()) {
            return AgentResult.unavailable(ID, "no question to search documents for");
        }
        Tool tool = ctx.tools().get(DocumentSearchTool.ID).orElse(null);
        if (tool == null) {
            return AgentResult.unavailable(ID, "document-search tool not available");
        }

        ToolResult result = tool.invoke(Map.of("query", query), ctx);
        if (!result.hasData()) {
            return AgentResult.unavailable(ID, "no document context found for the question");
        }

        return AgentResult.draft(ID, SPEC.version(), narrate(result.evidence()), result.evidence(), List.of(),
                "qualitative document grounding (R1.4 — RAG never supplies numbers)", 0.0,
                ctx.effectiveTier(SPEC.defaultTier()), Map.of("passages", result.evidence().size()));
    }

    /** List each grounding passage with its citation; no numbers are derived from the text. */
    private static String narrate(List<Evidence> evidence) {
        StringBuilder sb = new StringBuilder("Found ").append(evidence.size())
                .append(" relevant document passage(s):");
        for (Evidence e : evidence) {
            sb.append("\n- \"").append(String.valueOf(e.value())).append('"');
            if (e.sourceRef() != null && !e.sourceRef().isBlank()) {
                sb.append(" [").append(e.sourceRef()).append(']');
            }
        }
        sb.append("\n(Context only — figures come from the reconciliation/analytics engines, not these passages.)");
        return sb.toString();
    }
}
