package com.networth.model.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class PriceData {
    BigDecimal price;
    BigDecimal previousClose;
}
