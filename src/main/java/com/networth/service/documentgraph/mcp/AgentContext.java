package com.networth.service.documentgraph.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Thread-local context that allows agent tools to emit SSE events
 * back to the client during execution.
 *
 * Set by McpAIChatService before calling agent tools,
 * read by AgentTools to stream reasoning and progress.
 */
public class AgentContext {

    private static final ThreadLocal<SseEmitter> EMITTER = new ThreadLocal<>();
    private static final ThreadLocal<ObjectMapper> MAPPER = new ThreadLocal<>();
    private static final ThreadLocal<String> AGENT_ID = new ThreadLocal<>();
    private static final ThreadLocal<McpToolClient> TOOL_CLIENT = new ThreadLocal<>();

    public static void set(SseEmitter emitter, ObjectMapper objectMapper, String agentId, McpToolClient toolClient) {
        EMITTER.set(emitter);
        MAPPER.set(objectMapper);
        AGENT_ID.set(agentId);
        TOOL_CLIENT.set(toolClient);
    }

    public static void clear() {
        EMITTER.remove();
        MAPPER.remove();
        AGENT_ID.remove();
        TOOL_CLIENT.remove();
    }

    public static McpToolClient getToolClient() {
        return TOOL_CLIENT.get();
    }

    public static String getAgentId() {
        return AGENT_ID.get();
    }

    /** Send an agent-specific SSE event. Returns false if client disconnected. */
    public static boolean sendEvent(String eventName, Map<String, Object> data) {
        SseEmitter emitter = EMITTER.get();
        ObjectMapper mapper = MAPPER.get();
        if (emitter == null || mapper == null) return true; // No emitter = non-streaming mode

        try {
            // Must use mutable map since we add agentId
            java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>(data);
            payload.put("agentId", AGENT_ID.get());
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(mapper.writeValueAsString(payload)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Convenience: send a progress step */
    public static void step(String message) {
        sendEvent("agent_step", Map.of("step", message));
    }

    /** Convenience: send a reasoning token */
    public static void reasoningToken(String token) {
        sendEvent("agent_reasoning_delta", Map.of("token", token));
    }

    /** Convenience: signal reasoning complete */
    public static void reasoningDone() {
        sendEvent("agent_reasoning_done", Map.of());
    }
}
