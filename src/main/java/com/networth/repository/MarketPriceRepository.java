package com.networth.repository;

import com.networth.model.entity.MarketPrice;
import com.networth.model.enums.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketPriceRepository extends JpaRepository<MarketPrice, MarketPrice.MarketPriceId> {

    @Query("SELECT mp FROM MarketPrice mp WHERE mp.symbol = :symbol AND mp.assetType = :assetType AND mp.priceDate = :date")
    Optional<MarketPrice> findBySymbolAndAssetTypeAndDate(
            @Param("symbol") String symbol,
            @Param("assetType") AssetType assetType,
            @Param("date") LocalDate date);

    @Query("SELECT mp FROM MarketPrice mp WHERE mp.symbol = :symbol AND mp.assetType = :assetType AND mp.priceDate <= :date ORDER BY mp.priceDate DESC LIMIT 1")
    Optional<MarketPrice> findLatestPriceOnOrBefore(
            @Param("symbol") String symbol,
            @Param("assetType") AssetType assetType,
            @Param("date") LocalDate date);

    @Query("SELECT mp FROM MarketPrice mp WHERE mp.symbol = :symbol AND mp.assetType = :assetType ORDER BY mp.priceDate DESC LIMIT 1")
    Optional<MarketPrice> findLatestPrice(
            @Param("symbol") String symbol,
            @Param("assetType") AssetType assetType);

    List<MarketPrice> findBySymbolAndAssetTypeOrderByPriceDateDesc(String symbol, AssetType assetType);

    /**
     * Price history for many symbols in one query, newest first.
     *
     * <p>Lets callers that iterate holdings avoid a query per symbol. The row count is the
     * same as the per-symbol calls it replaces; only the round trips collapse.
     */
    List<MarketPrice> findBySymbolInOrderByPriceDateDesc(Collection<String> symbols);

    List<MarketPrice> findBySymbolAndAssetTypeAndPriceDateBetweenOrderByPriceDate(
            String symbol, AssetType assetType, LocalDate startDate, LocalDate endDate);
}
