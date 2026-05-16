package com.networth.controller;

import com.networth.model.entity.EmailTransaction;
import com.networth.model.entity.GmailConnection;
import com.networth.service.EmailTransactionService;
import com.networth.service.GmailOAuthService;
import com.networth.service.GmailSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gmail")
@RequiredArgsConstructor
public class GmailController {

    private final GmailOAuthService oauthService;
    private final GmailSyncService syncService;
    private final EmailTransactionService emailTransactionService;

    @GetMapping("/auth-url")
    public ResponseEntity<Map<String, String>> getAuthUrl() {
        if (!oauthService.isConfigured()) {
            return ResponseEntity.ok(Map.of("configured", "false"));
        }
        return ResponseEntity.ok(Map.of("url", oauthService.getAuthUrl(), "configured", "true"));
    }

    @PostMapping("/callback")
    public ResponseEntity<?> callback(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("code") String code) {
        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            GmailConnection conn = oauthService.connect(userId, code);
            syncService.syncForUser(userId);
            return ResponseEntity.ok(Map.of(
                    "status", "connected",
                    "email", conn.getGmailAddress()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        var conn = oauthService.getConnection(userId);
        if (conn.isEmpty()) {
            return ResponseEntity.ok(Map.of("connected", false));
        }
        GmailConnection c = conn.get();
        return ResponseEntity.ok(Map.of(
                "connected", true,
                "email", c.getGmailAddress(),
                "syncEnabled", c.getSyncEnabled(),
                "lastSyncAt", c.getLastSyncAt() != null ? c.getLastSyncAt().toString() : null));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<?> disconnect(@AuthenticationPrincipal UserDetails userDetails) {
        oauthService.disconnect(UUID.fromString(userDetails.getUsername()));
        return ResponseEntity.ok(Map.of("status", "disconnected"));
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            int count = syncService.syncForUser(UUID.fromString(userDetails.getUsername()));
            return ResponseEntity.ok(Map.of("synced", count));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<EmailTransaction>> getTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String status) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if ("pending".equals(status)) {
            return ResponseEntity.ok(emailTransactionService.getPendingTransactions(userId));
        }
        return ResponseEntity.ok(emailTransactionService.getUserTransactions(userId));
    }

    @PostMapping("/transactions/{id}/confirm")
    public ResponseEntity<EmailTransaction> confirmTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                emailTransactionService.confirm(id, UUID.fromString(userDetails.getUsername())));
    }

    @PostMapping("/transactions/{id}/ignore")
    public ResponseEntity<EmailTransaction> ignoreTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                emailTransactionService.ignore(id, UUID.fromString(userDetails.getUsername())));
    }
}
