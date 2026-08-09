package com.networth.model.entity;

import com.networth.model.enums.TaxRegime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Stores parsed Form 16 (TDS certificate from employer) data.
 *
 * Form 16 is issued annually by employers and contains:
 * - Part A: TDS deducted and deposited with government (quarterly)
 * - Part B: Salary breakup, deductions, and computed tax liability
 */
@Entity
@Table(name = "form16_data", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "financial_year"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Form16Data {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;  // e.g. "2024-2025"

    @Column(name = "assessment_year", length = 9)
    private String assessmentYear; // e.g. "2025-2026"

    // ===== Part A (Employer TDS Summary) =====

    @Column(name = "employer_name")
    private String employerName;

    @Column(name = "employer_pan", length = 20)
    private String employerPan;

    @Column(name = "employer_tan", length = 20)
    private String employerTan;

    @Column(name = "employee_pan", length = 20)
    private String employeePan;

    // ===== Salary Breakup =====

    @Column(name = "gross_salary", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal grossSalary = BigDecimal.ZERO;

    @Column(name = "exempt_allowances", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal exemptAllowances = BigDecimal.ZERO;

    @Column(name = "professional_tax", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal professionalTax = BigDecimal.ZERO;

    @Column(name = "standard_deduction", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal standardDeduction = BigDecimal.ZERO;

    @Column(name = "taxable_salary", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal taxableSalary = BigDecimal.ZERO;

    // ===== TDS (Part A) =====

    @Column(name = "tds_total", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal tdsTotal = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quarterly_tds", columnDefinition = "jsonb")
    private Map<String, Object> quarterlyTds;  // {q1, q2, q3, q4}

    // ===== Deductions (Part B - Old Regime only) =====

    @Column(name = "section_80c", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal section80c = BigDecimal.ZERO;

    @Column(name = "section_80ccd_1b", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal section80ccd1b = BigDecimal.ZERO;

    @Column(name = "section_80d", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal section80d = BigDecimal.ZERO;

    @Column(name = "section_80g", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal section80g = BigDecimal.ZERO;

    @Column(name = "section_80tta", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal section80tta = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "other_deductions", columnDefinition = "jsonb")
    private Map<String, Object> otherDeductions;

    // ===== Computed Totals =====

    @Column(name = "total_taxable_income", precision = 18, scale = 2)
    private BigDecimal totalTaxableIncome;

    @Column(name = "total_tax_liability", precision = 18, scale = 2)
    private BigDecimal totalTaxLiability;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", length = 10)
    @Builder.Default
    private TaxRegime taxRegime = TaxRegime.OLD;

    // ===== Source Tracking =====

    @Column(name = "document_id")
    private UUID documentId;

    @Column(length = 50)
    @Builder.Default
    private String source = "MANUAL";

    @Column(name = "parse_confidence")
    private Integer parseConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private Map<String, Object> rawData;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
