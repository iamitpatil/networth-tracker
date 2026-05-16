package com.networth.model.dto;

import com.networth.model.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRequest {

    @NotBlank(message = "Holding ID is required")
    private String holdingId;

    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private BigDecimal fees;

    private BigDecimal taxes;

    @NotNull(message = "Transaction date is required")
    private LocalDateTime transactionDate;

    private String notes;

    private String broker;
}
