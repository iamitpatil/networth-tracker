package com.networth.controller;

import com.networth.model.entity.*;
import com.networth.service.AccountsHubService;
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
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountsHubController {

    private final AccountsHubService accountsHubService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllAccounts(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(accountsHubService.getAllAccounts(userId));
    }

    // ── Credit Cards ──

    @PostMapping("/credit-cards")
    public ResponseEntity<CreditCard> createCreditCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody CreditCard card) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountsHubService.createCreditCard(userId, card));
    }

    @PutMapping("/credit-cards/{id}")
    public ResponseEntity<CreditCard> updateCreditCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody CreditCard card) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(accountsHubService.updateCreditCard(userId, UUID.fromString(id), card));
    }

    @DeleteMapping("/credit-cards/{id}")
    public ResponseEntity<Void> deleteCreditCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        accountsHubService.deleteCreditCard(userId, UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    // ── NPS/PRAN Accounts ──

    @PostMapping("/nps")
    public ResponseEntity<NpsAccount> createNpsAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody NpsAccount account) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountsHubService.createNpsAccount(userId, account));
    }

    @PutMapping("/nps/{id}")
    public ResponseEntity<NpsAccount> updateNpsAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody NpsAccount account) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(accountsHubService.updateNpsAccount(userId, UUID.fromString(id), account));
    }

    @DeleteMapping("/nps/{id}")
    public ResponseEntity<Void> deleteNpsAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        accountsHubService.deleteNpsAccount(userId, UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    // ── PPF Accounts ──

    @PostMapping("/ppf")
    public ResponseEntity<PpfAccount> createPpfAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PpfAccount account) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountsHubService.createPpfAccount(userId, account));
    }

    @PutMapping("/ppf/{id}")
    public ResponseEntity<PpfAccount> updatePpfAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody PpfAccount account) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(accountsHubService.updatePpfAccount(userId, UUID.fromString(id), account));
    }

    @DeleteMapping("/ppf/{id}")
    public ResponseEntity<Void> deletePpfAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        accountsHubService.deletePpfAccount(userId, UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    // ── EPF Accounts ──

    @PostMapping("/epf")
    public ResponseEntity<EpfAccount> createEpfAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody EpfAccount account) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountsHubService.createEpfAccount(userId, account));
    }

    @PutMapping("/epf/{id}")
    public ResponseEntity<EpfAccount> updateEpfAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody EpfAccount account) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(accountsHubService.updateEpfAccount(userId, UUID.fromString(id), account));
    }

    @DeleteMapping("/epf/{id}")
    public ResponseEntity<Void> deleteEpfAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        accountsHubService.deleteEpfAccount(userId, UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }
}
