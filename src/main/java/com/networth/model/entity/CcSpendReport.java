package com.networth.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "cc_spend_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CcSpendReport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "card_issuer", length = 100)
    private String cardIssuer;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "card_type", length = 20)
    private String cardType;

    @Column(name = "statement_month", nullable = false, length = 7)
    private String statementMonth; // YYYY-MM

    @Column(name = "statement_date")
    private LocalDate statementDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "total_amount_due", precision = 18, scale = 2)
    private BigDecimal totalAmountDue;

    @Column(name = "minimum_amount_due", precision = 18, scale = 2)
    private BigDecimal minimumAmountDue;

    @Column(name = "previous_balance", precision = 18, scale = 2)
    private BigDecimal previousBalance;

    @Column(name = "payments_received", precision = 18, scale = 2)
    private BigDecimal paymentsReceived;

    @Column(name = "new_charges", precision = 18, scale = 2)
    private BigDecimal newCharges;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spend_summary", columnDefinition = "jsonb")
    private Map<String, Object> spendSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transactions", columnDefinition = "jsonb")
    private List<Map<String, Object>> transactions;

    @Column(name = "document_id")
    private UUID documentId;

    // Payment tracking
    @Column(name = "paid")
    @Builder.Default
    private Boolean paid = false;

    @Column(name = "paid_amount", precision = 18, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_mode", length = 50)
    private String paymentMode; // UPI, NEFT, AUTO_DEBIT, ONLINE, OTHER

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
