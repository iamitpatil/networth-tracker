package com.networth.model.dto;

import com.networth.model.enums.TransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponse {

    private String id;
    private String holdingId;
    private TransactionType transactionType;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal fees;
    private BigDecimal taxes;
    private LocalDateTime transactionDate;

    /**
     * Where the holding period starts, when that differs from {@link #transactionDate} --
     * demerged shares inherit the original acquisition date (s.2(42A)). Null for ordinary rows.
     */
    private LocalDateTime acquisitionDate;

    /** Cost adjustment applied to earlier lots by a SPLIT or DEMERGER_OUT. Null otherwise. */
    private BigDecimal adjustmentFactor;

    private String notes;
    private String broker;
    /**
     * The demat account this trade went through.
     *
     * <p>Comes from the transaction's own column rather than being looked up through the holding, so a
     * ledger row is self-describing. Null only for asset types that have no demat account (NPS, PPF, FD,
     * cash, property); for equity, ETF and mutual funds it is always present.
     */
    private String dematAccountId;
    /**
     * When this row was recorded. An instant, so it serialises with an offset and the browser
     * renders it in the viewer's own zone -- unlike {@link #transactionDate}, which is a calendar
     * date and must never be shifted by a client's timezone.
     */
    private Instant createdAt;

    // Owner info (populated in family view for cross-member identification)
    private String ownerId;
    private String ownerName;
}
