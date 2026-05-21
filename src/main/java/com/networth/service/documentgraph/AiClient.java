package com.networth.service.documentgraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiClient {

    private final ObjectMapper objectMapper;

    private static final String LLAMA_URL = "http://localhost:8082/v1/chat/completions";
    private static final String MODEL = "llama";
    private static final double TEMPERATURE = 0.1;

    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, List.of());
    }

    public String chat(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", MODEL);
            body.put("temperature", TEMPERATURE);
            body.put("stream", false);

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

            URI uri = URI.create(LLAMA_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(120000);
            conn.setReadTimeout(120000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsBytes(body));
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        status >= 400 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String errorBody = br.lines().collect(Collectors.joining());
                    log.warn("AI server returned {}: {}", status, errorBody);
                    return null;
                }
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String responseBody = br.lines().collect(Collectors.joining());
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                    if (msg != null) {
                        return (String) msg.get("content");
                    }
                }
                return null;
            }
        } catch (java.net.ConnectException e) {
            log.warn("AI server not running on {}", LLAMA_URL);
            return null;
        } catch (Exception e) {
            log.warn("AI client error: {}", e.getMessage());
            return null;
        }
    }
}
