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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demat-accounts")
@RequiredArgsConstructor
public class DematAccountController {

    private final DematAccountService dematAccountService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getDematAccounts(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<DematAccount> accounts = dematAccountService.getUserDematAccounts(
                UUID.fromString(userDetails.getUsername()));
        return ResponseEntity.ok(accounts.stream().map(this::toMaskedResponse).toList());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDematAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody DematAccountRequest request) {
        DematAccount account = dematAccountService.createDematAccount(
                UUID.fromString(userDetails.getUsername()), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toMaskedResponse(account));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateDematAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody DematAccountRequest request) {
        DematAccount account = dematAccountService.updateDematAccount(
                UUID.fromString(userDetails.getUsername()), UUID.fromString(id), request);
        return ResponseEntity.ok(toMaskedResponse(account));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDematAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        dematAccountService.deleteDematAccount(
                UUID.fromString(userDetails.getUsername()), UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toMaskedResponse(DematAccount account) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", account.getId());
        map.put("brokerName", account.getBrokerName());
        map.put("accountNumber", maskNumber(account.getAccountNumber()));
        map.put("accountType", account.getAccountType());
        map.put("description", account.getDescription());
        map.put("isDefault", account.getIsDefault());
        map.put("createdAt", account.getCreatedAt());
        map.put("updatedAt", account.getUpdatedAt());
        return map;
    }

    private String maskNumber(String number) {
        if (number == null || number.length() <= 4) return number;
        return "****" + number.substring(number.length() - 4);
    }
}
