package com.networth.service.documentgraph.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.service.documentgraph.mcp.tools.DocumentClassificationTools;
import com.networth.service.documentgraph.mcp.tools.DocumentExtractionTools;
import com.networth.service.documentgraph.mcp.tools.EntityResolutionTools;
import com.networth.service.documentgraph.mcp.tools.ExecutionTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class McpToolClient {

    private final ObjectMapper objectMapper;
    private final Map<String, ToolCallback> toolCallbacks = new ConcurrentHashMap<>();

    public McpToolClient(
            ObjectMapper objectMapper,
            DocumentClassificationTools classificationTools,
            DocumentExtractionTools extractionTools,
            EntityResolutionTools resolutionTools,
            ExecutionTools executionTools) {
        this.objectMapper = objectMapper;

        MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(classificationTools, extractionTools, resolutionTools, executionTools)
                .build();

        for (ToolCallback cb : provider.getToolCallbacks()) {
            toolCallbacks.put(cb.getToolDefinition().name(), cb);
            log.debug("Registered tool: {}", cb.getToolDefinition().name());
        }
        log.info("McpToolClient initialized with {} tools", toolCallbacks.size());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> callTool(String toolName, Map<String, Object> args) {
        ToolCallback callback = toolCallbacks.get(toolName);
        if (callback == null) {
            log.warn("Tool not found: {}", toolName);
            return Map.of("error", "Tool not found: " + toolName);
        }

        try {
            String jsonArgs = objectMapper.writeValueAsString(args);
            String result = callback.call(jsonArgs);
            if (result == null || result.isBlank()) {
                return Map.of("status", "completed");
            }

            // Try parsing as Map first, fall back to wrapping a List
            try {
                return objectMapper.readValue(result, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                try {
                    List<Object> list = objectMapper.readValue(result, new TypeReference<List<Object>>() {});
                    Map<String, Object> wrapped = new LinkedHashMap<>();
                    wrapped.put("items", list);
                    wrapped.put("count", list.size());
                    return wrapped;
                } catch (Exception e2) {
                    Map<String, Object> wrapped = new LinkedHashMap<>();
                    wrapped.put("result", result);
                    return wrapped;
                }
            }
        } catch (Exception e) {
            log.warn("Tool {} execution failed: {}", toolName, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    public List<Map<String, String>> getToolList() {
        List<Map<String, String>> list = new ArrayList<>();
        for (ToolCallback cb : toolCallbacks.values()) {
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("name", cb.getToolDefinition().name());
            entry.put("description", cb.getToolDefinition().description());
            list.add(entry);
        }
        return list;
    }

    public boolean hasTool(String toolName) {
        return toolCallbacks.containsKey(toolName);
    }
}
