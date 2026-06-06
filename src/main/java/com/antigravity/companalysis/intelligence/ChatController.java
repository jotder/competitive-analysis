package com.antigravity.companalysis.intelligence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.gamma.agentkernel.agent.AgentResult;
import com.gamma.agentkernel.orchestrate.AgentStreamListener;

/**
 * The agent's streaming chat surface (R1.3; ADR-0012). {@code POST /api/chat} answers a comparison
 * question over a session, streaming the narration to the client as Server-Sent Events: one {@code chunk}
 * event per answer fragment, then a terminal {@code done} event carrying the status, confidence, and full
 * answer. Backed by {@link CxoAgent#compareStreaming}, which runs the kernel's streaming orchestrator over
 * the same neutral pipeline (deterministic grid + Gemini narration when configured).
 */
@RestController
public class ChatController {

    private static final long TIMEOUT_MS = 60_000L;

    private final CxoAgent agent;

    public ChatController(CxoAgent agent) {
        this.agent = agent;
    }

    @PostMapping(value = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        try {
            agent.compareStreaming(request.sessionId(), request.question(), new AgentStreamListener() {
                @Override
                public void onChunk(String delta) {
                    send(emitter, "chunk", delta);
                }

                @Override
                public void onComplete(AgentResult result) {
                    send(emitter, "done", new ChatDone(result.status().name(), result.confidence(),
                            result.answer()));
                }
            });
            emitter.complete();
        } catch (RuntimeException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write SSE event '" + event + "'", e);
        }
    }

    /** Request body: which session to answer over, and the user's question. */
    public record ChatRequest(UUID sessionId, String question) {
    }

    /** Terminal event payload: outcome, confidence, and the full answer. */
    public record ChatDone(String status, double confidence, String answer) {
    }
}
