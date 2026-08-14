package com.networth.service;

import com.networth.model.dto.TransactionResponse;
import com.networth.model.entity.Dividend;
import com.networth.model.entity.Holding;
import com.networth.model.entity.SymbolEvent;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.DividendRepository;
import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolEventRepository;
import com.networth.service.market.provider.MarketDataResolver;
import com.networth.service.portfolio.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dividends are computed from the stored events, with no provider in the loop.
 *
 * <p>That absence is the fix. This class used to call {@code marketDataResolver.getDividends(symbol)}
 * once per holding, and the resolver skips a rate-limited provider rather than waiting. With
 * {@code nse} at 1/s and 10/min and {@code yahoo} at 5/s, a 24-holding loop drained both per-second
 * buckets in 165 ms: five HTTP attempts, all of them Yahoo 429s, and roughly eighteen holdings with no
 * request issued at all. The skip logs at debug, so the endpoint answered {@code newDividends: 0} and
 * looked like it had succeeded. Live, that produced six dividend rows for one symbol out of 24 held.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DividendCalculationEventsTest {

    @Mock HoldingRepository holdingRepository;
    @Mock DividendRepository dividendRepository;
    @Mock SymbolEventRepository symbolEventRepository;
    @Mock TransactionService transactionService;
    /** Present only so the test can assert it is never touched. */
    @Mock MarketDataResolver marketDataResolver;

    private DividendCalculationService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID holdingId = UUID.randomUUID();
    private static final String SYMBOL = "ITC.NS";

    @BeforeEach
    void setUp() {
        service = new DividendCalculationService(holdingRepository, dividendRepository,
                symbolEventRepository, transactionService);

        Holding holding = new Holding();
        holding.setId(holdingId);
        holding.setUserId(userId);
        holding.setAssetType(AssetType.EQUITY);
        holding.setSymbol(SYMBOL);
        holding.setQuantity(new BigDecimal("100"));
        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(holding));
        when(dividendRepository.findByHoldingId(holdingId)).thenReturn(List.of());
        when(transactionService.getUserTransactions(userId.toString())).thenReturn(List.of(buy("100", 400)));
    }

    private TransactionResponse buy(String qty, int daysAgo) {
        return TransactionResponse.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(holdingId.toString())
                .transactionType(TransactionType.BUY)
                .quantity(new BigDecimal(qty))
                .price(new BigDecimal("400"))
                .transactionDate(LocalDate.now().minusDays(daysAgo).atStartOfDay())
                .build();
    }

    private SymbolEvent dividend(String amount, LocalDate exDate, String subtype) {
        return SymbolEvent.builder()
                .symbol(SYMBOL)
                .eventType("DIVIDEND")
                .eventSubtype(subtype)
                .amountPerShare(new BigDecimal(amount))
                .exDate(exDate)
                .recordDate(exDate)
                .source("NSE")
                .build();
    }

    @Test
    @DisplayName("payouts are computed without any provider call")
    void noProviderIsCalled() {
        when(symbolEventRepository.findBySymbolsAndType(any(), anyString()))
                .thenReturn(List.of(dividend("7.85", LocalDate.now().minusDays(100), "Final")));

        Map<String, Object> result = service.calculateDividends(userId);

        assertThat(result.get("newDividends")).isEqualTo(1);
        // The assertion that matters: no rate limiter, nothing to skip, so every holding is computed on
        // every run regardless of how many there are.
        org.mockito.Mockito.verifyNoInteractions(marketDataResolver);

        ArgumentCaptor<Dividend> saved = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(saved.capture());
        // 100 shares held on the record date x Rs 7.85
        assertThat(saved.getValue().getDividendAmount()).isEqualByComparingTo("785.00");
    }

    @Test
    @DisplayName("one query covers the whole portfolio, not one per holding")
    void eventsAreLoadedInOneQuery() {
        when(symbolEventRepository.findBySymbolsAndType(any(), anyString())).thenReturn(List.of());

        service.calculateDividends(userId);

        // Per-holding fetching is the shape that caused the original bug, and it is just as wasteful
        // against the database as it was against NSE.
        verify(symbolEventRepository, org.mockito.Mockito.times(1)).findBySymbolsAndType(any(), anyString());
        verify(symbolEventRepository, never()).findBySymbolOrderByExDateDesc(anyString());
    }

    @Test
    @DisplayName("a symbol with no stored events says so instead of reporting a silent zero")
    void awaitingSyncIsReported() {
        when(symbolEventRepository.findBySymbolsAndType(any(), anyString())).thenReturn(List.of());

        Map<String, Object> result = service.calculateDividends(userId);

        // "0 new dividends" used to be indistinguishable from "you are up to date", which is exactly how
        // this went unnoticed. The caller can now tell the difference.
        assertThat(result.get("newDividends")).isEqualTo(0);
        assertThat(result.get("symbolsWithEvents")).isEqualTo(0);
        assertThat(result.get("symbolsAwaitingSync")).isEqualTo(1);
        assertThat((String) result.get("note")).contains("backfill");
    }

    @Test
    @DisplayName("a revised amount updates the existing row rather than adding a second one")
    void revisedAmountUpdatesInPlace() {
        LocalDate ex = LocalDate.now().minusDays(100);
        Dividend existing = Dividend.builder()
                .holdingId(holdingId).symbol(SYMBOL)
                .dividendAmount(new BigDecimal("700.00"))
                .recordDate(ex).exDate(ex).reinvested(false).build();
        when(dividendRepository.findByHoldingId(holdingId)).thenReturn(List.of(existing));
        when(symbolEventRepository.findBySymbolsAndType(any(), anyString()))
                .thenReturn(List.of(dividend("7.85", ex, "Final")));

        Map<String, Object> result = service.calculateDividends(userId);

        assertThat(result.get("updatedDividends")).isEqualTo(1);
        assertThat(result.get("newDividends")).isEqualTo(0);
        assertThat(existing.getDividendAmount()).isEqualByComparingTo("785.00");
    }

    @Test
    @DisplayName("a second run over unchanged data writes nothing")
    void reRunIsIdempotent() {
        LocalDate ex = LocalDate.now().minusDays(100);
        Dividend existing = Dividend.builder()
                .holdingId(holdingId).symbol(SYMBOL)
                .dividendAmount(new BigDecimal("785.00"))
                .recordDate(ex).exDate(ex).reinvested(false).build();
        when(dividendRepository.findByHoldingId(holdingId)).thenReturn(List.of(existing));
        when(symbolEventRepository.findBySymbolsAndType(any(), anyString()))
                .thenReturn(List.of(dividend("7.85", ex, "Final")));

        Map<String, Object> result = service.calculateDividends(userId);

        assertThat(result.get("newDividends")).isEqualTo(0);
        assertThat(result.get("updatedDividends")).isEqualTo(0);
        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("an event before the position existed pays nothing")
    void eventsBeforeThePositionAreSkipped() {
        // The holding was bought 400 days ago; this dividend predates it by years.
        when(symbolEventRepository.findBySymbolsAndType(any(), anyString()))
                .thenReturn(List.of(dividend("7.85", LocalDate.now().minusDays(1200), "Final")));

        Map<String, Object> result = service.calculateDividends(userId);

        assertThat(result.get("newDividends")).isEqualTo(0);
        verify(dividendRepository, never()).save(any());
    }

    @Test
    @DisplayName("an interim and a final in the same year both pay out")
    void bothPayoutsInAYearAreRecorded() {
        when(symbolEventRepository.findBySymbolsAndType(any(), anyString())).thenReturn(List.of(
                dividend("6.50", LocalDate.now().minusDays(190), "Interim"),
                dividend("7.85", LocalDate.now().minusDays(80), "Final")));

        Map<String, Object> result = service.calculateDividends(userId);

        // Two distinct dates, so two rows. ITC pays exactly like this.
        assertThat(result.get("newDividends")).isEqualTo(2);
        verify(dividendRepository, org.mockito.Mockito.times(2)).save(any());
    }
}