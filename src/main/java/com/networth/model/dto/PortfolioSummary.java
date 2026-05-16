package com.networth.model.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSummary {

    private BigDecimal totalInvested;
    private BigDecimal currentValue;
    private BigDecimal totalPnl;
    private BigDecimal realizedPnl;
    private BigDecimal unrealizedPnl;
    private BigDecimal absoluteReturn;
    private Map<String, BigDecimal> assetAllocation;
    private int totalHoldings;
}
