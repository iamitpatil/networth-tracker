package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "demat_account_id")
    private UUID dematAccountId;

    @Column(name = "holding_id")
    private UUID holdingId;

    @Column(name = "salary_id")
    private UUID salaryId;

    @Column(name = "form16_id")
    private UUID form16Id;

    @Column(name = "itr_filing_id")
    private UUID itrFilingId;

    @Column(name = "bank_account_id")
    private UUID bankAccountId;

    // Generic account linking (new approach — replaces per-entity FK columns)
    @Column(name = "account_type", length = 50)
    private String accountType; // BANK, DEMAT, CREDIT_CARD, NPS, PPF, EPF, HOLDING, SALARY, etc.

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false)
    private String storedFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    @Builder.Default
    private boolean encrypted = false;

    @Column(nullable = false)
    @Builder.Default
    private String category = "OTHER";

    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
