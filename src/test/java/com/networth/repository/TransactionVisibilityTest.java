package com.networth.repository;

import com.networth.model.entity.Transaction;
import com.networth.model.enums.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deleted transaction is invisible to every query, not just the ones somebody remembered to filter.
 *
 * <p>{@code transactions.deleted_at} has had its own index since V20 and, until now, no reader and no
 * writer: {@code TransactionRepository} never mentioned it and {@code getUserTransactions} called a bare
 * {@code findByUserId}. The consequence in the live database was 1,638 of 1,894 rows belonging to holdings
 * their owner had already deleted, every one still showing in the ledger and still feeding the analytics
 * and tax calculators.
 *
 * <p>The filter is a {@code @SQLRestriction} on the entity rather than a condition on each finder, because
 * twelve call sites across eight services read transactions and one missed method would put a deleted row
 * back into a capital-gains figure years later. These tests are what prove the restriction actually binds
 * — including on {@code findById}, which is the easiest thing to assume is exempt.
 */
@DataJpaTest
@ActiveProfiles("test")
class TransactionVisibilityTest {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TestEntityManager em;

    private final UUID userId = UUID.randomUUID();
    private final UUID holdingId = UUID.randomUUID();

    private Transaction save(String qty, String price) {
        return transactionRepository.saveAndFlush(Transaction.builder()
                .holdingId(holdingId)
                .userId(userId)
                .transactionType(TransactionType.BUY)
                .quantity(new BigDecimal(qty))
                .price(new BigDecimal(price))
                .amount(new BigDecimal(qty).multiply(new BigDecimal(price)))
                .transactionDate(LocalDateTime.of(2025, 6, 2, 10, 0))
                .build());
    }

    /** Marked through SQL, exactly as V43 does — the entity's restriction makes it unreachable via JPA. */
    private void markDeleted(UUID id) {
        int updated = jdbcTemplate.update(
                "UPDATE transactions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        assertThat(updated).as("the row must already be in the database to be marked").isEqualTo(1);
        // Without this the finders below answer from the first-level cache and the restriction is never
        // exercised -- the test would pass for the wrong reason if the assertion were inverted.
        em.clear();
    }

    @Test
    @DisplayName("a marked transaction disappears from the user's ledger")
    void markedRowsLeaveTheLedger() {
        Transaction kept = save("10", "100");
        Transaction removed = save("5", "200");
        markDeleted(removed.getId());

        assertThat(transactionRepository.findByUserId(userId))
                .extracting(Transaction::getId)
                .containsExactly(kept.getId());
    }

    @Test
    @DisplayName("and from the per-holding view the tax calculators read")
    void markedRowsLeaveTheHoldingView() {
        Transaction kept = save("10", "100");
        markDeleted(save("5", "200").getId());

        assertThat(transactionRepository.findByHoldingId(holdingId)).hasSize(1);
        assertThat(transactionRepository.findByHoldingIdOrderByTransactionDateDesc(holdingId))
                .extracting(Transaction::getId).containsExactly(kept.getId());
    }

    @Test
    @DisplayName("findById is not an exemption")
    void findByIdIsRestrictedToo() {
        Transaction removed = save("5", "200");
        markDeleted(removed.getId());

        // Worth asserting explicitly: a restriction that covered the derived finders but not findById would
        // let a deleted row back in through any code path that already holds an id.
        assertThat(transactionRepository.findById(removed.getId())).isEmpty();
    }

    @Test
    @DisplayName("the import de-duplication check treats a deleted row as absent")
    void deletedRowsDoNotBlockReimport() {
        Transaction removed = transactionRepository.saveAndFlush(Transaction.builder()
                .holdingId(holdingId).userId(userId).transactionType(TransactionType.BUY)
                .quantity(new BigDecimal("7")).price(new BigDecimal("150"))
                .amount(new BigDecimal("1050")).broker("zerodha")
                .transactionDate(LocalDateTime.of(2025, 6, 2, 10, 0))
                .build());
        markDeleted(removed.getId());

        // Deliberate: having deleted a transaction, importing the statement again should bring it back
        // rather than silently skip it because a hidden row still matches.
        assertThat(transactionRepository.existsByHoldingIdAndBrokerAndTransactionDateAndQuantity(
                holdingId, "zerodha", LocalDateTime.of(2025, 6, 2, 10, 0), new BigDecimal("7")))
                .isFalse();
    }

    @Test
    @DisplayName("the delete report counts marked rows, because the cascade removes them too")
    void theCountIncludesMarkedRows() {
        save("10", "100");
        markDeleted(save("5", "200").getId());

        // Native precisely so it escapes the restriction. Reporting "1 transaction removed" when the
        // database is about to remove two would understate a destructive operation.
        assertThat(transactionRepository.countAllByHoldingIdIncludingDeleted(holdingId)).isEqualTo(2);
        assertThat(transactionRepository.findByHoldingId(holdingId)).hasSize(1);
    }
}
