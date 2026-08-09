package com.networth.model.entity;

import com.networth.model.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "holding_id", nullable = false)
    private UUID holdingId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal fees = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal taxes = BigDecimal.ZERO;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "settlement_date")
    private LocalDateTime settlementDate;

    /**
     * Holding-period start for the lot this row creates, when it differs from
     * {@code transactionDate}. Demerged shares inherit the original acquisition date under
     * s.2(42A), so they can be long-term the moment they are received.
     */
    @Column(name = "acquisition_date")
    private LocalDateTime acquisitionDate;

    /**
     * Scales the per-share cost of lots acquired before this row: 0.5 for a 1:2 split, 0.85
     * where a demerger apportions 15% of the cost away. Only used by SPLIT and DEMERGER_OUT.
     */
    @Column(name = "adjustment_factor")
    private BigDecimal adjustmentFactor;

    private String notes;

    private String broker;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
