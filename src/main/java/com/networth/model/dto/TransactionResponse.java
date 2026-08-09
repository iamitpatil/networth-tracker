package com.networth.model.dto;

import com.networth.model.enums.TransactionType;
import lombok.*;

import java.math.BigDecimal;
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
    private LocalDateTime createdAt;

    // Owner info (populated in family view for cross-member identification)
    private String ownerId;
    private String ownerName;
}
