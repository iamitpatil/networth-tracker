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
@Table(name = "nps_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NpsAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "pran_number", nullable = false, length = 12)
    private String pranNumber;

    @Column(name = "fund_manager", length = 100)
    private String fundManager;

    @Column(name = "scheme_preference", length = 20)
    private String schemePreference;

    @Column(length = 10)
    @Builder.Default
    private String tier = "TIER1";

    @Column(name = "asset_class", length = 5)
    private String assetClass;

    @Column(length = 20)
    private String cra;

    @Column(name = "opening_date")
    private LocalDate openingDate;

    @Column(name = "employer_name", length = 200)
    private String employerName;

    @Column(name = "scheme_code", length = 20)
    private String schemeCode;

    @Column(precision = 18, scale = 4)
    private BigDecimal units;

    @Column(precision = 18, scale = 4)
    private BigDecimal nav;

    @Column(name = "nav_date")
    private LocalDate navDate;

    @Column(name = "current_value", precision = 18, scale = 2)
    private BigDecimal currentValue;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
