package com.networth.service;

import com.networth.model.entity.BankAccount;
import com.networth.repository.BankAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository repository;

    @Transactional(readOnly = true)
    public List<BankAccount> getUserBankAccounts(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public BankAccount createBankAccount(UUID userId, BankAccountRequest req) {
        BankAccount account = BankAccount.builder()
                .userId(userId)
                .accountName(req.accountName())
                .bankName(req.bankName())
                .accountNumber(req.accountNumber())
                .accountType(req.accountType() != null ? req.accountType() : "SAVINGS")
                .ifscCode(req.ifscCode())
                .branch(req.branch())
                .balance(req.balance() != null ? req.balance() : BigDecimal.ZERO)
                .build();
        return repository.save(account);
    }

    @Transactional
    public BankAccount updateBankAccount(UUID userId, UUID accountId, BankAccountRequest req) {
        BankAccount account = repository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
        if (!account.getUserId().equals(userId))
            throw new IllegalArgumentException("Access denied");
        if (req.accountName() != null) account.setAccountName(req.accountName());
        if (req.bankName() != null) account.setBankName(req.bankName());
        if (req.accountNumber() != null) account.setAccountNumber(req.accountNumber());
        if (req.accountType() != null) account.setAccountType(req.accountType());
        if (req.ifscCode() != null) account.setIfscCode(req.ifscCode());
        if (req.branch() != null) account.setBranch(req.branch());
        if (req.balance() != null) account.setBalance(req.balance());
        return repository.save(account);
    }

    @Transactional
    public void deleteBankAccount(UUID userId, UUID accountId) {
        BankAccount account = repository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Bank account not found"));
        if (!account.getUserId().equals(userId))
            throw new IllegalArgumentException("Access denied");
        repository.delete(account);
    }

    public record BankAccountRequest(
            String accountName, String bankName, String accountNumber,
            String accountType, String ifscCode, String branch, BigDecimal balance) {}
}
