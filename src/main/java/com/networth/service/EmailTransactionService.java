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

    /** Row states. Constants because a typo in a string literal creates a silent fourth state. */
    static final String PENDING = "PENDING";
    static final String CONFIRMED = "CONFIRMED";
    static final String IGNORED = "IGNORED";

    private static final String CREDIT = "CREDIT";

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
                .status(PENDING)
                .build();

        // Deliberately does NOT touch the bank balance. The row is PENDING: the user has not
        // agreed the parse is right yet, and a bank alert email is guesswork parsed from prose.
        // Applying it here meant a mis-parsed email silently moved the balance, and ignore()
        // never put it back, so rejecting a bad parse left the wrong figure in place for good.
        // The balance now moves in confirm(), which is what the UI already assumes -- it
        // reloads accounts after confirming and not after ignoring.
        return emailTransactionRepository.save(tx);
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

    /**
     * Moves the linked account's balance to match a confirmed email.
     *
     * <p>A stated closing balance is preferred over an amount: the bank telling you what the
     * balance now is beats inferring it from one transaction, and it self-corrects if an earlier
     * email was missed. Only when no balance is quoted does this fall back to adding or
     * subtracting the amount, which does assume no email was skipped.
     */
    private void applyToBalance(EmailTransaction tx) {
        BankAccount account = bankAccountRepository.findById(tx.getBankAccountId()).orElse(null);
        if (account == null) {
            log.warn("Email transaction {} references bank account {}, which no longer exists",
                    tx.getId(), tx.getBankAccountId());
            return;
        }

        if (tx.getBalance() != null) {
            account.setBalance(tx.getBalance());
            bankAccountRepository.save(account);
            log.info("Set balance for {} to Rs.{} from a confirmed email",
                    account.getAccountName(), tx.getBalance());
            return;
        }

        if (tx.getAmount() == null || tx.getTransactionType() == null) {
            log.info("Email transaction {} quotes neither a balance nor an amount and type, so the "
                    + "balance is left alone", tx.getId());
            return;
        }
        if (account.getBalance() == null) {
            log.info("Account {} has no recorded balance to adjust; set one before confirming "
                    + "amount-only emails", account.getAccountName());
            return;
        }

        BigDecimal newBalance = CREDIT.equals(tx.getTransactionType())
                ? account.getBalance().add(tx.getAmount())
                : account.getBalance().subtract(tx.getAmount());
        account.setBalance(newBalance);
        bankAccountRepository.save(account);
        log.info("Adjusted balance for {} to Rs.{} ({} Rs.{}) from a confirmed email",
                account.getAccountName(), newBalance, tx.getTransactionType(), tx.getAmount());
    }

    /**
     * Accepts a parsed email and applies it to the linked bank account's balance.
     *
     * <p>Idempotent: confirming an already-confirmed row is a no-op rather than a second
     * application. Without that guard a double-click, a retried request or a refreshed tab would
     * debit the account twice, and nothing downstream would reveal it.
     */
    @Transactional
    public EmailTransaction confirm(UUID txId, UUID userId) {
        EmailTransaction tx = emailTransactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!tx.getUserId().equals(userId)) throw new IllegalArgumentException("Access denied");

        if (CONFIRMED.equals(tx.getStatus())) {
            log.debug("Email transaction {} is already confirmed; not re-applying the balance", txId);
            return tx;
        }

        tx.setStatus(CONFIRMED);
        EmailTransaction saved = emailTransactionRepository.save(tx);

        if (tx.getBankAccountId() != null) {
            applyToBalance(tx);
        } else {
            log.info("Email transaction {} confirmed but matched no bank account, so no balance "
                    + "was updated", txId);
        }
        return saved;
    }

    /**
     * Rejects a parsed email.
     *
     * <p>No balance to undo, because nothing was applied at ingest. An already-confirmed row is
     * refused rather than quietly downgraded: its balance change has happened, and this method
     * has no record of what the balance was beforehand, so it cannot reverse it honestly.
     */
    @Transactional
    public EmailTransaction ignore(UUID txId, UUID userId) {
        EmailTransaction tx = emailTransactionRepository.findById(txId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!tx.getUserId().equals(userId)) throw new IllegalArgumentException("Access denied");

        if (CONFIRMED.equals(tx.getStatus())) {
            throw new IllegalArgumentException("This transaction was already confirmed and has "
                    + "been applied to the account balance. Correct the balance on the account "
                    + "instead of ignoring the email.");
        }

        tx.setStatus(IGNORED);
        return emailTransactionRepository.save(tx);
    }

    /** Whether this Gmail message has already been recorded for the user. */
    @Transactional(readOnly = true)
    public boolean alreadyIngested(UUID userId, String gmailMessageId) {
        return emailTransactionRepository.existsByUserIdAndGmailMessageId(userId, gmailMessageId);
    }

    @Transactional(readOnly = true)
    public List<EmailTransaction> getUserTransactions(UUID userId) {
        return emailTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<EmailTransaction> getPendingTransactions(UUID userId) {
        return emailTransactionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, PENDING);
    }
}
