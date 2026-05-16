package com.networth.model.entity;

import com.networth.model.enums.AssetType;
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

@Entity
@Table(name = "holdings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Column(nullable = false)
    private String symbol;

    private String name;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "average_buy_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal averageBuyPrice;

    @Column(name = "current_price", precision = 18, scale = 4)
    private BigDecimal currentPrice;

    @Column(name = "current_value", precision = 18, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "realized_pnl", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(name = "unrealized_pnl", precision = 18, scale = 2)
    private BigDecimal unrealizedPnl;

    @Column(name = "day_change", precision = 18, scale = 4)
    private BigDecimal dayChange;

    @Column(name = "day_change_pct", precision = 18, scale = 4)
    private BigDecimal dayChangePct;

    @Column(length = 3)
    @Builder.Default
    private String currency = "INR";

    private String exchange;

    private String sector;

    private String isin;

    @Column(name = "lock_in_date")
    private LocalDate lockInDate;

    @Column(name = "lock_in_until")
    private LocalDate lockInUntil;

    @Column(name = "demat_account_id")
    private UUID dematAccountId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
