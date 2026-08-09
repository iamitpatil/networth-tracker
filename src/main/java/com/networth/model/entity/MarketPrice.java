package com.networth.model.entity;

import com.networth.model.enums.AssetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "market_prices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(MarketPrice.MarketPriceId.class)
public class MarketPrice {

    @Id
    @Column(nullable = false)
    private String symbol;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Id
    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * When a provider last confirmed this price.
     *
     * <p>Distinct from {@link #createdAt}, which is the day's <em>first</em> quote and cannot be
     * anything else. Freshness is measured against this one.
     *
     * <p>Deliberately not {@code @UpdateTimestamp}: that only fires when Hibernate finds the row
     * dirty, and a re-fetch that returns the same number is exactly the case that must still count as
     * a confirmation. A price that has not moved would otherwise look permanently stale and be
     * re-fetched on every pass. {@code PriceCache.savePrice} sets it explicitly.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketPriceId implements java.io.Serializable {
        private String symbol;
        private AssetType assetType;
        private LocalDate priceDate;
    }
}
