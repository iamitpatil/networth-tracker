package com.networth.service.market.provider;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class DividendEvent {
    String symbol;
    String isin;
    BigDecimal amountPerShare;
    String dividendType;
    LocalDate exDate;
    LocalDate recordDate;
    String description;
    String source;
}
