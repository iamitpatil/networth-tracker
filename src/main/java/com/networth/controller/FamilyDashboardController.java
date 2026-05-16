package com.networth.controller;

import com.networth.service.FamilyDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/family")
@RequiredArgsConstructor
public class FamilyDashboardController {

    private final FamilyDashboardService familyDashboardService;

    @GetMapping("/net-worth")
    public ResponseEntity<Map<String, Object>> getFamilyNetWorth(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam List<String> members) {
        return ResponseEntity.ok(familyDashboardService.getFamilyNetWorth(members.stream().map(UUID::fromString).toList()));
    }

    @GetMapping("/health-score")
    public ResponseEntity<Map<String, Object>> getFamilyHealthScore(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam List<String> members) {
        return ResponseEntity.ok(familyDashboardService.getFamilyHealthScore(members.stream().map(UUID::fromString).toList()));
    }
}
