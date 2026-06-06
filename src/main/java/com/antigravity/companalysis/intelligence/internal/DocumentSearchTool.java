package com.antigravity.companalysis.intelligence.internal;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.error.AgentError;
import com.gamma.agentkernel.retrieve.ContextBudget;
import com.gamma.agentkernel.tool.Evidence;
import com.gamma.agentkernel.tool.Tool;
import com.gamma.agentkernel.tool.ToolResult;
import com.gamma.agentkernel.tool.ToolSpec;

/**
 * The kernel {@link Tool} that surfaces qualitative document grounding via the agent context's
 * {@link AgentContext#retriever() Retriever} (the pgvector store when configured; ADR-0013). It returns the
 * matching passages as text snippets with their citations — <b>never figures</b> (ADR-0001; "RAG never
 * supplies numbers"). Abstains ({@code noData}) when the query is blank or no grounding is found (including
 * when RAG is disabled and the retriever is {@code NONE}).
 */
@Component
public class DocumentSearchTool implements Tool {

    public static final String ID = "search-documents";

    /** Total context budget; ~70% goes to retrieval (≈ a handful of passages). */
    private static final int BUDGET_TOKENS = 1200;

    private static final ToolSpec SPEC = new ToolSpec(ID, 1,
            "Retrieve qualitative grounding passages for a question from the document corpus, with "
                    + "citations. Never returns figures.",
            Duration.ofSeconds(10));

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public ToolResult invoke(Map<String, Object> args, AgentContext ctx) throws AgentError {
        Object raw = (args == null) ? null : args.get("query");
        String query = (raw == null) ? null : raw.toString();
        if (query == null || query.isBlank()) {
            return ToolResult.noData();
        }

        List<Evidence> evidence = ctx.retriever().retrieve(query, ContextBudget.standard(BUDGET_TOKENS));
        if (evidence.isEmpty()) {
            return ToolResult.noData(); // no grounding (or RAG disabled) — abstain rather than invent
        }
        List<String> snippets = evidence.stream().map(e -> String.valueOf(e.value())).toList();
        return ToolResult.of(snippets, evidence);
    }
}
