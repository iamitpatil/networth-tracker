package com.networth.repository;

import com.networth.model.entity.Transaction;
import com.networth.model.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByHoldingId(UUID holdingId);
    List<Transaction> findByHoldingIdOrderByTransactionDateDesc(UUID holdingId);
    List<Transaction> findByUserId(UUID userId);
    List<Transaction> findByUserIdAndTransactionDateBetween(UUID userId, LocalDateTime start, LocalDateTime end);
    List<Transaction> findByUserIdAndTransactionType(UUID userId, TransactionType type);
}
