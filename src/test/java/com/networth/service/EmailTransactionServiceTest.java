package com.networth.service;

import com.networth.model.entity.BankAccount;
import com.networth.model.entity.EmailTransaction;
import com.networth.repository.BankAccountRepository;
import com.networth.repository.EmailTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A parsed bank email must not move money until the user says it is right.
 *
 * <p>These emails are prose parsed with regexes, so a wrong reading is expected occasionally.
 * The bug this guards against was that the balance moved at ingest, while the row still said
 * PENDING, and {@code ignore()} had no way to put it back -- so rejecting a bad parse left the
 * account permanently wrong, with nothing in the UI to show it had happened.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailTransactionServiceTest {

    @Mock EmailTransactionRepository emailTransactionRepository;
    @Mock BankAccountRepository bankAccountRepository;
    @InjectMocks EmailTransactionService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID txId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private BankAccount account;

    @BeforeEach
    void setUp() {
        account = new BankAccount();
        account.setId(accountId);
        account.setUserId(userId);
        account.setBankName("HDFC Bank");
        account.setAccountName("HDFC Savings");
        account.setAccountNumber("1234567890");
        account.setBalance(new BigDecimal("10000"));

        when(bankAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(bankAccountRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(account));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(i -> i.getArgument(0));
        when(emailTransactionRepository.save(any(EmailTransaction.class))).thenAnswer(i -> i.getArgument(0));
    }

    private EmailParserService.ParsedEmail parsed(String amount, String balance, String type) {
        EmailParserService.ParsedEmail p = new EmailParserService.ParsedEmail();
        p.gmailMessageId = "msg-1";
        p.sender = "alerts@hdfcbank.net";
        p.subject = "Debit alert";
        p.accountLast4 = "7890";
        p.amount = amount == null ? null : new BigDecimal(amount);
        p.balance = balance == null ? null : new BigDecimal(balance);
        p.transactionType = type;
        return p;
    }

    private EmailTransaction row(String status, String amount, String balance, String type) {
        EmailTransaction tx = EmailTransaction.builder()
                .userId(userId)
                .bankAccountId(accountId)
                .gmailMessageId("msg-1")
                .amount(amount == null ? null : new BigDecimal(amount))
                .balance(balance == null ? null : new BigDecimal(balance))
                .transactionType(type)
                .status(status)
                .build();
        tx.setId(txId);
        when(emailTransactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        return tx;
    }

    // ── ingest ────────────────────────────────────────────────────────

    @Test
    @DisplayName("ingesting an email leaves the balance alone: the row is only PENDING")
    void ingestDoesNotTouchTheBalance() {
        service.saveParsedEmail(parsed("500", null, "DEBIT"), userId);

        assertThat(account.getBalance()).isEqualByComparingTo("10000");
        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("an ingested row starts PENDING and keeps its matched account")
    void ingestRecordsAPendingRow() {
        EmailTransaction saved = service.saveParsedEmail(parsed("500", null, "DEBIT"), userId);

        assertThat(saved.getStatus()).isEqualTo(EmailTransactionService.PENDING);
        // Matched on the last four digits of the account number.
        assertThat(saved.getBankAccountId()).isEqualTo(accountId);
    }

    // ── confirm ───────────────────────────────────────────────────────

    @Test
    @DisplayName("confirming a debit subtracts the amount")
    void confirmAppliesADebit() {
        row(EmailTransactionService.PENDING, "500", null, "DEBIT");

        service.confirm(txId, userId);

        assertThat(account.getBalance()).isEqualByComparingTo("9500");
    }

    @Test
    @DisplayName("confirming a credit adds the amount")
    void confirmAppliesACredit() {
        row(EmailTransactionService.PENDING, "500", null, "CREDIT");

        service.confirm(txId, userId);

        assertThat(account.getBalance()).isEqualByComparingTo("10500");
    }

    @Test
    @DisplayName("a quoted closing balance wins over inferring one from the amount")
    void statedBalanceIsPreferred() {
        // The bank telling you the balance is better evidence than one transaction, and it
        // self-corrects if an earlier email was never seen.
        row(EmailTransactionService.PENDING, "500", "7777", "DEBIT");

        service.confirm(txId, userId);

        assertThat(account.getBalance()).isEqualByComparingTo("7777");
    }

    @Test
    @DisplayName("confirming twice applies the balance once")
    void confirmIsIdempotent() {
        row(EmailTransactionService.PENDING, "500", null, "DEBIT");

        service.confirm(txId, userId);
        service.confirm(txId, userId);

        // A double-click or a retried request must not debit the account twice.
        assertThat(account.getBalance()).isEqualByComparingTo("9500");
        verify(bankAccountRepository, times(1)).save(any(BankAccount.class));
    }

    @Test
    @DisplayName("a row matching no account is confirmed without touching any balance")
    void confirmWithoutAMatchedAccount() {
        EmailTransaction tx = row(EmailTransactionService.PENDING, "500", null, "DEBIT");
        tx.setBankAccountId(null);

        service.confirm(txId, userId);

        assertThat(tx.getStatus()).isEqualTo(EmailTransactionService.CONFIRMED);
        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("an amount-only email against an account with no balance is left alone")
    void amountOnlyNeedsAStartingBalance() {
        account.setBalance(null);
        row(EmailTransactionService.PENDING, "500", null, "DEBIT");

        service.confirm(txId, userId);

        assertThat(account.getBalance()).isNull();
        verify(bankAccountRepository, never()).save(any());
    }

    // ── ignore ────────────────────────────────────────────────────────

    @Test
    @DisplayName("ignoring a bad parse leaves the balance untouched, because nothing was applied")
    void ignoreLeavesTheBalanceCorrect() {
        row(EmailTransactionService.PENDING, "500", null, "DEBIT");

        service.ignore(txId, userId);

        assertThat(account.getBalance()).isEqualByComparingTo("10000");
        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("ignoring an already-confirmed row is refused rather than silently downgraded")
    void ignoreRefusesAConfirmedRow() {
        row(EmailTransactionService.CONFIRMED, "500", null, "DEBIT");

        assertThatThrownBy(() -> service.ignore(txId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already confirmed");
    }

    // ── ownership ─────────────────────────────────────────────────────

    @Test
    @DisplayName("another user cannot confirm or ignore your transaction")
    void ownershipIsEnforced() {
        row(EmailTransactionService.PENDING, "500", null, "DEBIT");
        UUID intruder = UUID.randomUUID();

        assertThatThrownBy(() -> service.confirm(txId, intruder))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.ignore(txId, intruder))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("10000");
    }
}
