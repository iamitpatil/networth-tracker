package com.networth.controller;

import com.networth.model.dto.FamilyMemberResponse;
import com.networth.model.dto.FamilyResponse;
import com.networth.model.dto.InviteRequest;
import com.networth.service.FamilyService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/families")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

    @PostMapping
    public ResponseEntity<FamilyResponse> createFamily(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(familyService.createFamily(body.get("name"), userId));
    }

    @GetMapping
    public ResponseEntity<List<FamilyResponse>> getMyFamilies(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(familyService.getMyFamilies(userId));
    }

    @GetMapping("/{familyId}/members")
    public ResponseEntity<List<FamilyMemberResponse>> getFamilyMembers(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID familyId) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(familyService.getFamilyMembers(familyId, userId));
    }

    @PostMapping("/{familyId}/invite")
    public ResponseEntity<FamilyMemberResponse> inviteMember(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID familyId,
            @Valid @RequestBody InviteRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(familyService.inviteMember(familyId, userId, request));
    }

    @PostMapping("/invitations/{membershipId}/respond")
    public ResponseEntity<FamilyMemberResponse> respondToInvitation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID membershipId,
            @RequestBody Map<String, Boolean> body) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        boolean accept = body.getOrDefault("accept", false);
        return ResponseEntity.ok(familyService.respondToInvitation(membershipId, userId, accept));
    }

    @GetMapping("/invitations/pending")
    public ResponseEntity<List<FamilyMemberResponse>> getPendingInvitations(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(familyService.getPendingInvitations(userId));
    }

    @GetMapping("/members")
    public ResponseEntity<List<UUID>> getFamilyMemberIds(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(familyService.getApprovedMemberIds(userId));
    }

    @DeleteMapping("/{familyId}/leave")
    public ResponseEntity<Void> leaveFamily(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID familyId) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        familyService.leaveFamily(familyId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{familyId}")
    public ResponseEntity<Void> deleteFamily(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID familyId) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        familyService.deleteFamily(familyId, userId);
        return ResponseEntity.noContent().build();
    }
}
