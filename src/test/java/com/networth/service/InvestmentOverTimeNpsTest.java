package com.networth.service;

import com.networth.model.dto.TransactionResponse;
import com.networth.model.entity.Holding;
import com.networth.model.entity.StockPriceHistory;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.StockPriceHistoryRepository;
import com.networth.service.market.MarketCalendar;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The same NPS position, valued from its NAV where history exists and from cost where it does not.
 *
 * <p>This is the test for the third finding: {@code NON_MARKET_TYPES} used to contain
 * {@link AssetType#NPS}, so a pension fund was valued at {@code qty × last transaction price} on every
 * date and the chart drew a flat line at cost — for the one asset class whose entire point is
 * compounding. Removing it is only correct if two things hold, and both are asserted below: the market
 * path finds the NAV under the scheme code, and a date with no NAV still falls back to cost rather than
 * to zero.
 *
 * <p>That fallback is what makes the change safe to ship before the 282-scheme backfill has run. A
 * scheme with no history is exactly as it was; a scheme with history grows.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvestmentOverTimeNpsTest {

    @Mock TransactionService transactionService;
    @Mock HoldingRepository holdingRepository;
    @Mock HoldingService holdingService;
    @Mock StockPriceHistoryRepository priceHistoryRepository;

    private InvestmentOverTimeService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID holdingId = UUID.randomUUID();

    private static final String SCHEME = "SM001001";
    private static final BigDecimal UNITS = new BigDecimal("100");
    private static final BigDecimal COST_NAV = new BigDecimal("40");

    private final LocalDate today = LocalDate.now(MarketCalendar.ZONE);
    /** days = 90 gives a one-day interval, so every point in the series is checkable. */
    private final LocalDate cutoff = today.minusDays(90);

    @BeforeEach
    void setUp() {
        service = new InvestmentOverTimeService(transactionService, holdingRepository, holdingService,
                priceHistoryRepository);

        Holding holding = new Holding();
        holding.setId(holdingId);
        holding.setUserId(userId);
        holding.setAssetType(AssetType.NPS);
        holding.setSymbol(SCHEME);
        holding.setQuantity(UNITS);
        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(holding));
        when(holdingRepository.findByUserIdAndAssetType(eq(userId), any())).thenReturn(List.of(holding));
        // Strict validation forces an NPS holding's symbol to be a listed scheme code, which is what
        // stock_price_history is keyed by. This mock states that contract; HoldingServiceValidationTest
        // proves the real method honours it.
        when(holdingService.getEffectiveSymbolForPricing(holding)).thenReturn(SCHEME);

        // One contribution, well before the window, so the position exists for the whole series.
        TransactionResponse contribution = TransactionResponse.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(holdingId.toString())
                .transactionType(TransactionType.SIP)
                .quantity(UNITS)
                .price(COST_NAV)
                .transactionDate(today.minusDays(120).atStartOfDay())
                .build();
        when(transactionService.getUserTransactions(userId.toString())).thenReturn(List.of(contribution));
    }

    private void navHistory(StockPriceHistory... rows) {
        when(priceHistoryRepository.findBySymbolAndPriceDateBetweenOrderByPriceDate(
                eq(SCHEME), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(rows));
    }

    private StockPriceHistory nav(LocalDate date, String value) {
        return StockPriceHistory.builder()
                .symbol(SCHEME).priceDate(date).close(new BigDecimal(value)).source("NPSNAV").build();
    }

    private BigDecimal valueOn(List<Map<String, Object>> series, LocalDate date) {
        return series.stream()
                .filter(p -> date.toString().equals(p.get("date")))
                .map(p -> (BigDecimal) p.get("value"))
                .findFirst().orElseThrow(() -> new AssertionError("no point for " + date));
    }

    @Test
    @DisplayName("a scheme with NAV history is valued at NAV, and the series rises")
    void navHistoryDrivesTheSeries() {
        navHistory(nav(cutoff, "45.0000"), nav(today, "60.0000"));

        List<Map<String, Object>> series = service.getInvestmentOverTime(userId, 90);

        // 100 units × 45, not 100 × 40. The old behaviour returned 4000.00 here and on every other date.
        assertThat(valueOn(series, cutoff)).isEqualByComparingTo("4500.00");
        assertThat(valueOn(series, today)).isEqualByComparingTo("6000.00");
        // Growth is what was missing: the chart used to be a horizontal line at the contribution amount.
        assertThat(valueOn(series, today)).isGreaterThan(valueOn(series, cutoff));

        // Invested never moves — one contribution of 100 × 40 — so the gap between the two lines is the
        // NAV appreciation the chart is meant to show.
        assertThat((BigDecimal) series.get(series.size() - 1).get("invested"))
                .isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("a date between NAVs takes the last published one, not cost")
    void aGapFallsBackToTheLastNav() {
        // NPS publishes on business days only, so a Saturday has no row. The ±10-day walk-back covers it.
        navHistory(nav(cutoff, "45.0000"), nav(today.minusDays(30), "50.0000"), nav(today, "60.0000"));

        List<Map<String, Object>> series = service.getInvestmentOverTime(userId, 90);

        // Three days after the mid NAV and 27 before the last: still 50, and nowhere near cost.
        assertThat(valueOn(series, today.minusDays(27))).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("a scheme with no history at all is still valued at cost, not at zero")
    void noHistoryFallsBackToTransactionPrice() {
        when(priceHistoryRepository.findBySymbolAndPriceDateBetweenOrderByPriceDate(
                anyString(), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        List<Map<String, Object>> series = service.getInvestmentOverTime(userId, 90);

        // The pre-backfill state, and the reason removing NPS from NON_MARKET_TYPES is safe to deploy
        // before the 282-scheme load has run: flat at cost is the old behaviour, and it is what a
        // scheme without history keeps.
        assertThat(valueOn(series, cutoff)).isEqualByComparingTo("4000.00");
        assertThat(valueOn(series, today)).isEqualByComparingTo("4000.00");
        assertThat(valueOn(series, today)).isEqualByComparingTo(valueOn(series, cutoff));
    }

    @Test
    @DisplayName("the first point reaches back before the window, so a cutoff on a Sunday is not cost")
    void theWindowIsWidenedForTheLookback() {
        // Only a NAV from before the cutoff exists — the case a Sunday or a holiday run produces. The
        // query has to start PRICE_LOOKBACK_DAYS early or the walk-back has nothing to find, and the
        // first point silently reads qty × cost while every later point is NAV-priced. Seen live: a
        // 365-day series opened at 4000.00 with 2025-08-08's NAV one day outside the loaded range.
        LocalDate lookbackStart = cutoff.minusDays(10);
        when(priceHistoryRepository.findBySymbolAndPriceDateBetweenOrderByPriceDate(
                eq(SCHEME), eq(lookbackStart), any(LocalDate.class)))
                .thenReturn(List.of(nav(cutoff.minusDays(2), "45.0000")));

        List<Map<String, Object>> series = service.getInvestmentOverTime(userId, 90);

        // Stubbing the exact start date is the assertion: a query starting at the cutoff would not match
        // it, would return an empty list, and would leave this at 4000.00.
        assertThat(valueOn(series, cutoff)).isEqualByComparingTo("4500.00");
    }
}
