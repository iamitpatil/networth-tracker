package com.networth.controller;

import com.networth.config.AiConfig;
import com.networth.service.AIChatService;
import com.networth.service.AIChatService.ChatMessage;
import com.networth.service.DocumentService;
import com.networth.service.documentgraph.mcp.McpAIChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AIChatController {

    private final AIChatService aiChatService;
    private final McpAIChatService mcpAIChatService;
    private final DocumentService documentService;
    private final AiConfig aiConfig;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        String message = request.get("message") != null ? request.get("message").toString() : "";
        String mode = request.getOrDefault("mode", "advice").toString();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawHistory = (List<Map<String, Object>>) request.getOrDefault("history", List.of());
        List<ChatMessage> history = rawHistory.stream()
                .map(m -> new ChatMessage(
                        Boolean.TRUE.equals(m.get("user")),
                        m.get("content") != null ? m.get("content").toString() : ""))
                .toList();
        String response = aiChatService.chat(
                userDetails.getUsername(), message, mode, history);
        return ResponseEntity.ok(Map.of("response", response != null ? response : ""));
    }

    @PostMapping("/chat-v2")
    public ResponseEntity<Map<String, Object>> chatV2(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        String message = buildMessage(request);
        String mode = (String) request.getOrDefault("mode", "advice");
        String sessionId = (String) request.get("sessionId");

        McpAIChatService.McpChatResult result = mcpAIChatService.chat(
                sessionId, userDetails.getUsername(), message, mode);

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("response", result.response());
        body.put("toolCalls", result.toolCalls());
        body.put("sessionId", result.sessionId());
        if (result.pendingActionId() != null) {
            body.put("pendingActionId", result.pendingActionId());
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        String message = buildMessage(request);
        String mode = (String) request.getOrDefault("mode", "advice");
        String sessionId = (String) request.get("sessionId");

        SseEmitter emitter = new SseEmitter(aiConfig.getSseTimeoutMs());
        mcpAIChatService.streamChat(emitter, sessionId, userDetails.getUsername(), message, mode);
        return emitter;
    }

    @PostMapping(value = "/chat-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> chatUpload(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam("message") String message,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "password", required = false) String password) {
        try {
            String enriched = enrichWithFile(message, file, password);
            McpAIChatService.McpChatResult result = mcpAIChatService.chat(
                    sessionId, userDetails.getUsername(), enriched, "advice");
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("response", result.response());
            body.put("toolCalls", result.toolCalls());
            body.put("sessionId", result.sessionId());
            if (result.pendingActionId() != null) body.put("pendingActionId", result.pendingActionId());
            return ResponseEntity.ok(body);
        } catch (PdfPasswordRequiredException e) {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("error", "PASSWORD_REQUIRED");
            body.put("message", e.getMessage());
            return ResponseEntity.status(422).contentType(MediaType.APPLICATION_JSON).body(body);
        }
    }

    @PostMapping(value = "/chat-upload-stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object chatUploadStream(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam("message") String message,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "password", required = false) String password,
            jakarta.servlet.http.HttpServletResponse servletResponse) throws Exception {
        // Extract file content first — may throw PdfPasswordRequiredException
        String enriched;
        try {
            enriched = enrichWithFile(message, file, password);
        } catch (PdfPasswordRequiredException e) {
            // Write JSON error directly to response — bypasses Spring's converter
            servletResponse.setStatus(422);
            servletResponse.setContentType("application/json");
            servletResponse.setCharacterEncoding("UTF-8");
            servletResponse.getWriter().write("{\"error\":\"PASSWORD_REQUIRED\",\"message\":\"" +
                    e.getMessage().replace("\"", "\\\"") + "\"}");
            servletResponse.getWriter().flush();
            return null; // Response already written
        }

        // Save uploaded file as a document for linking later
        String documentId = null;
        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            var doc = documentService.uploadDocument(userId, file, "AI_UPLOAD", "Uploaded via AI chat",
                    null, null, null);
            documentId = doc.getId().toString();
            // Append documentId to enriched message so frontend/tools can reference it
            enriched += "\n\n[documentId: " + documentId + "]";
        } catch (Exception e) {
            log.warn("Failed to save uploaded document: {}", e.getMessage());
        }

        // Success — return SseEmitter directly (Spring handles SSE async natively)
        servletResponse.setContentType("text/event-stream");
        servletResponse.setCharacterEncoding("UTF-8");
        servletResponse.setHeader("Cache-Control", "no-cache");
        SseEmitter emitter = new SseEmitter(aiConfig.getSseTimeoutMs());
        mcpAIChatService.streamChat(emitter, sessionId, userDetails.getUsername(), enriched, "advice");
        return emitter;
    }

    @GetMapping(value = "/tools", produces = "application/json")
    public ResponseEntity<Map<String, Object>> getTools() {
        List<Map<String, String>> tools = mcpAIChatService.getToolDefinitions();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("tools", tools);
        return ResponseEntity.ok(body);
    }

    // ---- Session Management ----

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<Map<String, Object>> sessions = mcpAIChatService.getSessions(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("sessions", sessions));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String sessionId) {
        try {
            Map<String, Object> session = mcpAIChatService.getSession(
                    userDetails.getUsername(), sessionId);
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String sessionId) {
        mcpAIChatService.deleteSession(userDetails.getUsername(), sessionId);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // ---- HITL Action Management ----

    @PostMapping("/actions/{actionId}/confirm")
    public ResponseEntity<Map<String, Object>> confirmAction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String actionId) {
        try {
            Map<String, Object> result = mcpAIChatService.confirmAction(
                    userDetails.getUsername(), actionId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/actions/{actionId}/reject")
    public ResponseEntity<Map<String, Object>> rejectAction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String actionId) {
        try {
            Map<String, Object> result = mcpAIChatService.rejectAction(
                    userDetails.getUsername(), actionId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error", "message", e.getMessage()));
        }
    }

    @GetMapping("/actions/pending")
    public ResponseEntity<Map<String, Object>> getPendingActions(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<Map<String, Object>> actions = mcpAIChatService.getPendingActions(
                userDetails.getUsername());
        return ResponseEntity.ok(Map.of("actions", actions));
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        String message = request.get("message") != null ? request.get("message").toString() : "";
        String csv = request.get("csv") != null ? request.get("csv").toString() : null;
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

    // ---- PDF Utility (used by other controllers too) ----

    @PostMapping(value = "/extract-pdf-text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> extractPdfTextEndpoint(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password) {
        try {
            String text = extractPdfText(file.getBytes(), password);
            if (text == null || text.isBlank()) {
                return ResponseEntity.ok(Map.of("text", "", "message", "No text could be extracted from this PDF"));
            }
            return ResponseEntity.ok(Map.of("text", text, "pages", text.split("\\f").length));
        } catch (PdfPasswordRequiredException e) {
            return ResponseEntity.status(422).body(Map.of(
                    "error", "PASSWORD_REQUIRED",
                    "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "EXTRACTION_FAILED",
                    "message", e.getMessage() != null ? e.getMessage() : "Failed to read PDF"));
        }
    }

    // ---- Private helpers ----

    private String buildMessage(Map<String, Object> request) {
        String message = (String) request.getOrDefault("message", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> fileInfo = (Map<String, Object>) request.get("file");
        if (fileInfo != null) {
            String fileName = (String) fileInfo.getOrDefault("name", "file");
            String fileBase64 = (String) fileInfo.get("base64");
            String fileType = (String) fileInfo.getOrDefault("type", "application/octet-stream");
            if (fileBase64 != null) {
                String prefix = "[Uploaded file: " + fileName + " (" + fileType + ")]\n";
                String fileContent = decodeBase64(fileBase64);
                return prefix + message + "\n\n--- File Content ---\n" + fileContent;
            }
        }
        return message;
    }

    private String enrichWithFile(String message, MultipartFile file, String password) {
        try {
            String fileName = file.getOriginalFilename();
            String contentType = file.getContentType();
            String prefix = "[Uploaded file: " + fileName + " (" + contentType + ")]\n";

            // PDF — extract text using PDFBox
            if ((contentType != null && contentType.equals("application/pdf"))
                    || (fileName != null && fileName.toLowerCase().endsWith(".pdf"))) {
                // PdfPasswordRequiredException propagates to caller for proper HTTP response
                String pdfText = extractPdfText(file.getBytes(), password);
                if (pdfText != null && !pdfText.isBlank()) {
                    return prefix + message + "\n\n--- Extracted PDF Content ---\n" + pdfText;
                }
                return prefix + message + "\n\n[PDF uploaded but no text could be extracted. The PDF may be image-based.]";
            }

            // Images — not yet supported
            if (contentType != null && contentType.startsWith("image/")) {
                return prefix + message + "\n\n[Image uploaded — OCR extraction not yet supported. Please paste the text content instead.]";
            }

            // Text files (CSV, TXT, etc.)
            String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return prefix + message + "\n\n--- File Content ---\n" + content;
        } catch (PdfPasswordRequiredException e) {
            throw e; // Re-throw for controller to handle
        } catch (Exception e) {
            return message + "\n\n[File upload failed: " + (e.getMessage() != null ? e.getMessage() : "unknown error") + "]";
        }
    }

    /**
     * Extract text from a PDF. If the PDF is encrypted, attempts with the given password.
     * Throws PdfPasswordRequiredException if password is needed but not provided or wrong.
     */
    private String extractPdfText(byte[] pdfBytes, String password) {
        try {
            PDDocument doc = (password != null && !password.isBlank())
                    ? Loader.loadPDF(pdfBytes, password)
                    : Loader.loadPDF(pdfBytes);
            try (doc) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String text = stripper.getText(doc);
                return text != null ? text.trim() : null;
            }
        } catch (InvalidPasswordException e) {
            throw new PdfPasswordRequiredException(
                    password != null ? "Incorrect PDF password" : "This PDF is password-protected");
        } catch (Exception e) {
            // Retry without sort
            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                return new PDFTextStripper().getText(doc).trim();
            } catch (InvalidPasswordException e2) {
                throw new PdfPasswordRequiredException("This PDF is password-protected");
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /** Thrown when a PDF requires a password to open. */
    public static class PdfPasswordRequiredException extends RuntimeException {
        public PdfPasswordRequiredException(String message) { super(message); }
    }

    private String decodeBase64(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[Base64 decode failed]";
        }
    }
}
