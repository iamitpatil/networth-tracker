package com.networth.controller;

import com.networth.service.DividendService;
import com.networth.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dividends")
@RequiredArgsConstructor
public class DividendController {

    private final DividendService dividendService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDividendSummary(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(dividendService.getDividendSummary(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/passive-income")
    public ResponseEntity<Map<String, Object>> getPassiveIncomeEstimate(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(dividendService.getPassiveIncomeEstimate(UUID.fromString(userDetails.getUsername())));
    }
}
