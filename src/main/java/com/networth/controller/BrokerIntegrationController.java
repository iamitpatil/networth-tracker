package com.networth.controller;

import com.networth.service.AIInsightsService;
import com.networth.service.broker.AccountAggregatorService;
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
    private final AccountAggregatorService aaService;

    @GetMapping("/zerodha/holdings")
    public ResponseEntity<List<Map<String, Object>>> fetchZerodhaHoldings(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(zerodhaService.fetchHoldings(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/zerodha/positions")
    public ResponseEntity<List<Map<String, Object>>> fetchZerodhaPositions(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(zerodhaService.fetchPositions(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/zerodha/orders")
    public ResponseEntity<List<Map<String, Object>>> fetchZerodhaOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(zerodhaService.fetchOrders(UUID.fromString(userDetails.getUsername()), from, to));
    }

    @PostMapping("/zerodha/sync")
    public ResponseEntity<Map<String, Object>> syncZerodhaHoldings(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(zerodhaService.syncAllHoldings(UUID.fromString(userDetails.getUsername())));
    }

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
