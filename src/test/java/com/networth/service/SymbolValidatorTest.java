package com.networth.service;

import com.networth.model.entity.Symbol;
import com.networth.model.enums.AssetType;
import com.networth.repository.SymbolRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What a ticker has to be before a holding can carry it.
 *
 * <p>Written against the behaviour that was verified live before this existed: {@code FAKETICKER999},
 * {@code RELIENCE} and {@code NOTAREALSTOCK} all returned {@code 201 Created}, and a CSV of two invented
 * tickers reported {@code imported: 2, failed: 0}. Each rejection case below is one of those.
 *
 * <p>The canonicalisation cases matter just as much as the rejections. A holding stored as
 * {@code RELIANCE} while price history is keyed {@code RELIANCE.NS} is priced by nothing — it looks
 * accepted and behaves like a typo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SymbolValidatorTest {

    @Mock SymbolRepository symbolRepository;

    private SymbolValidator validator() {
        return new SymbolValidator(symbolRepository);
    }

    /** A reference list that is populated, so a rejection reads as a typo rather than an outage. */
    private void listIsPopulated() {
        // An EQUITY validation accepts an ETF row as well, so the size quoted in the message is both
        // lists together: 2,075 NSE equities plus 342 ETFs.
        when(symbolRepository.countByCategory("EQUITY")).thenReturn(2_075L);
        when(symbolRepository.countByCategory("ETF")).thenReturn(342L);
        when(symbolRepository.findMaxUpdatedAtByCategory(anyString()))
                .thenReturn(Optional.of(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    private Symbol row(String symbol, String category, String isin, String schemeCode) {
        return Symbol.builder().symbol(symbol).name(symbol + " Name").category(category)
                .isin(isin).schemeCode(schemeCode).build();
    }

    private void noMatchesAnywhere() {
        when(symbolRepository.findById(anyString())).thenReturn(Optional.empty());
        when(symbolRepository.findByIsin(anyString())).thenReturn(List.of());
        when(symbolRepository.findBySchemeCode(anyString())).thenReturn(List.of());
        when(symbolRepository.searchByCategoryAndName(anyString(), anyString())).thenReturn(List.of());
    }

    @Test
    @DisplayName("a symbol that is already canonical resolves to itself, with its ISIN")
    void exactMatchResolves() {
        when(symbolRepository.findById("INFY.NS"))
                .thenReturn(Optional.of(row("INFY.NS", "EQUITY", "INE009A01021", null)));

        SymbolValidator.Resolution resolved = validator().requireKnown("INFY.NS", AssetType.EQUITY, null);

        assertThat(resolved.symbol()).isEqualTo("INFY.NS");
        assertThat(resolved.isin()).isEqualTo("INE009A01021");
    }

    @Test
    @DisplayName("RELIANCE resolves to RELIANCE.NS, so one company cannot become two holdings")
    void theNseSuffixIsAdded() {
        when(symbolRepository.findById("RELIANCE")).thenReturn(Optional.empty());
        when(symbolRepository.findById("RELIANCE.NS"))
                .thenReturn(Optional.of(row("RELIANCE.NS", "EQUITY", "INE002A01018", null)));

        assertThat(validator().requireKnown("RELIANCE", AssetType.EQUITY, null).symbol())
                .isEqualTo("RELIANCE.NS");
    }

    @Test
    @DisplayName("an ETF filed under Equity is accepted — that is a classification choice, not a typo")
    void anEtfSatisfiesEquity() {
        when(symbolRepository.findById("NIFTYBEES")).thenReturn(Optional.empty());
        when(symbolRepository.findById("NIFTYBEES.NS"))
                .thenReturn(Optional.of(row("NIFTYBEES.NS", "ETF", "INF204KB14I2", null)));

        assertThat(validator().requireKnown("NIFTYBEES", AssetType.EQUITY, null).symbol())
                .isEqualTo("NIFTYBEES.NS");
    }

    @Test
    @DisplayName("a fund's ISIN is both its key and its ISIN")
    void aFundResolvesByIsin() {
        when(symbolRepository.findById("INF209K01UN8"))
                .thenReturn(Optional.of(row("INF209K01UN8", "MUTUAL_FUND", null, "120503")));

        SymbolValidator.Resolution resolved =
                validator().requireKnown("INF209K01UN8", AssetType.MUTUAL_FUND, null);

        assertThat(resolved.symbol()).isEqualTo("INF209K01UN8");
        // For a fund the primary key *is* the ISIN, and the NAV lookup needs it back.
        assertThat(resolved.isin()).isEqualTo("INF209K01UN8");
    }

    @Test
    @DisplayName("an NPS scheme code resolves, and carries no ISIN")
    void anNpsSchemeResolves() {
        when(symbolRepository.findById("SM001001"))
                .thenReturn(Optional.of(row("SM001001", "NPS", null, "SM001001")));

        SymbolValidator.Resolution resolved = validator().requireKnown("SM001001", AssetType.NPS, null);

        assertThat(resolved.symbol()).isEqualTo("SM001001");
        assertThat(resolved.isin()).isNull();
    }

    @Test
    @DisplayName("an unlisted NPS scheme is refused — only schemes with a published NAV are allowed")
    void anUnlistedNpsSchemeIsRefused() {
        noMatchesAnywhere();
        when(symbolRepository.countByCategory("NPS")).thenReturn(282L);
        when(symbolRepository.findMaxUpdatedAtByCategory("NPS")).thenReturn(Optional.of(Instant.now()));

        assertThatThrownBy(() -> validator().requireKnown("SM999999", AssetType.NPS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown NPS symbol 'SM999999'")
                .hasMessageContaining("NPS scheme reference list");
    }

    @Test
    @DisplayName("a misspelt equity is refused, with the list size and a way forward")
    void garbageIsRefused() {
        noMatchesAnywhere();
        listIsPopulated();

        assertThatThrownBy(() -> validator().requireKnown("RELIENCE", AssetType.EQUITY, null))
                .isInstanceOf(IllegalArgumentException.class)   // GlobalExceptionHandler maps this to 400
                .hasMessageContaining("Unknown EQUITY symbol 'RELIENCE'")
                .hasMessageContaining("2,417 symbols")
                .hasMessageContaining("/api/v1/symbols/refresh");
    }

    @Test
    @DisplayName("an empty reference list says so, instead of blaming the spelling")
    void anEmptyListIsReportedAsAnEmptyList() {
        noMatchesAnywhere();
        when(symbolRepository.countByCategory(anyString())).thenReturn(0L);

        // A provider outage and a typo look identical from the outside. Reporting the first as the
        // second sends somebody hunting for a mistake in a ticker that is perfectly correct.
        assertThatThrownBy(() -> validator().requireKnown("INFY", AssetType.EQUITY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reference list is empty")
                .hasMessageContaining("startup load did not complete")
                .hasMessageNotContaining("Check the spelling");
    }

    @Test
    @DisplayName("an ungated type passes anything through, without touching the reference list")
    void ungatedTypesArePassedThrough() {
        SymbolValidator validator = validator();

        // Nothing publishes a list of these: gold is grams, an FD is a bank's own reference, real
        // estate is an address. BOND is here too — NSE's DEBT.csv omits many listed bonds, so gating
        // it would reject instruments that genuinely exist.
        assertThat(validator.requireKnown("ANYTHING", AssetType.GOLD, null).symbol()).isEqualTo("ANYTHING");
        assertThat(validator.requireKnown("HDFC-FD-2031", AssetType.FD, null).symbol()).isEqualTo("HDFC-FD-2031");
        assertThat(validator.requireKnown("SOME-SGB-2029", AssetType.BOND, null).symbol()).isEqualTo("SOME-SGB-2029");

        assertThat(validator.isGated(AssetType.GOLD)).isFalse();
        assertThat(validator.isGated(AssetType.EQUITY)).isTrue();
        verifyNoInteractions(symbolRepository);
    }

    @Test
    @DisplayName("a fund named rather than coded still resolves — our own sample CSV is written that way")
    void aFundResolvesByName() {
        noMatchesAnywhere();
        when(symbolRepository.searchByCategoryAndName("MUTUAL_FUND", "PARAGPARIKHFLEXICAP"))
                .thenReturn(List.of(
                        row("INF879O01019", "MUTUAL_FUND", null, "122639"),
                        Symbol.builder().symbol("INF879O01027").name("Parag Parikh Flexi Cap Fund - Direct Growth")
                                .category("MUTUAL_FUND").build()));

        // Prefers the direct-growth variant, which is what a holding without an explicit plan is.
        assertThat(validator().requireKnown("PARAGPARIKHFLEXICAP", AssetType.MUTUAL_FUND, null).symbol())
                .isEqualTo("INF879O01027");
    }
}
