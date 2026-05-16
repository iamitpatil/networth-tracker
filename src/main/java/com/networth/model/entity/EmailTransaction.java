package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "email_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "bank_account_id")
    private UUID bankAccountId;

    @Column(name = "gmail_message_id", nullable = false)
    private String gmailMessageId;

    @Column(nullable = false)
    private String sender;

    @Column(columnDefinition = "TEXT")
    private String subject;

    @Column(name = "body_preview", columnDefinition = "TEXT")
    private String bodyPreview;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(precision = 18, scale = 2)
    private BigDecimal balance;

    @Column(name = "transaction_type", length = 20)
    private String transactionType;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> rawJson;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
