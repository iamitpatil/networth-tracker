package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "dividends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dividend {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "holding_id")
    private UUID holdingId;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "dividend_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal dividendAmount;

    @Column(name = "dividend_type")
    private String dividendType;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "ex_date")
    private LocalDate exDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Builder.Default
    private Boolean reinvested = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
