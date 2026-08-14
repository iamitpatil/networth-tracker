package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One corporate-action event for one symbol, as published by the exchange.
 *
 * <p>Provider data, not user data: these rows are shared by every holding of the symbol, which is the
 * point. A dividend announcement is a fact about the company, so fetching it once and joining locally
 * replaces one provider call per holding per calculation. {@link Dividend} remains the per-holding
 * payout computed from these.
 *
 * <p>Read through JPA; written through {@code JdbcTemplate.batchUpdate} in
 * {@code SymbolEventService}, because no {@code hibernate.jdbc.batch_size} is configured and a
 * per-row {@code save} would issue a round trip each.
 */
@Entity
@Table(name = "symbol_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymbolEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    /** DIVIDEND, BONUS, SPLIT, RIGHTS. */
    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    /**
     * Interim / Final / Special, or empty where the type has no subtype.
     *
     * <p>Never null. It is part of {@code uq_symbol_events}, and PostgreSQL treats NULLs as distinct,
     * so a null here would let two rows for the same symbol and date both through and stop
     * {@code ON CONFLICT} from ever firing.
     */
    @Column(name = "event_subtype", nullable = false, length = 20)
    @Builder.Default
    private String eventSubtype = "";

    @Column(name = "amount_per_share", precision = 18, scale = 4)
    private BigDecimal amountPerShare;

    /** Bonus or split factor. Null for dividends, which carry {@link #amountPerShare}. */
    @Column(precision = 18, scale = 8)
    private BigDecimal ratio;

    @Column(name = "ex_date", nullable = false)
    private LocalDate exDate;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * The date a holder had to be on the register for this event.
     *
     * <p>NSE publishes both, and they usually differ by a day or coincide, but only one of the two is
     * guaranteed present -- Yahoo supplies an ex-date and no record date at all. Preferring the record
     * date and falling back keeps the caller from having to repeat that choice.
     */
    public LocalDate entitlementDate() {
        return recordDate != null ? recordDate : exDate;
    }
}
