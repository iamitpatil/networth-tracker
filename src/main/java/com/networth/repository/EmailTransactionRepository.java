package com.networth.repository;

import com.networth.model.entity.EmailTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailTransactionRepository extends JpaRepository<EmailTransaction, UUID> {
    List<EmailTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<EmailTransaction> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);
    Optional<EmailTransaction> findByGmailMessageId(String gmailMessageId);
    boolean existsByGmailMessageId(String gmailMessageId);
    List<EmailTransaction> findByBankAccountIdAndStatusOrderByCreatedAtDesc(UUID bankAccountId, String status);
}
