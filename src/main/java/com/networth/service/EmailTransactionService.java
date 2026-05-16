package com.networth.service;

import com.networth.model.entity.BankAccount;
import com.networth.model.entity.EmailTransaction;
import com.networth.repository.BankAccountRepository;
import com.networth.repository.EmailTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTransactionService {

    private final EmailTransactionRepository emailTransactionRepository;
    private final BankAccountRepository bankAccountRepository;

    @Transactional
    public EmailTransaction saveParsedEmail(EmailParserService.ParsedEmail parsed, UUID userId) {
        UUID matchedAccountId = matchAccount(parsed.accountLast4, parsed.sender, userId);

        EmailTransaction tx = EmailTransaction.builder()
                .userId(userId)
                .bankAccountId(matchedAccountId)
                .gmailMessageId(parsed.gmailMessageId)
                .sender(parsed.sender)
                .subject(parsed.subject)
                .bodyPreview(parsed.bodyPreview)
                .amount(parsed.amount)
                .balance(parsed.balance)
                .transactionType(parsed.transactionType)
                .transactionDate(parsed.transactionDate)
                .status("PENDING")
                .build();

        EmailTransaction saved = emailTransactionRepository.save(tx);

        if (matchedAccountId != null && parsed.amount != null && parsed.transactionType != null) {
            autoUpdateBalance(matchedAccountId, parsed);
        }

        return saved;
    }

    private UUID matchAccount(String accountLast4, String sender, UUID userId) {
        List<BankAccount> accounts = bankAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);

        if (accountLast4 != null) {
            for (BankAccount a : accounts) {
                if (a.getAccountNumber() != null && a.getAccountNumber().endsWith(accountLast4)) {
                    return a.getId();
                }
            }
        }

        for (BankAccount a : accounts) {
            String bankLower = a.getBankName().toLowerCase();
            String senderLower = sender.toLowerCase();
            if (bankLower.contains("hdfc") && senderLower.contains("hdfc")) return a.getId();
            if (bankLower.contains("icici") && senderLower.contains("icici")) return a.getId();
            if (bankLower.contains("sbi") && senderLower.contains("sbi")) return a.getId();
            if (bankLower.contains("axis") && senderLower.contains("axis")) return a.getId();
            if (bankLower.contains("kotak") && senderLower.contains("kotak")) return a.getId();
            if (bankLower.contains("yes") && senderLower.contains("yes")) return a.getId();
            if (bankLower.contains("rbl") && senderLower.contains("rbl")) return a.getId();
        }

        return null;
    }

    private void autoUpdateBalance(UUID bankAccountId, EmailParserService.ParsedEmail parsed) {
        BankAccount account = bankAccountRepository.findById(bankAccountId).orElse(null);
        if (account == null) return;

        if (parsed.balance != null) {
            account.setBalance(parsed.balance);
            bankAccountRepository.save(account);
            log.info("Updated balance for {} to Rs.{} via email", account.getAccountName(), parsed.balance);
        } else if (parsed.amount != null && account.getBalance() != null) {
            BigDecimal newBalance = "CREDIT".equals(parsed.transactionType)
                    ? account.getBalance().add(parsed.amount)
                    : account.getBalance().subtract(parsed.amount);
            account.setBalance(newBalance);
            bankAccountRepository.save(account);
            log.info("Updated balance for {} to Rs.{} ({} Rs.{})",
                    account.getAccountName(), newBalance, parsed.transactionType, parsed.amount);
        }
    }

    @Transactional
    public EmailTransaction confirm(UUID txId, UUID userId) {
        EmailTransaction tx = emailTransactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!tx.getUserId().equals(userId)) throw new IllegalArgumentException("Access denied");
        tx.setStatus("CONFIRMED");
        return emailTransactionRepository.save(tx);
    }

    @Transactional
    public EmailTransaction ignore(UUID txId, UUID userId) {
        EmailTransaction tx = emailTransactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!tx.getUserId().equals(userId)) throw new IllegalArgumentException("Access denied");
        tx.setStatus("IGNORED");
        return emailTransactionRepository.save(tx);
    }

    @Transactional(readOnly = true)
    public List<EmailTransaction> getUserTransactions(UUID userId) {
        return emailTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<EmailTransaction> getPendingTransactions(UUID userId) {
        return emailTransactionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING");
    }
}
