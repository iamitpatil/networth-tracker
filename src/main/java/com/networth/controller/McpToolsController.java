package com.networth.controller;

import com.networth.service.documentgraph.mcp.McpToolClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpToolsController {

    private final McpToolClient mcpToolClient;

    @GetMapping("/tools")
    public ResponseEntity<List<Map<String, String>>> listTools() {
        return ResponseEntity.ok(mcpToolClient.getToolList());
    }

    @PostMapping("/call")
    public ResponseEntity<Map<String, Object>> callTool(@RequestBody Map<String, Object> request) {
        String toolName = (String) request.get("name");
        if (toolName == null || toolName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tool name is required"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request.getOrDefault("arguments", Map.of());

        if (!mcpToolClient.hasTool(toolName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tool not found: " + toolName));
        }

        Map<String, Object> result = mcpToolClient.callTool(toolName, arguments);
        if (result.containsKey("error") && !result.containsKey("status")) {
            return ResponseEntity.internalServerError().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
