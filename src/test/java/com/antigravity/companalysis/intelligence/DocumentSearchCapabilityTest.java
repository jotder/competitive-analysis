package com.antigravity.companalysis.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.antigravity.companalysis.intelligence.internal.DocumentSearchCapability;
import com.antigravity.companalysis.intelligence.internal.DocumentSearchTool;
import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.agent.AgentRequest;
import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.retrieve.Retriever;
import com.gamma.agentkernel.tool.CredibilityTier;
import com.gamma.agentkernel.tool.Evidence;
import com.gamma.agentkernel.tool.ToolRegistry;

/**
 * Tests the R1.4 RAG surface (ADR-0013): the {@code search-documents} capability over the
 * {@link DocumentSearchTool}, driven by a stub {@link Retriever} (no database). It proves the capability
 * surfaces qualitative passages with citations when grounding exists, and abstains (UNAVAILABLE) when the
 * retriever returns nothing (e.g. RAG disabled → {@code Retriever.NONE}) or the question is blank. The full
 * dispatch path through {@code CxoAgent}/the orchestrator is covered by the other integration tests, which
 * now construct {@code CxoAgent} with this tool.
 */
class DocumentSearchCapabilityTest {

    private final DocumentSearchCapability capability = new DocumentSearchCapability();

    @Test
    void surfacesGroundingPassagesWithCitations() {
        Retriever stub = (query, budget) -> List.of(
                new Evidence("Clubhouse and rooftop pool are flagship amenities.", CredibilityTier.INDICATIVE,
                        "doc", "doc:brochure#p3", 0.82, null),
                new Evidence("RERA registration covers towers A and B only.", CredibilityTier.INDICATIVE,
                        "doc", "doc:rera#p2", 0.61, null));
        AgentContext ctx = contextWith(stub);

        AgentResult result = capability.run(
                new AgentRequest("search-documents", Map.of(), Map.of(), "what amenities are offered?"), ctx);

        assertThat(result.status()).isEqualTo(AgentResult.Status.OK);
        assertThat(result.answer())
                .contains("Found 2 relevant document passage(s)")
                .contains("Clubhouse and rooftop pool")
                .contains("[doc:brochure#p3]")
                .contains("[doc:rera#p2]")
                .contains("Context only"); // never derives figures from the passages
        assertThat(result.evidence()).hasSize(2);
    }

    @Test
    void abstainsWhenRagDisabledOrNoGrounding() {
        AgentResult result = capability.run(
                new AgentRequest("search-documents", Map.of(), Map.of(), "anything?"), contextWith(Retriever.NONE));

        assertThat(result.status()).isEqualTo(AgentResult.Status.UNAVAILABLE);
    }

    @Test
    void abstainsWhenQuestionIsBlank() {
        Retriever stub = (query, budget) -> List.of(
                new Evidence("ignored", CredibilityTier.INDICATIVE, "doc", "doc:x", 1.0, null));

        AgentResult result = capability.run(
                new AgentRequest("search-documents", Map.of(), Map.of(), "   "), contextWith(stub));

        assertThat(result.status()).isEqualTo(AgentResult.Status.UNAVAILABLE);
    }

    private static AgentContext contextWith(Retriever retriever) {
        return AgentContext.builder()
                .tools(ToolRegistry.of(List.of(new DocumentSearchTool())))
                .retriever(retriever)
                .build();
    }
}
