package com.networth.model.entity;

import com.networth.model.enums.TaxRegime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Stores ITR (Income Tax Return) filing details.
 * Created after user files their tax return.
 */
@Entity
@Table(name = "itr_filings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "financial_year", "filing_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItrFiling {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "assessment_year", length = 9)
    private String assessmentYear;

    // ===== ITR Form Details =====

    @Column(name = "itr_form_type", length = 20)
    private String itrFormType;  // ITR-1, ITR-2, ITR-3, ITR-4

    @Column(name = "acknowledgement_number", length = 50)
    private String acknowledgementNumber;  // 15-digit ITR-V number

    @Column(name = "filing_date")
    private LocalDate filingDate;

    @Column(name = "filing_type", length = 20)
    @Builder.Default
    private String filingType = "ORIGINAL";  // ORIGINAL, REVISED, BELATED

    @Column(name = "ewaiver_date")
    private LocalDate ewaiverDate;

    @Column(name = "e_verified")
    @Builder.Default
    private Boolean eVerified = false;

    @Column(name = "e_verification_mode", length = 50)
    private String eVerificationMode;

    // ===== Income Summary =====

    @Column(name = "gross_total_income", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal grossTotalIncome = BigDecimal.ZERO;

    @Column(name = "total_deductions", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_taxable_income", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxableIncome = BigDecimal.ZERO;

    // ===== Tax Details =====

    @Column(name = "total_tax_payable", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal totalTaxPayable = BigDecimal.ZERO;

    @Column(name = "tds_total", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal tdsTotal = BigDecimal.ZERO;

    @Column(name = "advance_tax_paid", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal advanceTaxPaid = BigDecimal.ZERO;

    @Column(name = "self_assessment_tax", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal selfAssessmentTax = BigDecimal.ZERO;

    @Column(name = "tax_refund", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal taxRefund = BigDecimal.ZERO;

    @Column(name = "refund_status", length = 50)
    private String refundStatus;  // PENDING, ISSUED, FAILED, ADJUSTED

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_regime", length = 10)
    private TaxRegime taxRegime;

    // ===== Source =====

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
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
