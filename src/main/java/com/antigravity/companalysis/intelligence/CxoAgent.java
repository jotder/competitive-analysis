package com.antigravity.companalysis.intelligence;

import com.antigravity.companalysis.intelligence.internal.AmenityPremiumCapability;
import com.antigravity.companalysis.intelligence.internal.AmenityPremiumTool;
import com.antigravity.companalysis.intelligence.internal.AuditDiscrepanciesCapability;
import com.antigravity.companalysis.intelligence.internal.BhkSupplyGapCapability;
import com.antigravity.companalysis.intelligence.internal.BhkSupplyGapTool;
import com.antigravity.companalysis.intelligence.internal.CompareCapability;
import com.antigravity.companalysis.intelligence.internal.ComparisonGridTool;
import com.antigravity.companalysis.intelligence.internal.DiscrepancyTool;
import com.antigravity.companalysis.intelligence.internal.DocumentSearchCapability;
import com.antigravity.companalysis.intelligence.internal.DocumentSearchTool;
import com.antigravity.companalysis.intelligence.internal.ExplainParameterCapability;
import com.antigravity.companalysis.intelligence.internal.FloorRiseCapability;
import com.antigravity.companalysis.intelligence.internal.FloorRiseTool;
import com.antigravity.companalysis.intelligence.internal.ReconciliationTool;
import com.antigravity.companalysis.shared.ParameterType;
import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.model.ModelRouter;
import com.gamma.agentkernel.observe.AuditSink;
import com.gamma.agentkernel.orchestrate.AgentStreamListener;
import com.gamma.agentkernel.orchestrate.StreamingOrchestrator;
import com.gamma.agentkernel.orchestrate.SyncOrchestrator;
import com.gamma.agentkernel.retrieve.Retriever;
import com.gamma.agentkernel.tool.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
 * <p>Capabilities are auto-collected into the kernel's {@code CapabilityRegistry} from this app's
 * {@code Capability} beans (by {@code agent-kernel-spring}); this class owns only the tool set they may
 * call and maps each public method to an {@link AgentRequest}. R1.1 added {@code compare}; R1.2 adds
 * {@code explain-parameter} (reconciliation) and {@code audit-discrepancies} (analytics). No model is
 * wired yet (deterministic narration); the {@code POST /api/chat} SSE surface and a real model arrive in
 * R1.3.
 */
@Service
public class CxoAgent {

    private final SyncOrchestrator orchestrator;
    private final StreamingOrchestrator streamingOrchestrator;
    private final AuditSink audit;
    private final ModelRouter models;
    private final Retriever retriever;
    private final ToolRegistry tools;

    public CxoAgent(SyncOrchestrator orchestrator, StreamingOrchestrator streamingOrchestrator,
                    AuditSink audit, ModelRouter models, Retriever retriever,
                    ComparisonGridTool comparisonGridTool, ReconciliationTool reconciliationTool,
                    DiscrepancyTool discrepancyTool, AmenityPremiumTool amenityPremiumTool,
                    FloorRiseTool floorRiseTool, BhkSupplyGapTool bhkSupplyGapTool,
                    DocumentSearchTool documentSearchTool) {
        this.orchestrator = orchestrator;
        this.streamingOrchestrator = streamingOrchestrator;
        this.audit = audit;
        this.models = models;
        this.retriever = retriever;
        this.tools = ToolRegistry.of(List.of(comparisonGridTool, reconciliationTool, discrepancyTool,
                amenityPremiumTool, floorRiseTool, bhkSupplyGapTool, documentSearchTool));
    }

    /**
     * Answer a comparison question over a session's grid. Returns the neutral {@link AgentResult} — the
     * caller (a controller) maps it to a wire shape.
     */
    public AgentResult compare(UUID sessionId, String question) {
        return dispatch(CompareCapability.ID, Map.of("sessionId", sessionId.toString()), question);
    }

    /**
     * Stream the comparison answer to {@code listener} (chunk-by-chunk, then a terminal result) over the
     * kernel's {@link StreamingOrchestrator}. Backs the {@code POST /api/chat} SSE surface (R1.3). Returns
     * the same neutral {@link AgentResult} once streaming completes.
     */
    public AgentResult compareStreaming(UUID sessionId, String question, AgentStreamListener listener) {
        AgentRequest request = new AgentRequest(CompareCapability.ID,
                Map.of("sessionId", sessionId.toString()), Map.of(), question);
        return streamingOrchestrator.run(request, context(), listener);
    }

    /**
     * Explain how each parameter's canonical value was reconciled across sources (ADR-0002). Pass a
     * {@code parameter} to narrow the explanation to one attribute, or {@code null} for the whole session.
     */
    public AgentResult explainParameter(UUID sessionId, ParameterType parameter, String question) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sessionId", sessionId.toString());
        if (parameter != null) {
            context.put("parameter", parameter.name());
        }
        return dispatch(ExplainParameterCapability.ID, context, question);
    }

    /** Audit a session for material gaps between authoritative and advertised values (the RERA-vs-listing check). */
    public AgentResult auditDiscrepancies(UUID sessionId, String question) {
        return dispatch(AuditDiscrepanciesCapability.ID, Map.of("sessionId", sessionId.toString()), question);
    }

    /** Compare projects on the price premium paid for additional amenities (analytics engine). */
    public AgentResult analyzeAmenityPremium(UUID sessionId, String question) {
        return dispatch(AmenityPremiumCapability.ID, Map.of("sessionId", sessionId.toString()), question);
    }

    /** Rank projects by their per-floor surcharge / floor-rise premium (analytics engine). */
    public AgentResult analyzeFloorRise(UUID sessionId, String question) {
        return dispatch(FloorRiseCapability.ID, Map.of("sessionId", sessionId.toString()), question);
    }

    /** Map BHK configuration supply across the market and surface under-supplied gaps (analytics engine). */
    public AgentResult analyzeBhkSupplyGap(UUID sessionId, String question) {
        return dispatch(BhkSupplyGapCapability.ID, Map.of("sessionId", sessionId.toString()), question);
    }

    /**
     * Surface qualitative document grounding for a question (R1.4; ADR-0013): the relevant source passages
     * with citations, never figures. Returns UNAVAILABLE when RAG is disabled or nothing is found.
     */
    public AgentResult searchDocuments(String question) {
        return dispatch(DocumentSearchCapability.ID, Map.of(), question);
    }

    private AgentResult dispatch(String capabilityId, Map<String, Object> screenContext, String question) {
        AgentRequest request = new AgentRequest(capabilityId, screenContext, Map.of(), question);
        return orchestrator.run(request, context());
    }

    /** The read-only handle bag a capability may use: the tools, model router, retriever, and audit sink. */
    private AgentContext context() {
        return AgentContext.builder().tools(tools).models(models).retriever(retriever).audit(audit).build();
    }
}
