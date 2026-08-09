package com.networth.repository;

import com.networth.model.entity.EmailTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmailTransactionRepository extends JpaRepository<EmailTransaction, UUID> {
    List<EmailTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<EmailTransaction> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);
    /**
     * Whether a Gmail message has already been ingested for this user.
     *
     * <p>Scoped to the user because message IDs are only unique within one mailbox. Backed by the
     * unique index added in V37, so this is an index probe rather than a scan -- the sync used to
     * load every one of the user's rows once per message and compare in Java.
     */
    boolean existsByUserIdAndGmailMessageId(UUID userId, String gmailMessageId);
    List<EmailTransaction> findByBankAccountIdAndStatusOrderByCreatedAtDesc(UUID bankAccountId, String status);
}
