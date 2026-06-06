package com.antigravity.companalysis.intelligence;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.model.ModelTier;
import com.gamma.agentkernel.orchestrate.AgentStreamListener;

/**
 * Standalone MockMvc test of the SSE chat surface: {@code POST /api/chat} drives
 * {@link CxoAgent#compareStreaming} and writes one {@code chunk} event per answer fragment, then a
 * terminal {@code done} event. The agent is mocked so the test exercises only the controller's
 * listener→SSE mapping (no model, no database).
 */
class ChatControllerTest {

    private static final UUID SESSION = UUID.randomUUID();

    @Test
    void streamsChunksThenDoneEventAsServerSentEvents() throws Exception {
        CxoAgent agent = mock(CxoAgent.class);
        when(agent.compareStreaming(eq(SESSION), any(), any())).thenAnswer(inv -> {
            AgentStreamListener listener = inv.getArgument(2);
            listener.onChunk("Lodha Amara ");
            listener.onChunk("leads on price (per AUTHORITATIVE).");
            AgentResult result = AgentResult.ok("compare", 1,
                    "Lodha Amara leads on price (per AUTHORITATIVE).", List.of(), List.of(),
                    "deterministic", 1.0, ModelTier.SMALL);
            listener.onComplete(result);
            return result;
        });
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ChatController(agent)).build();

        MvcResult started = mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + SESSION + "\",\"question\":\"compare price\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("chunk")))
                .andExpect(content().string(containsString("Lodha Amara")))
                .andExpect(content().string(containsString("done")));
    }
}
