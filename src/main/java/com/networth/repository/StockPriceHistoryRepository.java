package com.networth.repository;

import com.networth.model.entity.StockPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockPriceHistoryRepository extends JpaRepository<StockPriceHistory, Long> {

    Optional<StockPriceHistory> findBySymbolAndPriceDate(String symbol, LocalDate priceDate);

    List<StockPriceHistory> findBySymbolAndPriceDateBetweenOrderByPriceDate(
            String symbol, LocalDate startDate, LocalDate endDate);

    @Query("SELECT h FROM StockPriceHistory h WHERE h.symbol = :symbol AND h.priceDate <= :date ORDER BY h.priceDate DESC LIMIT 1")
    Optional<StockPriceHistory> findLatestOnOrBefore(
            @Param("symbol") String symbol,
            @Param("date") LocalDate date);

    @Query("SELECT h FROM StockPriceHistory h WHERE h.symbol = :symbol ORDER BY h.priceDate DESC LIMIT 1")
    Optional<StockPriceHistory> findLatest(@Param("symbol") String symbol);

    /**
     * Bulk query: get the latest price_date per symbol in one shot.
     * Returns rows of [symbol, max(price_date)].
     */
    @Query("SELECT h.symbol, MAX(h.priceDate) FROM StockPriceHistory h GROUP BY h.symbol")
    List<Object[]> findLatestDatePerSymbol();

    /**
     * Bulk query: get earliest and latest price_date per symbol.
     * Returns rows of [symbol, min(price_date), max(price_date)].
     */
    @Query("SELECT h.symbol, MIN(h.priceDate), MAX(h.priceDate) FROM StockPriceHistory h GROUP BY h.symbol")
    List<Object[]> findDateRangePerSymbol();
}
