package com.antigravity.companalysis.intelligence;

import com.antigravity.companalysis.intelligence.internal.CompareCapability;
import com.antigravity.companalysis.intelligence.internal.ComparisonGridTool;
import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.observe.AuditSink;
import com.gamma.agentkernel.orchestrate.SyncOrchestrator;
import com.gamma.agentkernel.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code intelligence} module's public entry point: the CxO decision-support agent. It owns the
 * read-only {@link AgentContext} (the tools a capability may call + the audit sink) and dispatches a
 * request through the kernel's {@link SyncOrchestrator}, which {@code agent-kernel-spring} auto-wires
 * from this app's {@code Capability} beans (R1; ADR-0010). The orchestrator handles
 * resolve → confidence-gate → abstain-below-threshold → audit; this class only maps the call.
 *
 * <p>R1.1 exposes one capability ({@code compare}) over one tool ({@link ComparisonGridTool}); no model
 * is wired yet (deterministic narration). The {@code POST /api/chat} SSE surface and a real model arrive
 * in R1.3.
 */
@Service
public class CxoAgent {

    private final SyncOrchestrator orchestrator;
    private final AuditSink audit;
    private final ToolRegistry tools;

    public CxoAgent(SyncOrchestrator orchestrator, AuditSink audit, ComparisonGridTool comparisonGridTool) {
        this.orchestrator = orchestrator;
        this.audit = audit;
        this.tools = ToolRegistry.of(List.of(comparisonGridTool));
    }

    /**
     * Answer a comparison question over a session's grid. Returns the neutral {@link AgentResult} — the
     * caller (a controller, in R1.3) maps it to a wire/SSE shape.
     */
    public AgentResult compare(UUID sessionId, String question) {
        AgentRequest request = new AgentRequest(CompareCapability.ID,
                Map.of("sessionId", sessionId.toString()), Map.of(), question);
        AgentContext ctx = AgentContext.builder().tools(tools).audit(audit).build();
        return orchestrator.run(request, ctx);
    }
}
