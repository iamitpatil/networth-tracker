package com.networth.repository;

import com.networth.model.entity.MarketPrice;
import com.networth.model.enums.AssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The batched lookup behind the "as of" stamp on the Holdings page.
 *
 * <p>Tested against a database rather than a mock for two reasons. The interface projection binds
 * getters to JPQL aliases at runtime, so a renamed alias compiles and then fails on the first page
 * load. And the correlated subquery is the part that has to be right: the stamp has to describe the
 * same row {@code findLatestPrice} hands the refresh decision, or the screen will say a figure is
 * stale while the sweep is convinced it is current.
 */
@DataJpaTest
@ActiveProfiles("test")
class MarketPriceLastConfirmedTest {

    @Autowired
    private MarketPriceRepository marketPriceRepository;

    @Test
    @DisplayName("the stamp comes from the newest price date, not the newest write")
    void theNewestDateWins() {
        // Backfill order is not chronological order: a gap-filling run writes an older candle after a
        // newer one, so MAX(updatedAt) would have reported the backfill's clock as the current stamp.
        save("INFY", AssetType.EQUITY, LocalDate.of(2026, 8, 7), "1500.00", Instant.parse("2026-08-09T04:00:00Z"));
        save("INFY", AssetType.EQUITY, LocalDate.of(2026, 8, 9), "1512.50", Instant.parse("2026-08-09T09:55:00Z"));
        marketPriceRepository.flush();

        assertThat(marketPriceRepository.findLastConfirmedBySymbolIn(List.of("INFY")))
                .extracting(
                        MarketPriceRepository.LastConfirmed::getSymbol,
                        MarketPriceRepository.LastConfirmed::getAssetType,
                        MarketPriceRepository.LastConfirmed::getLastConfirmedAt)
                .containsExactly(tuple("INFY", AssetType.EQUITY, Instant.parse("2026-08-09T09:55:00Z")));
    }

    @Test
    @DisplayName("it answers exactly what findLatestPrice would have, one symbol at a time")
    void itAgreesWithThePerSymbolQuery() {
        save("INFY", AssetType.EQUITY, LocalDate.of(2026, 8, 7), "1500.00", Instant.parse("2026-08-07T10:00:00Z"));
        save("INFY", AssetType.EQUITY, LocalDate.of(2026, 8, 9), "1512.50", Instant.parse("2026-08-09T09:55:00Z"));
        save("TCS", AssetType.EQUITY, LocalDate.of(2026, 8, 9), "3900.00", Instant.parse("2026-08-09T09:55:00Z"));
        save("INF846K01EW2", AssetType.MUTUAL_FUND, LocalDate.of(2026, 8, 8), "62.4100", Instant.parse("2026-08-08T17:30:00Z"));
        marketPriceRepository.flush();

        for (MarketPriceRepository.LastConfirmed row
                : marketPriceRepository.findLastConfirmedBySymbolIn(List.of("INFY", "TCS", "INF846K01EW2"))) {
            assertThat(marketPriceRepository.findLatestPrice(row.getSymbol(), row.getAssetType()))
                    .get()
                    .extracting(MarketPrice::getUpdatedAt)
                    .isEqualTo(row.getLastConfirmedAt());
        }
    }

    @Test
    @DisplayName("one symbol under two asset types is two stamps")
    void assetTypeIsPartOfTheKey() {
        // A sovereign gold bond and physical gold can carry the same symbol and are priced from
        // different sources; collapsing them would stamp one with the other's age.
        save("SGBAUG28", AssetType.SGB, LocalDate.of(2026, 8, 9), "7100.00", Instant.parse("2026-08-09T09:00:00Z"));
        save("SGBAUG28", AssetType.GOLD, LocalDate.of(2026, 8, 6), "7050.00", Instant.parse("2026-08-06T09:00:00Z"));
        marketPriceRepository.flush();

        assertThat(marketPriceRepository.findLastConfirmedBySymbolIn(List.of("SGBAUG28")))
                .extracting(
                        MarketPriceRepository.LastConfirmed::getAssetType,
                        MarketPriceRepository.LastConfirmed::getLastConfirmedAt)
                .containsExactlyInAnyOrder(
                        tuple(AssetType.SGB, Instant.parse("2026-08-09T09:00:00Z")),
                        tuple(AssetType.GOLD, Instant.parse("2026-08-06T09:00:00Z")));
    }

    @Test
    @DisplayName("a symbol nobody asked about is not returned")
    void theListBoundsTheAnswer() {
        save("INFY", AssetType.EQUITY, LocalDate.of(2026, 8, 9), "1512.50", Instant.parse("2026-08-09T09:55:00Z"));
        save("TCS", AssetType.EQUITY, LocalDate.of(2026, 8, 9), "3900.00", Instant.parse("2026-08-09T09:55:00Z"));
        marketPriceRepository.flush();

        assertThat(marketPriceRepository.findLastConfirmedBySymbolIn(List.of("INFY")))
                .extracting(MarketPriceRepository.LastConfirmed::getSymbol)
                .containsExactly("INFY");
    }

    private void save(String symbol, AssetType assetType, LocalDate priceDate, String price, Instant updatedAt) {
        marketPriceRepository.save(MarketPrice.builder()
                .symbol(symbol)
                .assetType(assetType)
                .priceDate(priceDate)
                .price(new BigDecimal(price))
                .source("TEST")
                .updatedAt(updatedAt)
                .build());
    }
}
