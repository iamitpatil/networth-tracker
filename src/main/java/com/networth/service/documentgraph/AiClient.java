package com.networth.service.documentgraph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networth.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AiClient {

    private final ObjectMapper objectMapper;
    private final AiConfig config;

    public AiClient(ObjectMapper objectMapper, AiConfig config) {
        this.objectMapper = objectMapper;
        this.config = config;
        log.info("AiClient initialized — server: {}, model: {}, temp: {}", config.getServerUrl(), config.getModel(), config.getTemperature());
    }

    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, List.of());
    }

    public String chat(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        var response = chatWithTools(systemPrompt, userMessage, history, null);
        return response != null ? response.content() : null;
    }

    public ChatWithToolsResponse chatWithTools(
            String systemPrompt,
            String userMessage,
            List<Map<String, String>> history,
            List<Map<String, Object>> tools) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getModel());
            body.put("temperature", config.getTemperature());
            body.put("stream", false);

            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsArray = body.putArray("tools");
                for (Map<String, Object> tool : tools) {
                    toolsArray.add(objectMapper.valueToTree(tool));
                }
                body.put("tool_choice", "auto");
            }

            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            if (history != null) {
                for (Map<String, String> m : history) {
                    messages.addObject()
                            .put("role", m.getOrDefault("role", "user"))
                            .put("content", m.get("content"));
                }
            }
            messages.addObject().put("role", "user").put("content", userMessage);

            String responseBody = post(body);
            if (responseBody == null) return null;

            Map<String, Object> responseMap = objectMapper.readValue(responseBody, new TypeReference<>() {});
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            if (msg == null) return null;

            String content = (String) msg.get("content");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
            String reasoning = msg.get("reasoning_content") != null ? msg.get("reasoning_content").toString() : null;

            return new ChatWithToolsResponse(content, toolCalls, reasoning);
        } catch (java.net.ConnectException e) {
            log.warn("AI server not running on {}", config.getServerUrl());
            return null;
        } catch (Exception e) {
            log.warn("AI client error: {}", e.getMessage());
            return null;
        }
    }

    public ChatWithToolsResponse chatWithToolsFull(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getModel());
            body.put("temperature", config.getTemperature());
            body.put("stream", false);

            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsArray = body.putArray("tools");
                for (Map<String, Object> tool : tools) {
                    toolsArray.add(objectMapper.valueToTree(tool));
                }
                body.put("tool_choice", "auto");
            }

            ArrayNode msgsArray = body.putArray("messages");
            for (Map<String, Object> m : messages) {
                msgsArray.add(objectMapper.valueToTree(m));
            }

            String responseBody = post(body);
            if (responseBody == null) return null;

            Map<String, Object> responseMap = objectMapper.readValue(responseBody, new TypeReference<>() {});
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            if (msg == null) return null;

            String content = (String) msg.get("content");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
            String reasoning = msg.get("reasoning_content") != null ? msg.get("reasoning_content").toString() : null;

            return new ChatWithToolsResponse(content, toolCalls, reasoning);
        } catch (java.net.ConnectException e) {
            log.warn("AI server not running on {}", config.getServerUrl());
            return null;
        } catch (Exception e) {
            log.warn("AI client error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Callback interface for streaming AI responses token by token.
     */
    public interface StreamCallback {
        void onReasoningToken(String token);
        void onContentToken(String token);
        void onToolCallDelta(int index, String id, String name, String argumentsDelta);
        void onComplete(String finishReason);
        void onError(String error);
    }

    /**
     * Streaming version of chatWithToolsFull. Calls llama.cpp with stream=true
     * and forwards tokens via the callback. Returns the assembled final response.
     */
    @SuppressWarnings("unchecked")
    public ChatWithToolsResponse chatWithToolsStreaming(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            StreamCallback callback) {
        HttpURLConnection conn = null;
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.getModel());
            body.put("temperature", config.getTemperature());
            body.put("stream", true);

            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsArray = body.putArray("tools");
                for (Map<String, Object> tool : tools) {
                    toolsArray.add(objectMapper.valueToTree(tool));
                }
                body.put("tool_choice", "auto");
            }

            ArrayNode msgsArray = body.putArray("messages");
            for (Map<String, Object> m : messages) {
                msgsArray.add(objectMapper.valueToTree(m));
            }

            URI uri = URI.create(config.getServerUrl());
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(config.getConnectTimeoutMs());
            conn.setReadTimeout(config.getReadTimeoutMs());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsBytes(body));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                String errMsg = "AI server returned " + status;
                callback.onError(errMsg);
                return null;
            }

            // Parse SSE stream
            StringBuilder reasoningBuf = new StringBuilder();
            StringBuilder contentBuf = new StringBuilder();
            // Tool calls: index -> {id, name, argumentsBuf}
            Map<Integer, Map<String, Object>> toolCallBuilders = new LinkedHashMap<>();
            String finishReason = null;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank() || !line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;

                    Map<String, Object> chunk = objectMapper.readValue(data, new TypeReference<>() {});
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                    if (choices == null || choices.isEmpty()) continue;

                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                    if (delta == null) continue;

                    // Reasoning tokens
                    if (delta.containsKey("reasoning_content") && delta.get("reasoning_content") != null) {
                        String token = delta.get("reasoning_content").toString();
                        reasoningBuf.append(token);
                        callback.onReasoningToken(token);
                    }

                    // Content tokens
                    if (delta.containsKey("content") && delta.get("content") != null) {
                        String token = delta.get("content").toString();
                        contentBuf.append(token);
                        callback.onContentToken(token);
                    }

                    // Tool call deltas
                    if (delta.containsKey("tool_calls")) {
                        List<Map<String, Object>> tcDeltas = (List<Map<String, Object>>) delta.get("tool_calls");
                        if (tcDeltas != null) {
                            for (Map<String, Object> tcDelta : tcDeltas) {
                                int idx = tcDelta.get("index") != null ? ((Number) tcDelta.get("index")).intValue() : 0;
                                Map<String, Object> builder = toolCallBuilders.computeIfAbsent(idx, k -> {
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("id", null);
                                    m.put("name", null);
                                    m.put("argumentsBuf", new StringBuilder());
                                    return m;
                                });

                                if (tcDelta.containsKey("id")) builder.put("id", tcDelta.get("id").toString());
                                Map<String, Object> fn = (Map<String, Object>) tcDelta.get("function");
                                if (fn != null) {
                                    if (fn.containsKey("name") && fn.get("name") != null) builder.put("name", fn.get("name").toString());
                                    if (fn.containsKey("arguments") && fn.get("arguments") != null) {
                                        ((StringBuilder) builder.get("argumentsBuf")).append(fn.get("arguments").toString());
                                        callback.onToolCallDelta(idx, (String) builder.get("id"), (String) builder.get("name"), fn.get("arguments").toString());
                                    }
                                }
                            }
                        }
                    }

                    // Finish reason
                    if (choice.containsKey("finish_reason") && choice.get("finish_reason") != null) {
                        finishReason = choice.get("finish_reason").toString();
                    }
                }
            }

            callback.onComplete(finishReason != null ? finishReason : "stop");

            // Assemble tool calls
            List<Map<String, Object>> assembledToolCalls = null;
            if (!toolCallBuilders.isEmpty()) {
                assembledToolCalls = new ArrayList<>();
                for (Map.Entry<Integer, Map<String, Object>> entry : toolCallBuilders.entrySet()) {
                    Map<String, Object> b = entry.getValue();
                    Map<String, Object> tc = new LinkedHashMap<>();
                    tc.put("id", b.get("id") != null ? b.get("id") : "call_" + UUID.randomUUID().toString().substring(0, 8));
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", b.get("name"));
                    fn.put("arguments", ((StringBuilder) b.get("argumentsBuf")).toString());
                    tc.put("function", fn);
                    tc.put("type", "function");
                    assembledToolCalls.add(tc);
                }
            }

            String reasoning = reasoningBuf.length() > 0 ? reasoningBuf.toString() : null;
            String content = contentBuf.length() > 0 ? contentBuf.toString() : null;
            return new ChatWithToolsResponse(content, assembledToolCalls, reasoning);

        } catch (java.net.ConnectException e) {
            callback.onError("AI server not running on " + config.getServerUrl());
            return null;
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("AI streaming error: {}", errMsg);
            callback.onError(errMsg);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String post(ObjectNode body) throws Exception {
        URI uri = URI.create(config.getServerUrl());
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(config.getConnectTimeoutMs());
            conn.setReadTimeout(config.getReadTimeoutMs());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsBytes(body));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                InputStream errStream = conn.getErrorStream();
                if (errStream != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(errStream, StandardCharsets.UTF_8))) {
                        String errorBody = br.lines().collect(Collectors.joining("\n"));
                        log.warn("AI server returned {}: {}", status, errorBody);
                    }
                } else {
                    log.warn("AI server returned {} with no error body", status);
                }
                return null;
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                return br.lines().collect(Collectors.joining("\n"));
            }
        } finally {
            conn.disconnect();
        }
    }

    public record ChatWithToolsResponse(String content, List<Map<String, Object>> toolCalls, String reasoningContent) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
