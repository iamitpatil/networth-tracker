package com.networth.service.market;

import com.networth.model.entity.MarketPrice;
import com.networth.model.enums.AssetType;
import com.networth.service.market.provider.MarketDataResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * When {@link PriceService} calls a provider, and when it declines to.
 *
 * <p>Counting the provider calls is the whole test. Both bugs this replaces were invisible in the
 * returned number and visible only in the call count: the read path served a stored row of any age
 * without ever asking for a newer one, while the Holdings page and the scheduler asked for every
 * symbol on every pass whether or not anything could have changed. The staleness rule itself is
 * {@link PriceFreshnessPolicyTest}'s subject; here it is mocked, so the assertions are about which
 * branch runs rather than about the calendar.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PriceServiceFreshnessTest {

    @Mock MarketDataResolver resolver;
    @Mock GoldPriceFetcher goldPriceFetcher;
    @Mock PriceCache priceCache;
    @Mock PriceFreshnessPolicy freshnessPolicy;

    @InjectMocks PriceService priceService;

    private static final Instant CONFIRMED_AT = Instant.parse("2026-08-10T09:45:00Z");

    @Test
    @DisplayName("a Redis hit is served without touching the database or a provider")
    void aCacheHitShortCircuitsEverything() {
        when(priceCache.getCachedPrice("INFY", AssetType.EQUITY)).thenReturn(new BigDecimal("1500.00"));

        assertThat(priceService.getCurrentPrice("INFY", AssetType.EQUITY)).isEqualByComparingTo("1500.00");

        // Entries expire when the policy stops vouching for them, so a hit needs no age check of its
        // own -- see PriceCache#cachePrice. That is the only reason this shortcut is safe.
        verify(priceCache, never()).getLatestPriceRecord(anyString(), any());
        verifyNoInteractions(resolver, goldPriceFetcher);
    }

    @Test
    @DisplayName("a stored price still inside the policy window is served, not re-fetched")
    void aFreshRowIsServedFromStorage() {
        storedPrice("INFY", AssetType.EQUITY, "1500.00");
        when(freshnessPolicy.isStale(eq(AssetType.EQUITY), any())).thenReturn(false);

        assertThat(priceService.getCurrentPrice("INFY", AssetType.EQUITY)).isEqualByComparingTo("1500.00");

        verifyNoInteractions(resolver, goldPriceFetcher);
        // Re-cached so the next reader does not repeat this query for the same number.
        verify(priceCache).cachePrice("INFY", AssetType.EQUITY, new BigDecimal("1500.00"), CONFIRMED_AT);
        verify(priceCache, never()).savePrice(anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("a stored price the policy calls stale falls through to a provider and is stored")
    void aStaleRowIsRefetched() {
        storedPrice("INFY", AssetType.EQUITY, "1500.00");
        when(freshnessPolicy.isStale(eq(AssetType.EQUITY), any())).thenReturn(true);
        when(resolver.getPrice("INFY", AssetType.EQUITY)).thenReturn(new BigDecimal("1512.50"));
        when(resolver.getSourceName(any())).thenReturn("UPSTOX");

        assertThat(priceService.getCurrentPrice("INFY", AssetType.EQUITY)).isEqualByComparingTo("1512.50");

        // Falling through here is what stops a six-month-old row being reported as today's price.
        verify(resolver).getPrice("INFY", AssetType.EQUITY);
        verify(priceCache).savePrice("INFY", AssetType.EQUITY, new BigDecimal("1512.50"), "UPSTOX");
    }

    @Test
    @DisplayName("nothing stored means a fetch, and a symbol no provider knows returns null")
    void anUnknownSymbolReturnsNull() {
        when(priceCache.getLatestPriceRecord("WAT", AssetType.EQUITY)).thenReturn(Optional.empty());
        when(freshnessPolicy.isStale(eq(AssetType.EQUITY), eq(null))).thenReturn(true);
        when(resolver.getPrice("WAT", AssetType.EQUITY)).thenReturn(null);

        assertThat(priceService.getCurrentPrice("WAT", AssetType.EQUITY)).isNull();

        verify(priceCache, never()).savePrice(anyString(), any(), any(), anyString());
        verify(priceCache, never()).cachePrice(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("when no provider answers, a stale price is served but deliberately not cached")
    void aStalePriceSurvivesAProviderOutageWithoutBeingPinned() {
        storedPrice("INFY", AssetType.EQUITY, "1500.00");
        when(freshnessPolicy.isStale(eq(AssetType.EQUITY), any())).thenReturn(true);
        when(resolver.getPrice("INFY", AssetType.EQUITY)).thenReturn(null);

        assertThat(priceService.getCurrentPrice("INFY", AssetType.EQUITY))
                .as("a stale total beats no total")
                .isEqualByComparingTo("1500.00");

        // Caching it would suppress the next attempt and pin the old number in place for as long as
        // anyone kept looking at it, which is exactly how the original bug sustained itself.
        verify(priceCache, never()).cachePrice(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("an unpriceable type is served from its row and sends no requests")
    void anEpfBalanceIsNeverFetched() {
        // The policy calls these never stale, so no separate isPriceable check is needed on this path:
        // a portfolio of provident fund and fixed deposits makes no outbound calls at all.
        storedPrice("EPF-1234", AssetType.EPF, "812000.00");
        when(freshnessPolicy.isStale(eq(AssetType.EPF), any())).thenReturn(false);

        assertThat(priceService.getCurrentPrice("EPF-1234", AssetType.EPF)).isEqualByComparingTo("812000.00");

        verifyNoInteractions(resolver, goldPriceFetcher);
    }

    @Test
    @DisplayName("gold goes to the gold fetcher rather than the exchange")
    void goldUsesItsOwnSource() {
        when(priceCache.getLatestPriceRecord("GOLD-24K", AssetType.GOLD)).thenReturn(Optional.empty());
        when(freshnessPolicy.isStale(eq(AssetType.GOLD), any())).thenReturn(true);
        when(goldPriceFetcher.fetchGoldPricePerGram()).thenReturn(new BigDecimal("9850.00"));
        when(resolver.getSourceName(any())).thenReturn("UPSTOX");

        assertThat(priceService.getCurrentPrice("GOLD-24K", AssetType.GOLD)).isEqualByComparingTo("9850.00");

        verify(goldPriceFetcher).fetchGoldPricePerGram();
        verify(resolver, never()).getPrice(anyString(), any());
    }

    @Test
    @DisplayName("refreshPriceIfStale asks a provider only when the policy says so")
    void conditionalRefreshRespectsThePolicy() {
        storedPrice("INFY", AssetType.EQUITY, "1500.00");
        when(freshnessPolicy.isStale(eq(AssetType.EQUITY), any())).thenReturn(false);

        assertThat(priceService.refreshPriceIfStale("INFY", AssetType.EQUITY))
                .as("nothing fetched, so nothing was stored")
                .isFalse();
        verifyNoInteractions(resolver, goldPriceFetcher);

        when(freshnessPolicy.isStale(eq(AssetType.EQUITY), any())).thenReturn(true);
        when(resolver.getPrice("INFY", AssetType.EQUITY)).thenReturn(new BigDecimal("1512.50"));
        when(resolver.getSourceName(any())).thenReturn("UPSTOX");

        assertThat(priceService.refreshPriceIfStale("INFY", AssetType.EQUITY)).isTrue();
        verify(resolver, times(1)).getPrice("INFY", AssetType.EQUITY);
    }

    @Test
    @DisplayName("refreshPrice is unconditional, because the user asked for it in as many words")
    void anExplicitRefreshAlwaysFetches() {
        when(freshnessPolicy.isStale(any(), any())).thenReturn(false);
        when(resolver.getPrice("INFY", AssetType.EQUITY)).thenReturn(new BigDecimal("1512.50"));
        when(resolver.getSourceName(any())).thenReturn("UPSTOX");

        assertThat(priceService.refreshPrice("INFY", AssetType.EQUITY)).isTrue();

        verify(resolver).getPrice("INFY", AssetType.EQUITY);
        verify(priceCache).savePrice("INFY", AssetType.EQUITY, new BigDecimal("1512.50"), "UPSTOX");
    }

    @Test
    @DisplayName("a failed explicit refresh reports the failure instead of swallowing it")
    void anExplicitRefreshReportsAnOutage() {
        when(resolver.getPrice("INFY", AssetType.EQUITY)).thenReturn(null);

        // Returning a boolean is what lets the sweep log how many symbols it actually updated, rather
        // than reporting a count of attempts as though they had all succeeded.
        assertThat(priceService.refreshPrice("INFY", AssetType.EQUITY)).isFalse();
        verify(priceCache, never()).savePrice(anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("lastConfirmedAt reads the row's confirmation time, empty when nothing is stored")
    void lastConfirmedAtComesFromTheRow() {
        storedPrice("INFY", AssetType.EQUITY, "1500.00");
        when(priceCache.getLatestPriceRecord("WAT", AssetType.EQUITY)).thenReturn(Optional.empty());

        assertThat(priceService.lastConfirmedAt("INFY", AssetType.EQUITY)).contains(CONFIRMED_AT);
        assertThat(priceService.lastConfirmedAt("WAT", AssetType.EQUITY)).isEmpty();
    }

    private void storedPrice(String symbol, AssetType assetType, String price) {
        MarketPrice stored = MarketPrice.builder()
                .symbol(symbol)
                .assetType(assetType)
                .priceDate(LocalDate.of(2026, 8, 10))
                .price(new BigDecimal(price))
                .source("UPSTOX")
                .updatedAt(CONFIRMED_AT)
                .build();
        when(priceCache.getLatestPriceRecord(symbol, assetType)).thenReturn(Optional.of(stored));
    }
}
