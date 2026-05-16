package com.networth.controller;

import com.networth.service.AIInsightsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
public class AIInsightsController {

    private final AIInsightsService aiInsightsService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getInsights(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(aiInsightsService.generateInsights(UUID.fromString(userDetails.getUsername())));
    }
}
