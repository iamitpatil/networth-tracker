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
@Table(name = "epf_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EpfAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "uan_number", length = 12)
    private String uanNumber;

    @Column(name = "pf_number", length = 30)
    private String pfNumber;

    @Column(name = "employer_name", length = 200)
    private String employerName;

    @Column(name = "date_of_joining")
    private LocalDate dateOfJoining;

    @Column(name = "employee_contribution_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal employeeContributionRate = new BigDecimal("12.0");

    @Column(name = "employer_contribution_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal employerContributionRate = new BigDecimal("12.0");

    @Column(name = "current_balance", precision = 18, scale = 2)
    private BigDecimal currentBalance;

    @Column(name = "basic_salary", precision = 18, scale = 2)
    private BigDecimal basicSalary;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
