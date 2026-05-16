package com.networth.controller;

import com.networth.service.AIChatService;
import com.networth.service.AIChatService.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AIChatController {

    private final AIChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String mode = (String) request.getOrDefault("mode", "advice");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawHistory = (List<Map<String, Object>>) request.getOrDefault("history", List.of());
        List<ChatMessage> history = rawHistory.stream()
                .map(m -> new ChatMessage((Boolean) m.get("user"), (String) m.get("content")))
                .toList();

        String response = aiChatService.chat(
                userDetails.getUsername(), message, mode, history);
        return ResponseEntity.ok(Map.of("response", response));
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String csv = (String) request.get("csv");
        String userId = userDetails.getUsername();

        List<Map<String, Object>> results;
        if (csv != null && !csv.isBlank()) {
            results = aiChatService.executeCsv(userId, csv);
        } else {
            results = aiChatService.executeImport(userId, message);
        }

        long successCount = results.stream().filter(r -> "success".equals(r.get("status"))).count();
        long errorCount = results.stream().filter(r -> "error".equals(r.get("status"))).count();

        return ResponseEntity.ok(Map.of(
                "success", errorCount == 0,
                "results", results,
                "message", successCount + " transaction(s) added" +
                        (errorCount > 0 ? ", " + errorCount + " error(s)" : "")
        ));
    }
}
