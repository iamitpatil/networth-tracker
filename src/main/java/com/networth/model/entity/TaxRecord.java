package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tax_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "holding_id")
    private UUID holdingId;

    @Column(name = "gain_type")
    private String gainType;

    @Column(name = "gain_amount", precision = 18, scale = 2)
    private BigDecimal gainAmount;

    @Column(name = "tax_amount", precision = 18, scale = 2)
    private BigDecimal taxAmount;

    private String section;

    @Column(name = "is_harvested")
    @Builder.Default
    private Boolean isHarvested = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
