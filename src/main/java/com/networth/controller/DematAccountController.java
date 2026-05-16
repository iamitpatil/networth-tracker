package com.networth.controller;

import com.networth.model.entity.DematAccount;
import com.networth.service.DematAccountService;
import com.networth.service.DematAccountService.DematAccountRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demat-accounts")
@RequiredArgsConstructor
public class DematAccountController {

    private final DematAccountService dematAccountService;

    @GetMapping
    public ResponseEntity<List<DematAccount>> getDematAccounts(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                dematAccountService.getUserDematAccounts(UUID.fromString(userDetails.getUsername())));
    }

    @PostMapping
    public ResponseEntity<DematAccount> createDematAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DematAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dematAccountService.createDematAccount(
                        UUID.fromString(userDetails.getUsername()), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DematAccount> updateDematAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody DematAccountRequest request) {
        return ResponseEntity.ok(dematAccountService.updateDematAccount(
                UUID.fromString(userDetails.getUsername()), UUID.fromString(id), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDematAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        dematAccountService.deleteDematAccount(
                UUID.fromString(userDetails.getUsername()), UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }
}
