package com.networth.controller;

import com.networth.service.SIPCalendarService;
import com.networth.service.analytics.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final SIPCalendarService sipCalendarService;

    @GetMapping("/xirr")
    public ResponseEntity<Map<String, Object>> getXIRR(@AuthenticationPrincipal UserDetails userDetails) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        BigDecimal xirr = analyticsService.calculateXIRR(uid);
        return ResponseEntity.ok(Map.of("xirr", xirr));
    }

    @GetMapping("/cagr")
    public ResponseEntity<Map<String, Object>> getCAGR(@AuthenticationPrincipal UserDetails userDetails) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        BigDecimal cagr = analyticsService.calculateCAGR(uid);
        return ResponseEntity.ok(Map.of("cagr", cagr));
    }

    @GetMapping("/allocation")
    public ResponseEntity<Map<String, Object>> getAllocation(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(analyticsService.getAssetAllocation(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/allocation/sector")
    public ResponseEntity<Map<String, Object>> getSectorAllocation(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(analyticsService.getSectorAllocation(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/risk")
    public ResponseEntity<Map<String, Object>> getRiskMetrics(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(analyticsService.getRiskMetrics(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/sip-calendar")
    public ResponseEntity<Map<String, Object>> getSIPCalendar(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(sipCalendarService.getSIPCalendar(UUID.fromString(userDetails.getUsername())));
    }
}
