package com.networth.service.market;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Symbol;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.StockPriceHistoryRepository;
import com.networth.repository.SymbolRepository;
import com.networth.service.market.provider.ProviderRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Which instruments the historical backfill spends its Upstox budget on.
 *
 * <p>It used to walk the whole {@code EQUITY} category of the symbols table -- some two thousand
 * instruments -- and then the holdings again on top, so a held stock was requested twice per run and
 * roughly 98% of the requests built history for instruments nobody owns. The 03:00 job exhausted the
 * daily budget long before it reached the symbols on somebody's screen, which is why holdings charts
 * kept coming up empty. The assertion is therefore a count of rate-limiter slots taken: one per held
 * instrument, and none for anything else.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpstoxBackfillScopeTest {

    @Mock RestTemplate restTemplate;
    @Mock ProviderRateLimiter rateLimiter;
    @Mock HoldingRepository holdingRepository;
    @Mock StockPriceHistoryRepository historyRepository;
    @Mock SymbolRepository symbolRepository;

    private UpstoxHistoricalService service;

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 8, 9);

    @BeforeEach
    void setUp() {
        service = new UpstoxHistoricalService(restTemplate, rateLimiter, holdingRepository,
                historyRepository, symbolRepository);
        ReflectionTestUtils.setField(service, "accessToken", "test-token");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.upstox.test/v3");
        when(historyRepository.findDateRangePerSymbol()).thenReturn(List.of());
        when(historyRepository.findLatest(anyString())).thenReturn(Optional.empty());
        // Refusing the slot stops each symbol just short of the HTTP call, so the count of attempts is
        // observable without stubbing a candle response for every one of them.
        when(rateLimiter.acquire("upstox-historical")).thenReturn(false);
    }

    @Test
    @DisplayName("only held equities and ETFs are fetched, once each")
    void theWorkListIsWhatSomebodyHolds() {
        when(holdingRepository.findAllActive()).thenReturn(List.of(
                holding("INFY", AssetType.EQUITY, "INE009A01021"),
                // Same stock, second demat account: one price history, not two.
                holding("INFY", AssetType.EQUITY, "INE009A01021"),
                holding("NIFTYBEES", AssetType.ETF, "INF204KB14I2"),
                holding("AXISBLUECHIP", AssetType.MUTUAL_FUND, "INF846K01EW2"),
                holding("EPF-1234", AssetType.EPF, null)));

        service.backfillAll(FROM, TO);

        // Two instruments, two attempts. The symbols table is no longer consulted for a work list, so
        // its two thousand unheld equities cost nothing.
        verify(rateLimiter, times(2)).acquire("upstox-historical");
        verify(symbolRepository, never()).findByCategory(anyString());
        // Mutual funds are AMFI's job and provident fund has no market at all.
        verify(historyRepository, never()).findLatest("AXISBLUECHIP");
        verify(historyRepository, never()).findLatest("EPF-1234");
    }

    @Test
    @DisplayName("a holding with no ISIN anywhere is skipped rather than guessed at")
    void anUnresolvableSymbolIsSkipped() {
        when(holdingRepository.findAllActive())
                .thenReturn(List.of(holding("WAT", AssetType.EQUITY, null)));
        when(symbolRepository.findById(anyString())).thenReturn(Optional.empty());

        service.backfillAll(FROM, TO);

        // The Upstox instrument key is NSE_EQ|<isin>; without one there is nothing to ask for.
        verifyNoInteractions(restTemplate);
        verify(rateLimiter, never()).acquire(anyString());
    }

    @Test
    @DisplayName("a missing ISIN found in the symbols table is written back to the holding")
    void aResolvedIsinIsPersisted() {
        Holding holding = holding("INFY", AssetType.EQUITY, null);
        when(holdingRepository.findAllActive()).thenReturn(List.of(holding));
        when(symbolRepository.findById("INFY")).thenReturn(Optional.of(
                Symbol.builder().symbol("INFY").isin("INE009A01021").build()));

        service.backfillAll(FROM, TO);

        // Persisted, not merely used: broker imports arrive without ISINs, and the price sweep needs
        // one to price a fund and the chart needs one to backfill. Resolving it once fixes both.
        verify(holdingRepository).save(holding);
        verify(rateLimiter).acquire("upstox-historical");
    }

    @Test
    @DisplayName("of two rows for one symbol, the one that already knows its ISIN is used")
    void theRepresentativeRowIsTheOneWithAnIsin() {
        when(holdingRepository.findAllActive()).thenReturn(List.of(
                holding("INFY", AssetType.EQUITY, null),
                holding("INFY", AssetType.EQUITY, "INE009A01021")));

        service.backfillAll(FROM, TO);

        // Picking the first row seen would have sent this symbol through a symbols-table lookup that
        // another row could already answer -- and, when the table has no entry, dropped the symbol.
        verify(symbolRepository, never()).findById(anyString());
        verify(rateLimiter, times(1)).acquire("upstox-historical");
    }

    @Test
    @DisplayName("a symbol whose history already reaches the requested end is not re-fetched")
    void coveredHistoryCostsNothing() {
        when(holdingRepository.findAllActive())
                .thenReturn(List.of(holding("INFY", AssetType.EQUITY, "INE009A01021")));
        when(historyRepository.findDateRangePerSymbol())
                .thenReturn(List.<Object[]>of(new Object[]{"INFY", FROM, TO}));

        service.backfillAll(FROM, TO);

        verify(rateLimiter, never()).acquire(anyString());
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("cancellation stops the run instead of finishing the list")
    void cancellationIsHonoured() {
        when(holdingRepository.findAllActive()).thenReturn(List.of(
                holding("INFY", AssetType.EQUITY, "INE009A01021"),
                holding("TCS", AssetType.EQUITY, "INE467B01029")));

        service.backfillAll(FROM, TO, () -> false);

        verify(rateLimiter, never()).acquire(anyString());
    }

    private Holding holding(String symbol, AssetType assetType, String isin) {
        return Holding.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .symbol(symbol)
                .assetType(assetType)
                .isin(isin)
                .name(symbol)
                .quantity(new BigDecimal("10"))
                .averageBuyPrice(new BigDecimal("100.00"))
                .createdAt(Instant.now())
                .build();
    }
}
