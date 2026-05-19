package com.networth.controller;

import com.networth.model.entity.Goal;
import com.networth.model.entity.GoalHolding;
import com.networth.service.FamilyDataService;
import com.networth.service.GoalService;
import com.networth.service.GoalService.GoalProgress;
import com.networth.service.GoalService.GoalRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final FamilyDataService familyDataService;

    @GetMapping
    public ResponseEntity<List<Goal>> getGoals(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Map<String, String> params) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        boolean fam = "f".equals(params.getOrDefault("view", ""));
        return ResponseEntity.ok(familyDataService.getGoals(uid, fam));
    }

    @PostMapping
    public ResponseEntity<Goal> createGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(goalService.createGoal(UUID.fromString(userDetails.getUsername()), request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Goal> getGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(goalService.getGoal(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Goal> updateGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.ok(goalService.updateGoal(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id),
                request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        goalService.deleteGoal(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    // --- Holding Linking ---

    @PostMapping("/{id}/holdings")
    public ResponseEntity<GoalHolding> linkHolding(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        UUID holdingId = UUID.fromString((String) request.get("holdingId"));
        BigDecimal pct = request.containsKey("allocationPct")
                ? new BigDecimal(request.get("allocationPct").toString())
                : BigDecimal.valueOf(100);
        return ResponseEntity.ok(goalService.linkHolding(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id),
                holdingId, pct));
    }

    @GetMapping("/{id}/holdings")
    public ResponseEntity<List<Map<String, Object>>> getLinkedHoldings(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(goalService.getLinkedHoldings(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id)));
    }

    @DeleteMapping("/{id}/holdings/{holdingId}")
    public ResponseEntity<Void> unlinkHolding(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @PathVariable String holdingId) {
        goalService.unlinkHolding(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id),
                UUID.fromString(holdingId));
        return ResponseEntity.noContent().build();
    }

    // --- Progress ---

    @GetMapping("/{id}/progress")
    public ResponseEntity<GoalProgress> getGoalProgress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(goalService.getGoalProgress(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id)));
    }

    /** Backward-compatible endpoint */
    @PostMapping("/{id}/map-holding")
    public ResponseEntity<Void> mapHoldingToGoal(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        String holdingId = (String) request.get("holdingId");
        BigDecimal allocation = new BigDecimal(request.get("allocationPercentage").toString());
        goalService.linkHolding(
                UUID.fromString(userDetails.getUsername()),
                UUID.fromString(id),
                UUID.fromString(holdingId),
                allocation);
        return ResponseEntity.ok().build();
    }
}
