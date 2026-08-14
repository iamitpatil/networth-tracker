package com.networth.service.market;

import com.networth.repository.SymbolRepository;
import com.networth.service.market.provider.DividendEvent;
import com.networth.service.market.provider.MarketDataResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fetching a symbol's corporate-action events once, and the two ways that goes wrong.
 *
 * <p>The first is a PostgreSQL rule rather than a policy choice: {@code ON CONFLICT DO UPDATE} refuses a
 * statement that would touch the same row twice, so two provider entries sharing the conflict key abort
 * the entire batch and take every other event for that symbol with them.
 *
 * <p>The second is the watermark. An empty answer and an unreachable provider must be told apart, or a
 * provider outage marks every symbol as done and leaves the store empty for a month — the same
 * silent-success shape that made dividends read zero in the first place.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SymbolEventServiceTest {

    @Mock MarketDataResolver marketDataResolver;
    @Mock SymbolRepository symbolRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @Mock PreparedStatement ps;

    private SymbolEventService service;

    private static final String SYMBOL = "HDFCBANK.NS";
    private static final LocalDate EX = LocalDate.of(2026, 6, 19);

    @BeforeEach
    void setUp() {
        service = new SymbolEventService(marketDataResolver, symbolRepository, jdbcTemplate);
        ReflectionTestUtils.setField(service, "maxAge", Duration.ofDays(30));
        ReflectionTestUtils.setField(service, "providerWait", Duration.ofSeconds(90));
        ReflectionTestUtils.setField(service, "scope", "all");
        // count(*) before, then after. The delta is how new rows are counted, because the PostgreSQL
        // driver may answer SUCCESS_NO_INFO (-2) per batch statement.
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), ArgumentMatchers.<Object>any()))
                .thenReturn(0L, 2L);
    }

    private DividendEvent event(String amount, LocalDate exDate, String type) {
        return DividendEvent.builder()
                .symbol(SYMBOL)
                .amountPerShare(new BigDecimal(amount))
                .exDate(exDate)
                .recordDate(exDate)
                .dividendType(type)
                .description(type + " Dividend - Rs " + amount + " Per Share")
                .source("NSE")
                .build();
    }

    /** The rows the batch would actually write, read back out of the captured setter. */
    private List<Object[]> capturedRows() throws SQLException {
        ArgumentCaptor<BatchPreparedStatementSetter> captor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(anyString(), captor.capture());
        BatchPreparedStatementSetter setter = captor.getValue();

        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < setter.getBatchSize(); i++) {
            Object[] row = new Object[10];
            org.mockito.Mockito.reset(ps);
            setter.setValues(ps, i);
            ArgumentCaptor<String> strings = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Integer> idx = ArgumentCaptor.forClass(Integer.class);
            verify(ps, org.mockito.Mockito.atLeast(0)).setString(idx.capture(), strings.capture());
            for (int k = 0; k < idx.getAllValues().size(); k++) {
                row[idx.getAllValues().get(k)] = strings.getAllValues().get(k);
            }
            rows.add(row);
        }
        return rows;
    }

    @Test
    @DisplayName("a final and a special dividend on the same date are two rows, not one")
    void sameDateDifferentSubtypeBothSurvive() throws SQLException {
        // HDFCBANK really does this, and ITC pays an interim and a final in the same year. Keying only on
        // (symbol, ex_date) would silently discard one of the pair and understate the year.
        when(marketDataResolver.getDividendsWaiting(eq(SYMBOL), any(Duration.class)))
                .thenReturn(List.of(event("22", EX, "Final"), event("5", EX, "Special")));

        service.syncSymbol(SYMBOL);

        List<Object[]> rows = capturedRows();
        assertThat(rows).hasSize(2);
        assertThat(List.of(rows.get(0)[3], rows.get(1)[3]))
                .as("subtype is column 3, and it is what keeps them distinct")
                .containsExactlyInAnyOrder("Final", "Special");
    }

    @Test
    @DisplayName("two entries with the same conflict key collapse to one row")
    void duplicateConflictKeysAreCollapsed() throws SQLException {
        // Not tidiness: PostgreSQL aborts an ON CONFLICT DO UPDATE statement that would affect a row
        // twice, so leaving both in would lose the whole batch for this symbol.
        when(marketDataResolver.getDividendsWaiting(eq(SYMBOL), any(Duration.class)))
                .thenReturn(List.of(event("22", EX, "Final"), event("23", EX, "Final")));

        service.syncSymbol(SYMBOL);

        assertThat(capturedRows()).as("one row per conflict key").hasSize(1);
    }

    @Test
    @DisplayName("Yahoo's untyped 'Dividend' becomes an empty subtype, not a third variant")
    void flatDividendTypeCollapsesToEmpty() throws SQLException {
        // Otherwise the same payout arriving from NSE as 'Final' and from Yahoo as 'Dividend' would be
        // stored twice for one date.
        when(marketDataResolver.getDividendsWaiting(eq(SYMBOL), any(Duration.class)))
                .thenReturn(List.of(event("22", EX, "Dividend")));

        service.syncSymbol(SYMBOL);

        assertThat(capturedRows().get(0)[3]).isEqualTo("");
    }

    @Test
    @DisplayName("an unreachable provider leaves the symbol unsynced, so the next run retries it")
    void unreachableProviderIsNotStamped() {
        // null means nobody answered. Stamping here is what would turn an NSE outage into a permanent
        // "this company pays no dividend".
        when(marketDataResolver.getDividendsWaiting(eq(SYMBOL), any(Duration.class))).thenReturn(null);

        assertThat(service.syncSymbol(SYMBOL)).isZero();

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        verify(jdbcTemplate, never()).update(anyString(), eq(SYMBOL));
    }

    @Test
    @DisplayName("a provider that reports no dividends marks the symbol done")
    void emptyAnswerIsStamped() {
        // Plenty of listed companies have never declared one. At scope=all, re-asking several hundred of
        // them on every run would cost hours at ten requests a minute.
        when(marketDataResolver.getDividendsWaiting(eq(SYMBOL), any(Duration.class))).thenReturn(List.of());

        assertThat(service.syncSymbol(SYMBOL)).isZero();

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        verify(jdbcTemplate).update(anyString(), eq(SYMBOL));
    }

    @Test
    @DisplayName("a stored symbol is stamped, so a re-run has nothing to fetch")
    void aStoredSymbolIsStamped() {
        when(marketDataResolver.getDividendsWaiting(eq(SYMBOL), any(Duration.class)))
                .thenReturn(List.of(event("22", EX, "Final")));

        service.syncSymbol(SYMBOL);

        // This stamp is the whole fetch-once mechanism: findAllSymbolsNeedingEventSync filters on it.
        verify(jdbcTemplate).update(anyString(), eq(SYMBOL));
    }

    @Test
    @DisplayName("an event with no usable amount or no date is dropped rather than written")
    void unusableEventsAreDropped() {
        when(marketDataResolver.getDividendsWaiting(eq(SYMBOL), any(Duration.class))).thenReturn(List.of(
                DividendEvent.builder().symbol(SYMBOL).exDate(EX)
                        .amountPerShare(BigDecimal.ZERO).source("NSE").build(),          // ex_date ok, no amount
                DividendEvent.builder().symbol(SYMBOL)
                        .amountPerShare(new BigDecimal("5")).source("NSE").build()));    // amount ok, no date

        service.syncSymbol(SYMBOL);

        // ex_date is NOT NULL and a zero payout is meaningless, so neither can be stored.
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("scope=held narrows the work list; scope=all is the default")
    void scopeSelectsTheWorkList() {
        when(symbolRepository.findAllSymbolsNeedingEventSync(any())).thenReturn(List.of());
        when(symbolRepository.findHeldSymbolsNeedingEventSync(any())).thenReturn(List.of());

        service.syncAll(() -> true, null);
        verify(symbolRepository).findAllSymbolsNeedingEventSync(any());

        ReflectionTestUtils.setField(service, "scope", "held");
        service.syncAll(() -> true, null);
        verify(symbolRepository).findHeldSymbolsNeedingEventSync(any());
    }

    @Test
    @DisplayName("a cancelled sync stops without touching the rest of the work list")
    void cancellationStopsTheRun() {
        // The full run is hours at scope=all, so cancelling has to take effect at the next symbol rather
        // than at the end.
        when(symbolRepository.findAllSymbolsNeedingEventSync(any()))
                .thenReturn(List.of("A.NS", "B.NS", "C.NS"));

        service.syncAll(() -> false, null);

        verify(marketDataResolver, never()).getDividendsWaiting(anyString(), any(Duration.class));
    }
}