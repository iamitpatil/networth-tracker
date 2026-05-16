package com.networth.service;

import com.networth.model.entity.DematAccount;
import com.networth.repository.DematAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DematAccountService {

    private final DematAccountRepository dematAccountRepository;

    @Transactional(readOnly = true)
    public List<DematAccount> getUserDematAccounts(UUID userId) {
        return dematAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public DematAccount createDematAccount(UUID userId, DematAccountRequest request) {
        if (request.isDefault()) {
            clearExistingDefault(userId);
        }

        DematAccount account = DematAccount.builder()
                .userId(userId)
                .brokerName(request.brokerName())
                .accountNumber(request.accountNumber())
                .accountType(request.accountType() != null ? request.accountType() : "Equity")
                .description(request.description())
                .isDefault(request.isDefault())
                .build();

        return dematAccountRepository.save(account);
    }

    @Transactional
    public DematAccount updateDematAccount(UUID userId, UUID accountId, DematAccountRequest request) {
        DematAccount account = dematAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Demat account not found"));

        if (!account.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to update this demat account");
        }

        if (request.isDefault() && !Boolean.TRUE.equals(account.getIsDefault())) {
            clearExistingDefault(userId);
        }

        if (request.brokerName() != null) account.setBrokerName(request.brokerName());
        if (request.accountNumber() != null) account.setAccountNumber(request.accountNumber());
        if (request.accountType() != null) account.setAccountType(request.accountType());
        if (request.description() != null) account.setDescription(request.description());
        account.setIsDefault(request.isDefault());

        return dematAccountRepository.save(account);
    }

    @Transactional
    public void deleteDematAccount(UUID userId, UUID accountId) {
        DematAccount account = dematAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Demat account not found"));

        if (!account.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to delete this demat account");
        }

        dematAccountRepository.deleteById(accountId);
    }

    private void clearExistingDefault(UUID userId) {
        List<DematAccount> accounts = dematAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (DematAccount acc : accounts) {
            if (Boolean.TRUE.equals(acc.getIsDefault())) {
                acc.setIsDefault(false);
                dematAccountRepository.save(acc);
            }
        }
    }

    public record DematAccountRequest(
            String brokerName,
            String accountNumber,
            String accountType,
            String description,
            boolean isDefault
    ) {}
}
