package com.networth.model.entity;

import com.networth.model.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
/*
 * Deleted transactions are invisible to every query for this entity, not just to the ones somebody
 * remembered to filter.
 *
 * <p>The column has existed with its own index since V20 and had no reader at all: TransactionRepository
 * never referenced it and getUserTransactions called a bare findByUserId. Twelve call sites across eight
 * services read transactions — including CapitalGainsCalculator and TaxHarvestService — so filtering
 * finder by finder means one missed method puts a deleted transaction back into a tax figure, silently
 * and years later. A restriction on the entity cannot be forgotten.
 *
 * <p>It applies to findById and to the import de-duplication check too, both deliberately: a deleted
 * transaction should not block its own re-import.
 *
 * <p>Consequences worth knowing. Native SQL bypasses this, which is how V43 marks rows and how
 * HoldingService counts what a delete will remove. And nothing in the application can read a deleted
 * transaction back, which is acceptable because deletion of a holding is now physical — the marked rows
 * are only the historical ones V43 settled.
 */
@SQLRestriction("deleted_at IS NULL")
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

    /**
     * The demat account this trade went through.
     *
     * <p>Stamped from the holding when the transaction is created, so a ledger row can say where it
     * happened on its own. Before this the only link was indirect, through
     * {@code holding.demat_account_id}, and {@code broker} was a free-text string.
     *
     * <p>Null for asset types that have no demat account — NPS, PPF, EPF, FD, cash, property. For EQUITY,
     * ETF and MUTUAL_FUND it is non-null by construction, because {@code chk_holdings_demat_required}
     * (V42) guarantees the holding it is copied from has one.
     */
    @Column(name = "demat_account_id")
    private UUID dematAccountId;

    /**
     * When this transaction was deleted, or null while it is live.
     *
     * <p>Mapped so the column exists wherever the schema is generated from the entities — the tests run on
     * H2 with {@code ddl-auto=create-drop} and Flyway disabled, and {@code @SQLRestriction} above names this
     * column, so leaving it unmapped would make every transaction query fail there while working in
     * production. That is the kind of difference that only shows up in the test that needed it.
     *
     * <p>Writes go through SQL, not through this field: V43 marks the rows of holdings that were deleted
     * before deletion became physical. Because the restriction hides such a row from every JPA query, it
     * cannot be loaded and re-saved, so nothing can blank this by accident.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
