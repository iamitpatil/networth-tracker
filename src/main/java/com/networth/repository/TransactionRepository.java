package com.networth.repository;

import com.networth.model.entity.Transaction;
import com.networth.model.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reads over transactions.
 *
 * <p>None of the finders below mention {@code deleted_at} because they do not need to:
 * {@code @SQLRestriction} on {@link Transaction} applies {@code deleted_at IS NULL} to every query for the
 * entity. Filtering here instead would mean twelve call sites across eight services each having to pick
 * the right method, and one wrong choice puts a deleted transaction back into a capital-gains figure.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByHoldingId(UUID holdingId);
    List<Transaction> findByHoldingIdOrderByTransactionDateDesc(UUID holdingId);
    List<Transaction> findByUserId(UUID userId);
    List<Transaction> findByUserIdAndTransactionDateBetween(UUID userId, LocalDateTime start, LocalDateTime end);
    List<Transaction> findByUserIdAndTransactionType(UUID userId, TransactionType type);
    List<Transaction> findByHoldingIdAndBroker(UUID holdingId, String broker);
    boolean existsByHoldingIdAndBrokerAndTransactionDateAndQuantity(UUID holdingId, String broker, LocalDateTime transactionDate, BigDecimal quantity);

    /**
     * Every transaction row for a holding, <b>including</b> ones marked deleted.
     *
     * <p>Native precisely so it escapes the entity's {@code @SQLRestriction}. It answers "what will the
     * database actually remove", which is what a delete should report; a JPA count would say seven where
     * the cascade removes ten, and understating a destructive operation is worse than not reporting it.
     */
    @Query(value = "SELECT count(*) FROM transactions WHERE holding_id = :holdingId", nativeQuery = true)
    long countAllByHoldingIdIncludingDeleted(@Param("holdingId") UUID holdingId);
}
