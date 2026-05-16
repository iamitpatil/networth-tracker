package com.networth.controller;

import com.networth.service.RebalancingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rebalance")
@RequiredArgsConstructor
public class RebalancingController {

    private final RebalancingService rebalancingService;

    @GetMapping("/suggestions")
    public ResponseEntity<List<Map<String, Object>>> getRebalancingSuggestions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "moderate") String riskProfile) {
        return ResponseEntity.ok(rebalancingService.getRebalancingSuggestions(UUID.fromString(userDetails.getUsername()), riskProfile));
    }

    @GetMapping("/drift")
    public ResponseEntity<Map<String, Object>> getAllocationDrift(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(rebalancingService.getAllocationDrift(UUID.fromString(userDetails.getUsername())));
    }
}
