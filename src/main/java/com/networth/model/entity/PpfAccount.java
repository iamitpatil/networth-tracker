package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "ppf_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PpfAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "bank_or_post_office", nullable = false, length = 100)
    private String bankOrPostOffice;

    @Column(length = 100)
    private String branch;

    @Column(name = "opening_date")
    private LocalDate openingDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(length = 100)
    private String nominee;

    @Column(name = "current_balance", precision = 18, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "current_fy_deposit", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal currentFyDeposit = BigDecimal.ZERO;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal interestRate = new BigDecimal("7.1");

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
