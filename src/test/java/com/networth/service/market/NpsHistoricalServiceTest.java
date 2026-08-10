package com.networth.service.market;

import com.networth.model.entity.Holding;
import com.networth.model.entity.NpsAccount;
import com.networth.model.entity.StockPriceHistory;
import com.networth.model.entity.Symbol;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.NpsAccountRepository;
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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Day-wise NPS NAVs on their way into {@code stock_price_history}.
 *
 * <p>{@link JdbcTemplate} is mocked rather than run against H2 deliberately: what these tests are about
 * is the parsing, the date window and the row shape handed to the batch, and H2's tolerance of a
 * PostgreSQL {@code ON CONFLICT (symbol, price_date)} conflict <em>target</em> is not something worth
 * making the assertions depend on. The conflict clause itself is exercised live against PostgreSQL by
 * re-running the backfill and confirming the row count does not move.
 *
 * <p>The insert count comes from a {@code count(*)} delta rather than from {@code batchUpdate}'s return
 * value, because PostgreSQL's driver may answer {@code SUCCESS_NO_INFO} (-2) per row — so "a re-run
 * inserts nothing" is expressed here as a before-count equal to the after-count.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NpsHistoricalServiceTest {

    @Mock NpsNavService npsNavService;
    @Mock ProviderRateLimiter rateLimiter;
    @Mock StockPriceHistoryRepository historyRepository;
    @Mock SymbolRepository symbolRepository;
    @Mock HoldingRepository holdingRepository;
    @Mock NpsAccountRepository npsAccountRepository;
    @Mock JdbcTemplate jdbcTemplate;

    private NpsHistoricalService service;

    private static final String SCHEME = "SM001001";

    @BeforeEach
    void setUp() {
        service = new NpsHistoricalService(npsNavService, rateLimiter, historyRepository,
                symbolRepository, holdingRepository, npsAccountRepository, jdbcTemplate);
        when(rateLimiter.acquire(eq("npsnav"), any(Duration.class))).thenReturn(true);
    }

    /** One entry as npsnav.in returns it: {@code dd-MM-yyyy} and a JSON number. */
    private Map<String, Object> nav(String date, Object value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("date", date);
        entry.put("nav", value);
        return entry;
    }

    /** The {@code count(*)} that brackets the insert: {@code before}, then {@code after}. */
    private void counts(long before, long after) {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq(SCHEME)))
                .thenReturn(before, after);
    }

    private BatchPreparedStatementSetter capturedSetter() {
        ArgumentCaptor<BatchPreparedStatementSetter> captor =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbcTemplate).batchUpdate(contains("ON CONFLICT"), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("dd-MM-yyyy parses, and a row lands as (scheme code, date, nav, NPSNAV)")
    void datesAndNavsAreParsed() throws SQLException {
        when(npsNavService.getHistoricalNav(SCHEME)).thenReturn(List.of(
                nav("01-04-2008", 10.0),
                nav("07-08-2026", 51.2263)));
        counts(0, 2);

        int inserted = service.backfillScheme(SCHEME, LocalDate.of(2000, 1, 1), LocalDate.of(2026, 8, 9));

        assertThat(inserted).isEqualTo(2);

        BatchPreparedStatementSetter setter = capturedSetter();
        assertThat(setter.getBatchSize()).isEqualTo(2);

        PreparedStatement ps = mock(PreparedStatement.class);
        setter.setValues(ps, 0);
        verify(ps).setString(1, SCHEME);
        // The oldest NPS NAV there is. Parsed as 1 April 2008, not 4 January — the format is dd-MM-yyyy,
        // and an ISO reading would silently shift every date in the series.
        verify(ps).setDate(2, Date.valueOf(LocalDate.of(2008, 4, 1)));
        verify(ps).setBigDecimal(3, BigDecimal.valueOf(10.0));
        // Not the head of the equity chain: an NPS NAV recorded as coming from Upstox is a lie that
        // makes provider attribution useless.
        verify(ps).setString(4, "NPSNAV");

        setter.setValues(ps, 1);
        verify(ps).setDate(2, Date.valueOf(LocalDate.of(2026, 8, 7)));
        verify(ps).setBigDecimal(3, BigDecimal.valueOf(51.2263));
    }

    @Test
    @DisplayName("a re-run over the same window inserts nothing")
    void aRerunInsertsNothing() {
        when(npsNavService.getHistoricalNav(SCHEME)).thenReturn(List.of(nav("07-08-2026", 51.2263)));
        // The row is already there, so ON CONFLICT DO NOTHING skips it and the count does not move.
        // This is what makes an interrupted backfill resumable by simply being run again.
        counts(4_544, 4_544);

        assertThat(service.backfillScheme(SCHEME, LocalDate.of(2000, 1, 1), LocalDate.of(2026, 8, 9)))
                .isZero();

        verify(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("the window filters what is written, since the API returns the whole series")
    void datesOutsideTheWindowAreDropped() {
        when(npsNavService.getHistoricalNav(SCHEME)).thenReturn(List.of(
                nav("05-08-2026", 51.0),
                nav("06-08-2026", 51.1),
                nav("07-08-2026", 51.2)));
        counts(0, 2);

        // A daily top-up asks for the two days it is missing. One call returns all 4,544 either way,
        // so the window's job is to stop the database being handed 4,542 rows to reject.
        service.backfillScheme(SCHEME, LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 7));

        assertThat(capturedSetter().getBatchSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("a zero or negative NAV is a hole in the feed, not a price")
    void nonPositiveNavsAreDropped() {
        when(npsNavService.getHistoricalNav(SCHEME)).thenReturn(List.of(
                nav("05-08-2026", 0),
                nav("06-08-2026", -1.5),
                nav("07-08-2026", "51.2263"),      // a string NAV, which some entries are
                nav("bogus-date", 12.0)));
        counts(0, 1);

        service.backfillScheme(SCHEME, LocalDate.of(2000, 1, 1), LocalDate.of(2026, 8, 9));

        // Storing a zero would draw the fund collapsing to nothing for a day.
        assertThat(capturedSetter().getBatchSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("nothing is fetched or written when the rate-limit slot is refused")
    void aRefusedSlotSkipsTheScheme() {
        when(rateLimiter.acquire(eq("npsnav"), any(Duration.class))).thenReturn(false);

        assertThat(service.backfillScheme(SCHEME, LocalDate.of(2000, 1, 1), LocalDate.of(2026, 8, 9)))
                .isZero();

        // The next run fills the gap; hammering a free community API to fill it now does not.
        verifyNoInteractions(npsNavService);
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("an empty feed is reported, not written as an empty batch")
    void anEmptyFeedWritesNothing() {
        when(npsNavService.getHistoricalNav(SCHEME)).thenReturn(List.of());

        assertThat(service.backfillScheme(SCHEME, LocalDate.of(2000, 1, 1), LocalDate.of(2026, 8, 9)))
                .isZero();
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("the scheme list comes from the symbols table, not from a second network call")
    void theFullLoadReadsTheReferenceTable() {
        when(symbolRepository.findByCategory("NPS")).thenReturn(List.of(
                Symbol.builder().symbol("SM001001").name("SBI CENTRAL GOVT").category("NPS").build(),
                Symbol.builder().symbol("SM002002").name("LIC CENTRAL GOVT").category("NPS").build()));
        when(npsNavService.getHistoricalNav(anyString())).thenReturn(List.of());

        service.backfillAll(() -> true, null);

        // Reading the table keeps the codes identical to the ones ticker validation accepts, and means a
        // full backfill does not depend on /api/schemes answering as well as /api/historical.
        verify(npsNavService, never()).getSchemes();
        verify(npsNavService).getHistoricalNav("SM001001");
        verify(npsNavService).getHistoricalNav("SM002002");
    }

    @Test
    @DisplayName("an empty reference table falls back to the scheme API")
    void anEmptyReferenceTableFallsBackToTheApi() {
        when(symbolRepository.findByCategory("NPS")).thenReturn(List.of());
        when(npsNavService.getSchemes()).thenReturn(List.of(Map.of("schemeCode", "SM001001", "name", "SBI")));
        when(npsNavService.getHistoricalNav(anyString())).thenReturn(List.of());

        service.backfillAll(() -> true, null);

        verify(npsNavService).getHistoricalNav("SM001001");
    }

    @Test
    @DisplayName("cancelling stops between schemes, keeping what is already written")
    void cancellationIsCheckedPerScheme() {
        when(symbolRepository.findByCategory("NPS")).thenReturn(List.of(
                Symbol.builder().symbol("SM001001").name("a").category("NPS").build(),
                Symbol.builder().symbol("SM002002").name("b").category("NPS").build()));

        service.backfillAll(() -> false, null);

        verifyNoInteractions(npsNavService);
    }

    @Test
    @DisplayName("a top-up covers both places NPS is recorded, and starts from the last day held")
    void heldSchemesComeFromHoldingsAndAccounts() {
        Holding holding = new Holding();
        holding.setId(UUID.randomUUID());
        holding.setAssetType(AssetType.NPS);
        holding.setSymbol("SM001001");
        when(holdingRepository.findAllActive()).thenReturn(List.of(holding));

        NpsAccount account = new NpsAccount();
        account.setSchemeCode("SM002002");
        when(npsAccountRepository.findAll()).thenReturn(List.of(account));

        LocalDate today = LocalDate.now(MarketCalendar.ZONE);
        // One scheme is two days behind; the other is current and must not be fetched at all.
        when(historyRepository.findLatest("SM001001")).thenReturn(Optional.of(
                StockPriceHistory.builder().symbol("SM001001").priceDate(today.minusDays(2))
                        .close(new BigDecimal("51.0")).build()));
        when(historyRepository.findLatest("SM002002")).thenReturn(Optional.of(
                StockPriceHistory.builder().symbol("SM002002").priceDate(today)
                        .close(new BigDecimal("42.0")).build()));
        when(npsNavService.getHistoricalNav(anyString())).thenReturn(List.of());

        service.backfillHeldSchemes();

        // A Holding of type NPS and an NpsAccount with a scheme code are the two ways a pension fund
        // arrives, and both are priced from the same NAV series.
        verify(npsNavService).getHistoricalNav("SM001001");
        verify(npsNavService, never()).getHistoricalNav("SM002002");
    }

    @Test
    @DisplayName("a held scheme with no history at all takes the whole series")
    void aSchemeWithNoHistoryTakesEverything() {
        Holding holding = new Holding();
        holding.setId(UUID.randomUUID());
        holding.setAssetType(AssetType.NPS);
        holding.setSymbol(SCHEME);
        when(holdingRepository.findAllActive()).thenReturn(List.of(holding));
        when(npsAccountRepository.findAll()).thenReturn(List.of());
        when(historyRepository.findLatest(SCHEME)).thenReturn(Optional.empty());
        when(npsNavService.getHistoricalNav(SCHEME)).thenReturn(List.of(
                nav("01-04-2008", 10.0),
                nav("07-08-2026", 51.2263)));
        counts(0, 2);

        service.backfillHeldSchemes();

        // No 365-day cap: one call returns the lot anyway, so capping would buy nothing and would leave
        // the chart short for a fund held longer than a year — which every pension fund is.
        assertThat(capturedSetter().getBatchSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("one dead scheme does not cost the other 281")
    void oneFailingSchemeDoesNotStopTheRest() {
        when(symbolRepository.findByCategory("NPS")).thenReturn(List.of(
                Symbol.builder().symbol("SM001001").name("a").category("NPS").build(),
                Symbol.builder().symbol("SM002002").name("b").category("NPS").build()));
        when(npsNavService.getHistoricalNav("SM001001")).thenThrow(new RuntimeException("502 from npsnav"));
        when(npsNavService.getHistoricalNav("SM002002")).thenReturn(List.of());

        assertThat(service.backfillAll(() -> true, null)).isZero();

        verify(npsNavService).getHistoricalNav("SM002002");
    }
}
