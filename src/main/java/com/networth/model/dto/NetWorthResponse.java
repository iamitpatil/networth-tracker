package com.networth.model.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetWorthResponse {

    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal netWorth;
    private BigDecimal liquidAssets;
    private BigDecimal equityValue;
    private BigDecimal debtValue;
    private BigDecimal goldValue;
    private BigDecimal realEstateValue;
    private BigDecimal cashValue;
    private BigDecimal cryptoValue;
}
