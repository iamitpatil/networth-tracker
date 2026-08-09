package com.networth.model.dto;

import com.networth.model.enums.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoldingRequest {

    @NotNull(message = "Asset type is required")
    private AssetType assetType;

    @NotBlank(message = "Symbol is required")
    private String symbol;

    private String name;

    @NotNull(message = "Quantity is required")
    private BigDecimal quantity;

    @NotNull(message = "Average buy price is required")
    private BigDecimal averageBuyPrice;

    private String currency;

    private String exchange;

    private String sector;

    private String isin;

    private String dematAccountId;

    private LocalDate lockInUntil;

    /**
     * When the position was originally bought. Used to record the opening lot so cost basis
     * and holding period are correct. Defaults to today when omitted.
     */
    private LocalDate purchaseDate;

    private Map<String, Object> metadata;
}