package com.networth.model.dto;

import com.networth.model.enums.AssetType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
