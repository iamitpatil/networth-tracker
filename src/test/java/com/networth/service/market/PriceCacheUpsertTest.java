package com.networth.service.market;

import com.networth.model.entity.MarketPrice;
import com.networth.model.enums.AssetType;
import com.networth.repository.MarketPriceRepository;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Saving a price twice in one day.
 *
 * <p>{@code market_prices} allows one row per symbol per day, and that is deliberate: the daily
 * series is what the charts read. So an intraday refresh has to <em>replace</em> the day's row rather
 * than add to it, and the row has to record when the number was last confirmed — otherwise no caller
 * can tell a quote taken a minute ago from one taken at the opening bell.
 */
@DataJpaTest
@ActiveProfiles("test")
class PriceCacheUpsertTest {

    @Autowired
    private MarketPriceRepository marketPriceRepository;

    private PriceCache priceCache;

    @BeforeEach
    void setUp() {
        // Redis is a write-through cache here and irrelevant to what is being tested.
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        priceCache = new PriceCache(redisTemplate, marketPriceRepository,
                new PriceFreshnessPolicy(new MarketCalendar("Asia/Kolkata")));
    }

    @Test
    @DisplayName("two saves in one day leave one row holding the later price")
    void secondSaveReplacesTheDaysPrice() {
        priceCache.savePrice("INFY", AssetType.EQUITY, new BigDecimal("1500.00"), "UPSTOX");
        priceCache.savePrice("INFY", AssetType.EQUITY, new BigDecimal("1512.50"), "UPSTOX");

        marketPriceRepository.flush();

        assertThat(marketPriceRepository.findAll())
                .as("one row per symbol per day")
                .hasSize(1);
        assertThat(marketPriceRepository.findLatestPrice("INFY", AssetType.EQUITY))
                .get()
                .extracting(MarketPrice::getPrice)
                .asInstanceOf(InstanceOfAssertFactories.BIG_DECIMAL)
                .isEqualByComparingTo("1512.50");
    }

    @Test
    @DisplayName("the second save moves updated_at forward but leaves created_at alone")
    void confirmationTimeMovesForward() {
        priceCache.savePrice("INFY", AssetType.EQUITY, new BigDecimal("1500.00"), "UPSTOX");
        marketPriceRepository.flush();
        MarketPrice opening = marketPriceRepository.findLatestPrice("INFY", AssetType.EQUITY).orElseThrow();
        Instant firstConfirmed = opening.getUpdatedAt();
        Instant created = opening.getCreatedAt();

        priceCache.savePrice("INFY", AssetType.EQUITY, new BigDecimal("1512.50"), "UPSTOX");
        marketPriceRepository.flush();
        MarketPrice refreshed = marketPriceRepository.findLatestPrice("INFY", AssetType.EQUITY).orElseThrow();

        assertThat(refreshed.getUpdatedAt())
                .as("when a provider last confirmed the price")
                .isAfterOrEqualTo(firstConfirmed);
        assertThat(refreshed.getCreatedAt())
                .as("created_at stays on the day's first quote, which is why it cannot measure freshness")
                .isEqualTo(created);
    }

    @Test
    @DisplayName("a re-fetch that returns the same number still counts as a confirmation")
    void anUnchangedPriceIsStillConfirmed() {
        // The trap in leaving this to @UpdateTimestamp: Hibernate finds the row unchanged, skips the
        // write, and the price looks permanently stale. A flat NAV or a closed market would then be
        // re-fetched on every single pass.
        priceCache.savePrice("INFY", AssetType.EQUITY, new BigDecimal("1500.00"), "UPSTOX");
        marketPriceRepository.flush();
        Instant firstConfirmed =
                marketPriceRepository.findLatestPrice("INFY", AssetType.EQUITY).orElseThrow().getUpdatedAt();

        priceCache.savePrice("INFY", AssetType.EQUITY, new BigDecimal("1500.00"), "UPSTOX");
        marketPriceRepository.flush();

        assertThat(marketPriceRepository.findLatestPrice("INFY", AssetType.EQUITY).orElseThrow().getUpdatedAt())
                .isAfterOrEqualTo(firstConfirmed);
    }

    @Test
    @DisplayName("the latest record carries its confirmation time, not just the number")
    void latestRecordExposesTheAge() {
        priceCache.savePrice("TCS", AssetType.EQUITY, new BigDecimal("3900.00"), "UPSTOX");
        marketPriceRepository.flush();

        assertThat(priceCache.getLatestPriceRecord("TCS", AssetType.EQUITY))
                .get()
                .satisfies(record -> {
                    assertThat(record.getUpdatedAt()).isNotNull();
                    assertThat(record.getPriceDate()).isEqualTo(LocalDate.now(MarketCalendar.ZONE));
                });
        assertThat(priceCache.getLatestPriceRecord("NOSUCH", AssetType.EQUITY)).isEmpty();
    }

    @Test
    @DisplayName("different symbols and asset types keep their own rows")
    void rowsAreScopedToTheSymbolAndType() {
        priceCache.savePrice("INFY", AssetType.EQUITY, new BigDecimal("1500.00"), "UPSTOX");
        priceCache.savePrice("INFY", AssetType.ETF, new BigDecimal("1490.00"), "UPSTOX");
        priceCache.savePrice("TCS", AssetType.EQUITY, new BigDecimal("3900.00"), "UPSTOX");

        marketPriceRepository.flush();

        assertThat(marketPriceRepository.findAll()).hasSize(3);
    }
}
