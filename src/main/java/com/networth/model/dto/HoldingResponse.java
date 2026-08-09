package com.networth.model.dto;

import com.networth.model.enums.AssetType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoldingResponse {

    private String id;
    private AssetType assetType;
    private String symbol;
    private String name;
    private BigDecimal quantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal currentPrice;
    private BigDecimal currentValue;
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private BigDecimal dayChange;
    private BigDecimal dayChangePct;
    private String currency;
    private String exchange;
    private String sector;
    private String isin;
    private String dematAccountId;
    private String dematAccountBroker;
    private String dematAccountNumber;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * When a provider last confirmed {@code currentPrice}, or null if none ever has.
     *
     * <p>Not the same as {@code updatedAt}, which moves whenever anything about the holding changes —
     * a quantity edit, a rename — and so said nothing about the age of the price. Without this the
     * screen presented a six-month-old figure and today's figure identically.
     */
    private Instant priceAsOf;

    /**
     * Whether {@code priceAsOf} is old enough that this asset type's price should have been refreshed
     * by now, as judged by the one freshness policy the fetching paths also consult.
     *
     * <p>Computed rather than left to the browser because the answer depends on the exchange calendar:
     * a quote from Friday's close is not stale on Sunday, and a NAV published at 23:00 is not stale at
     * 22:00 the next day. Anything true on Saturday would have to duplicate the trading calendar in
     * JavaScript to work that out.
     */
    private Boolean priceStale;

    // Owner info (populated in family view for cross-member identification)
    private String ownerId;
    private String ownerName;
}
