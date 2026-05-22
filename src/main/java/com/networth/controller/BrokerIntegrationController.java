package com.networth.controller;

import com.networth.service.FeatureFlagService;
import com.networth.service.broker.AccountAggregatorService;
import com.networth.service.broker.UpstoxBrokerService;
import com.networth.service.broker.ZerodhaIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/brokers")
@RequiredArgsConstructor
public class BrokerIntegrationController {

    private final ZerodhaIntegrationService zerodhaService;
    private final UpstoxBrokerService upstoxService;
    private final AccountAggregatorService aaService;
    private final FeatureFlagService featureFlags;

    // ---- Upstox ----

    @GetMapping("/upstox/auth-url")
    public ResponseEntity<?> getUpstoxAuthUrl(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (!featureFlags.isEnabled("upstox-import")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Upstox import is not enabled"));
        }
        String url = upstoxService.getAuthUrl(UUID.fromString(userDetails.getUsername()));
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/upstox/callback")
    public ResponseEntity<Map<String, Object>> upstoxCallback(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> request) {
        if (!featureFlags.isEnabled("upstox-import")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Upstox import is not enabled"));
        }
        String code = request.get("code");
        return ResponseEntity.ok(upstoxService.exchangeCodeForToken(
                UUID.fromString(userDetails.getUsername()), code));
    }

    @PostMapping("/upstox/sync")
    public ResponseEntity<Map<String, Object>> syncUpstoxHoldings(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (!featureFlags.isEnabled("upstox-import")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Upstox import is not enabled"));
        }
        return ResponseEntity.ok(upstoxService.syncHoldings(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/upstox/status")
    public ResponseEntity<Map<String, Object>> getUpstoxStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(upstoxService.getConnectionStatus(UUID.fromString(userDetails.getUsername())));
    }

    @PostMapping("/upstox/disconnect")
    public ResponseEntity<Map<String, String>> disconnectUpstox(
            @AuthenticationPrincipal UserDetails userDetails) {
        upstoxService.disconnect(UUID.fromString(userDetails.getUsername()));
        return ResponseEntity.ok(Map.of("message", "Upstox disconnected"));
    }

    // ---- Zerodha ----

    @GetMapping("/zerodha/auth-url")
    public ResponseEntity<?> getZerodhaAuthUrl(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (!featureFlags.isEnabled("zerodha-import")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Zerodha import is not enabled"));
        }
        String url = zerodhaService.getAuthUrl(UUID.fromString(userDetails.getUsername()));
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/zerodha/callback")
    public ResponseEntity<Map<String, Object>> zerodhaCallback(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> request) {
        if (!featureFlags.isEnabled("zerodha-import")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Zerodha import is not enabled"));
        }
        String requestToken = request.get("request_token");
        return ResponseEntity.ok(zerodhaService.exchangeRequestToken(
                UUID.fromString(userDetails.getUsername()), requestToken));
    }

    @PostMapping("/zerodha/sync")
    public ResponseEntity<Map<String, Object>> syncZerodhaHoldings(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (!featureFlags.isEnabled("zerodha-import")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Zerodha import is not enabled"));
        }
        return ResponseEntity.ok(zerodhaService.syncHoldings(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/zerodha/status")
    public ResponseEntity<Map<String, Object>> getZerodhaStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(zerodhaService.getConnectionStatus(UUID.fromString(userDetails.getUsername())));
    }

    @PostMapping("/zerodha/disconnect")
    public ResponseEntity<Map<String, String>> disconnectZerodha(
            @AuthenticationPrincipal UserDetails userDetails) {
        zerodhaService.disconnect(UUID.fromString(userDetails.getUsername()));
        return ResponseEntity.ok(Map.of("message", "Zerodha disconnected"));
    }

    // ---- Account Aggregator ----

    @PostMapping("/aa/consent")
    public ResponseEntity<Map<String, Object>> initiateConsent(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        String fiuId = request.get("fiuId").toString();
        String fipId = request.get("fipId").toString();
        @SuppressWarnings("unchecked")
        List<String> accountTypes = (List<String>) request.get("accountTypes");
        return ResponseEntity.ok(aaService.initiateConsentRequest(UUID.fromString(userDetails.getUsername()), fiuId, fipId, accountTypes));
    }

    @GetMapping("/aa/fips")
    public ResponseEntity<Map<String, Object>> listSupportedFIPs() {
        return ResponseEntity.ok(aaService.listSupportedFIPs());
    }
}
