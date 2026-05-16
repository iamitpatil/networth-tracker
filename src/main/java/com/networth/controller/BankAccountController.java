package com.networth.controller;

import com.networth.model.entity.BankAccount;
import com.networth.service.BankAccountService;
import com.networth.service.BankAccountService.BankAccountRequest;
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
@RequestMapping("/api/v1/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @GetMapping
    public ResponseEntity<List<BankAccount>> getBankAccounts(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(bankAccountService.getUserBankAccounts(UUID.fromString(userDetails.getUsername())));
    }

    @PostMapping
    public ResponseEntity<BankAccount> createBankAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bankAccountService.createBankAccount(UUID.fromString(userDetails.getUsername()), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BankAccount> updateBankAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody BankAccountRequest request) {
        return ResponseEntity.ok(bankAccountService.updateBankAccount(UUID.fromString(userDetails.getUsername()), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBankAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        bankAccountService.deleteBankAccount(UUID.fromString(userDetails.getUsername()), id);
        return ResponseEntity.noContent().build();
    }
}
