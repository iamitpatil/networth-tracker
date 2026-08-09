package com.networth.repository;

import com.networth.model.entity.MarketPrice;
import com.networth.model.enums.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    /**
     * When each of these instruments last had a price confirmed, in one query.
     *
     * <p>For the "as of" stamp the holdings list shows next to every figure. Asking per holding would
     * be a query per row on every page load, and the answer has to come from the row rather than from
     * Redis: a cache miss is not evidence that a price is old, only that nobody has read it lately.
     *
     * <p>The subquery restricts each instrument to its newest price date, so this returns exactly what
     * {@link #findLatestPrice} would have returned one symbol at a time — the badge and the refresh
     * decision must not be able to disagree about the same figure.
     */
    @Query("""
            SELECT mp.symbol AS symbol, mp.assetType AS assetType, mp.updatedAt AS lastConfirmedAt
            FROM MarketPrice mp
            WHERE mp.symbol IN :symbols
              AND mp.priceDate = (SELECT MAX(newest.priceDate) FROM MarketPrice newest
                                  WHERE newest.symbol = mp.symbol AND newest.assetType = mp.assetType)
            """)
    List<LastConfirmed> findLastConfirmedBySymbolIn(@Param("symbols") Collection<String> symbols);

    /** When one instrument's stored price was last confirmed by a provider. */
    interface LastConfirmed {
        String getSymbol();

        AssetType getAssetType();

        Instant getLastConfirmedAt();
    }
}
