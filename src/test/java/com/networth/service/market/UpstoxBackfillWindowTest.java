package com.networth.service.market;

import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.StockPriceHistoryRepository;
import com.networth.repository.SymbolRepository;
import com.networth.service.market.provider.ProviderRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which date windows the historical backfill actually asks Upstox for.
 *
 * <p>Two things were wrong, and both are invisible without looking at the request URLs.
 *
 * <p><b>Backward extension was dead code.</b> {@code backfillAll} computes a pre-gap window — from the
 * requested start to the day before the symbol's earliest stored row — and handed it to
 * {@code backfillSymbol}, whose first act is {@code if (!latestDate.isBefore(toDate)) return 0}. For a
 * pre-gap fetch {@code toDate} is by construction before the newest row, so that test was always true
 * and the branch always returned 0 without issuing a request. A symbol's history could therefore only
 * grow forwards from whatever window first created it, which is why twelve held symbols sat at exactly
 * one rolling year (2025-08-11 onwards) and re-running the backfill never moved them.
 *
 * <p><b>Long windows are rejected outright.</b> Upstox serves at most ten years per request: fifteen
 * comes back {@code UDAPI1148 Invalid date range}, measured against the live API. A single request from
 * inception would fail for every symbol, so the window has to be split.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpstoxBackfillWindowTest {

    @Mock RestTemplate restTemplate;
    @Mock ProviderRateLimiter rateLimiter;
    @Mock HoldingRepository holdingRepository;
    @Mock StockPriceHistoryRepository historyRepository;
    @Mock SymbolRepository symbolRepository;
    @Mock JdbcTemplate jdbcTemplate;

    private UpstoxHistoricalService service;

    private static final String SYMBOL = "RELIANCE.NS";
    private static final String ISIN = "INE002A01018";
    private static final LocalDate INCEPTION = LocalDate.of(2000, 1, 3);
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);

    @BeforeEach
    void setUp() {
        service = new UpstoxHistoricalService(restTemplate, rateLimiter, holdingRepository,
                historyRepository, symbolRepository, jdbcTemplate);
        ReflectionTestUtils.setField(service, "accessToken", "test-token");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.upstox.test/v3");

        Holding holding = new Holding();
        holding.setId(UUID.randomUUID());
        holding.setAssetType(AssetType.EQUITY);
        holding.setSymbol(SYMBOL);
        holding.setIsin(ISIN);
        holding.setQuantity(new BigDecimal("10"));
        when(holdingRepository.findAllActive()).thenReturn(List.of(holding));

        // A slot is always available, so nothing is skipped for budget reasons.
        when(rateLimiter.acquire(anyString())).thenReturn(true);
        when(rateLimiter.acquire(anyString(), any())).thenReturn(true);
        // Every request returns no candles: the assertions are about which windows were requested, and an
        // empty response is the real API's answer for a window before the stock listed anyway.
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(java.util.Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(java.util.Map.of()));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object.class))).thenReturn(0L);
    }

    /** Every {from, to} pair the service asked Upstox for, parsed back out of the request URLs. */
    private List<LocalDate[]> requestedWindows() {
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, atLeastOnce())
                .exchange(urls.capture(), eq(HttpMethod.GET), any(), eq(java.util.Map.class));
        // .../historical-candle/NSE_EQ|ISIN/days/1/{to}/{from}
        return urls.getAllValues().stream().map(url -> {
            String[] parts = url.split("/");
            return new LocalDate[]{
                    LocalDate.parse(parts[parts.length - 1]),   // from
                    LocalDate.parse(parts[parts.length - 2]),   // to
            };
        }).toList();
    }

    @Test
    @DisplayName("a hole before the earliest stored row is actually fetched")
    void preGapIsFetched() {
        // Exactly the live situation: one rolling year already stored, and a request reaching back to
        // inception. The old code issued no request at all here.
        LocalDate earliest = LocalDate.of(2025, 8, 11);
        when(historyRepository.findDateRangePerSymbol())
                .thenReturn(List.<Object[]>of(new Object[]{SYMBOL, earliest, TODAY}));
        // Essential to the regression, not scene-setting: the old code's clamp keyed off findLatest, so
        // without a newest row stubbed here the buggy version would have fetched too and this test would
        // pass against it. TODAY is the newest row, which is precisely what made the pre-gap unreachable.
        when(historyRepository.findLatest(SYMBOL)).thenReturn(Optional.of(
                com.networth.model.entity.StockPriceHistory.builder()
                        .symbol(SYMBOL).priceDate(TODAY).close(new BigDecimal("1400")).build()));

        service.backfillAll(INCEPTION, TODAY);

        List<LocalDate[]> windows = requestedWindows();
        assertThat(windows).as("the pre-gap must produce requests").isNotEmpty();
        assertThat(windows.get(0)[0]).as("starts at the requested inception").isEqualTo(INCEPTION);
        // Nothing is asked for at or past what we already hold.
        assertThat(windows.get(windows.size() - 1)[1])
                .as("stops the day before the earliest stored row")
                .isEqualTo(earliest.minusDays(1));
    }

    @Test
    @DisplayName("no request covers more than ten years, because Upstox rejects those")
    void windowsAreChunkedToTenYears() {
        when(historyRepository.findDateRangePerSymbol()).thenReturn(List.of());
        when(historyRepository.findLatest(anyString())).thenReturn(Optional.empty());

        service.backfillAll(INCEPTION, TODAY);   // 2000-01-03 .. 2026-08-13, about 26.6 years

        List<LocalDate[]> windows = requestedWindows();
        assertThat(windows).as("26.6 years needs three requests at a ten-year ceiling").hasSize(3);
        for (LocalDate[] w : windows) {
            assertThat(ChronoUnit.DAYS.between(w[0], w[1]))
                    .as("window %s..%s must stay inside ten years", w[0], w[1])
                    .isLessThanOrEqualTo(366L * 10);
        }
    }

    @Test
    @DisplayName("the chunks are contiguous and cover the whole span exactly once")
    void chunksTileTheSpan() {
        when(historyRepository.findDateRangePerSymbol()).thenReturn(List.of());
        when(historyRepository.findLatest(anyString())).thenReturn(Optional.empty());

        service.backfillAll(INCEPTION, TODAY);

        List<LocalDate[]> windows = requestedWindows();
        assertThat(windows.get(0)[0]).isEqualTo(INCEPTION);
        assertThat(windows.get(windows.size() - 1)[1]).isEqualTo(TODAY);
        for (int i = 1; i < windows.size(); i++) {
            // A gap would silently lose days; an overlap would re-fetch them and waste budget.
            assertThat(windows.get(i)[0])
                    .as("chunk %d starts the day after chunk %d ended", i, i - 1)
                    .isEqualTo(windows.get(i - 1)[1].plusDays(1));
        }
    }

    @Test
    @DisplayName("a forward-only gap still fetches just the missing tail")
    void postGapIsUnchanged() {
        // The behaviour that already worked must keep working: only the days after the newest row.
        LocalDate latest = TODAY.minusDays(5);
        when(historyRepository.findDateRangePerSymbol())
                .thenReturn(List.<Object[]>of(new Object[]{SYMBOL, INCEPTION, latest}));

        service.backfillAll(INCEPTION, TODAY);

        List<LocalDate[]> windows = requestedWindows();
        assertThat(windows).hasSize(1);
        assertThat(windows.get(0)[0]).isEqualTo(latest.plusDays(1));
        assertThat(windows.get(0)[1]).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("a fully covered symbol is not requested at all")
    void fullCoverageAsksForNothing() {
        when(historyRepository.findDateRangePerSymbol())
                .thenReturn(List.<Object[]>of(new Object[]{SYMBOL, INCEPTION, TODAY}));

        service.backfillAll(INCEPTION, TODAY);

        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.GET), any(), eq(java.util.Map.class));
    }

    @Test
    @DisplayName("backfillSymbol keeps its forward-only clamp for callers with no gap information")
    void backfillSymbolStillClamps() {
        // StartupBackfillService and the 03:00 job rely on "everything since the newest row", so the
        // clamp has to survive; it is only wrong for a caller that has already computed a backward gap.
        com.networth.model.entity.StockPriceHistory newest =
                com.networth.model.entity.StockPriceHistory.builder()
                        .symbol(SYMBOL).priceDate(TODAY).close(new BigDecimal("1400")).build();
        when(historyRepository.findLatest(SYMBOL)).thenReturn(Optional.of(newest));

        int written = service.backfillSymbol(SYMBOL, ISIN, INCEPTION, TODAY);

        assertThat(written).isZero();
        verify(restTemplate, never()).exchange(anyString(), eq(HttpMethod.GET), any(), eq(java.util.Map.class));
    }
}