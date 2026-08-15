package com.networth.service.portfolio;

import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.repository.DematAccountRepository;
import com.networth.repository.HoldingRepository;
import com.networth.repository.MarketPriceRepository;
import com.networth.repository.SymbolRepository;
import com.networth.repository.TransactionRepository;
import com.networth.service.market.MarketCalendar;
import com.networth.service.market.PriceFreshnessPolicy;
import com.networth.service.market.PriceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the scheduled price sweep asks providers for, and what it writes back.
 *
 * <p>Both halves were wrong before, in ways that cancelled out into something that looked like it
 * worked. It fetched too much -- every equity holding, every 15 minutes, closed market or not, once
 * per holding rather than once per instrument -- and then wrote nothing, so the Holdings page had to
 * fetch everything again on load to show a current number. The assertions here are counts of
 * outbound calls and counts of saved holdings; a returned price proves neither.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldingPriceSweepTest {

    @Mock HoldingRepository holdingRepository;
    @Mock MarketPriceRepository marketPriceRepository;
    @Mock PriceService priceService;
    @Mock DematAccountRepository dematAccountRepository;
    @Mock SymbolRepository symbolRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock com.networth.repository.DividendRepository dividendRepository;

    /** The real policy: which types are priceable at all is exactly what the sweep must respect. */
    private final PriceFreshnessPolicy freshnessPolicy =
            new PriceFreshnessPolicy(new MarketCalendar("Asia/Kolkata"));

    private HoldingService service() {
        return new HoldingService(holdingRepository, marketPriceRepository, priceService,
                dematAccountRepository, transactionRepository, dividendRepository, freshnessPolicy,
                new com.networth.service.SymbolValidator(symbolRepository));
    }

    @Test
    @DisplayName("an instrument held five times over is fetched once")
    void oneInstrumentIsOneRequest() {
        when(holdingRepository.findDistinctHeldSymbols()).thenReturn(List.of(
                heldSymbol("INFY", null, AssetType.EQUITY),
                heldSymbol("TCS", null, AssetType.EQUITY)));
        when(holdingRepository.findAllActive()).thenReturn(List.of());

        service().refreshStalePrices();

        verify(priceService, times(1)).refreshPriceIfStale("INFY", AssetType.EQUITY);
        verify(priceService, times(1)).refreshPriceIfStale("TCS", AssetType.EQUITY);
    }

    @Test
    @DisplayName("every priceable type is swept, and the unpriceable ones are left alone")
    void coverageIsByCadenceNotByHardcodedType() {
        when(holdingRepository.findDistinctHeldSymbols()).thenReturn(List.of(
                heldSymbol("INFY", null, AssetType.EQUITY),
                heldSymbol("NIFTYBEES", null, AssetType.ETF),
                heldSymbol("BTC", null, AssetType.CRYPTO),
                heldSymbol("GOLD-24K", null, AssetType.GOLD),
                heldSymbol("SGBAUG28", null, AssetType.SGB),
                heldSymbol("AXISBLUECHIP", "INF846K01EW2", AssetType.MUTUAL_FUND),
                heldSymbol("EPF-1234", null, AssetType.EPF),
                heldSymbol("Flat 402", null, AssetType.REAL_ESTATE)));
        when(holdingRepository.findAllActive()).thenReturn(List.of());

        service().refreshStalePrices();

        // The two jobs this replaced covered EQUITY and MUTUAL_FUND. ETFs, crypto, gold and sovereign
        // gold bonds were on no schedule at all.
        verify(priceService).refreshPriceIfStale("NIFTYBEES", AssetType.ETF);
        verify(priceService).refreshPriceIfStale("BTC", AssetType.CRYPTO);
        verify(priceService).refreshPriceIfStale("GOLD-24K", AssetType.GOLD);
        verify(priceService).refreshPriceIfStale("SGBAUG28", AssetType.SGB);
        // Mutual funds price by ISIN, which is why the projection carries one.
        verify(priceService).refreshPriceIfStale("INF846K01EW2", AssetType.MUTUAL_FUND);

        verify(priceService, never()).refreshPriceIfStale(eq("EPF-1234"), any());
        verify(priceService, never()).refreshPriceIfStale(eq("Flat 402"), any());
    }

    @Test
    @DisplayName("a fund with no ISIN falls back to its symbol rather than being skipped")
    void aFundMissingItsIsinIsStillAttempted() {
        when(holdingRepository.findDistinctHeldSymbols())
                .thenReturn(List.of(heldSymbol("AXISBLUECHIP", "  ", AssetType.MUTUAL_FUND)));
        when(holdingRepository.findAllActive()).thenReturn(List.of());

        service().refreshStalePrices();

        // Likely to find nothing, but the reprice pass that follows resolves and persists the ISIN, so
        // the next sweep succeeds. Skipping outright would leave it stuck.
        verify(priceService).refreshPriceIfStale("AXISBLUECHIP", AssetType.MUTUAL_FUND);
    }

    @Test
    @DisplayName("one unreachable symbol does not end the sweep")
    void aFailingProviderDoesNotStopTheRest() {
        when(holdingRepository.findDistinctHeldSymbols()).thenReturn(List.of(
                heldSymbol("BROKEN", null, AssetType.EQUITY),
                heldSymbol("INFY", null, AssetType.EQUITY)));
        when(holdingRepository.findAllActive()).thenReturn(List.of());
        when(priceService.refreshPriceIfStale("BROKEN", AssetType.EQUITY))
                .thenThrow(new RuntimeException("provider timeout"));
        when(priceService.refreshPriceIfStale("INFY", AssetType.EQUITY)).thenReturn(true);

        assertThat(service().refreshStalePrices())
                .as("the count reports what actually got a new price")
                .isEqualTo(1);
        verify(priceService).refreshPriceIfStale("INFY", AssetType.EQUITY);
    }

    @Test
    @DisplayName("the sweep writes the price it fetched onto the holding")
    void fetchedPricesReachTheHoldings() {
        // The missing half of the old equity job: it fetched prices and stored them in market_prices,
        // then left every holding showing whatever it had shown before.
        Holding holding = holding("INFY", AssetType.EQUITY, "1500.00");
        when(holdingRepository.findDistinctHeldSymbols())
                .thenReturn(List.of(heldSymbol("INFY", null, AssetType.EQUITY)));
        when(holdingRepository.findAllActive()).thenReturn(List.of(holding));
        when(priceService.refreshPriceIfStale("INFY", AssetType.EQUITY)).thenReturn(true);
        when(priceService.getCurrentPrice("INFY", AssetType.EQUITY)).thenReturn(new BigDecimal("1512.50"));

        service().refreshStalePrices();

        assertThat(holding.getCurrentPrice()).isEqualByComparingTo("1512.50");
        assertThat(holding.getCurrentValue()).isEqualByComparingTo("15125.00");
        assertThat(holding.getUnrealizedPnl()).isEqualByComparingTo("5125.00");
        verify(holdingRepository).save(holding);
    }

    @Test
    @DisplayName("a price that has not moved is neither written nor charged a day-change call")
    void anUnchangedPriceCostsNothing() {
        Holding holding = holding("INFY", AssetType.EQUITY, "1500.00");
        holding.setCurrentValue(new BigDecimal("15000.00"));
        when(holdingRepository.findDistinctHeldSymbols())
                .thenReturn(List.of(heldSymbol("INFY", null, AssetType.EQUITY)));
        when(holdingRepository.findAllActive()).thenReturn(List.of(holding));
        when(priceService.getCurrentPrice("INFY", AssetType.EQUITY)).thenReturn(new BigDecimal("1500.00"));

        service().refreshStalePrices();

        verify(holdingRepository, never()).save(any(Holding.class));
        // getPreviousClose is itself a provider call, and it used to be made for every equity holding
        // on every pass. If the price has not moved, neither has the day's change.
        verify(priceService, never()).getPreviousClose(anyString(), any());
    }

    @Test
    @DisplayName("a holding whose value was left at zero is repaired even at an unchanged price")
    void aZeroValueIsStillRecomputed() {
        Holding holding = holding("INFY", AssetType.EQUITY, "1500.00");
        holding.setCurrentValue(BigDecimal.ZERO);
        when(holdingRepository.findDistinctHeldSymbols()).thenReturn(List.of());
        when(holdingRepository.findAllActive()).thenReturn(List.of(holding));
        when(priceService.getCurrentPrice("INFY", AssetType.EQUITY)).thenReturn(new BigDecimal("1500.00"));

        service().refreshStalePrices();

        assertThat(holding.getCurrentValue()).isEqualByComparingTo("15000.00");
        verify(holdingRepository).save(holding);
    }

    @Test
    @DisplayName("when no price can be had, the holding keeps the number it had")
    void aPricelessHoldingIsLeftAlone() {
        Holding holding = holding("WAT", AssetType.EQUITY, "1500.00");
        holding.setCurrentValue(new BigDecimal("15000.00"));
        when(holdingRepository.findDistinctHeldSymbols()).thenReturn(List.of());
        when(holdingRepository.findAllActive()).thenReturn(List.of(holding));
        when(priceService.getCurrentPrice("WAT", AssetType.EQUITY)).thenReturn(null);

        service().refreshStalePrices();

        assertThat(holding.getCurrentPrice()).isEqualByComparingTo("1500.00");
        verify(holdingRepository, never()).save(any(Holding.class));
    }

    @Test
    @DisplayName("the page-load refresh is freshness-gated and deduplicated too")
    void theUserFacingRefreshUsesTheSamePolicy() {
        UUID alice = UUID.randomUUID();
        Holding first = holding("INFY", AssetType.EQUITY, "1500.00");
        Holding second = holding("INFY", AssetType.EQUITY, "1500.00");
        Holding epf = holding("EPF-1234", AssetType.EPF, "812000.00");
        when(holdingRepository.findByUserId(alice)).thenReturn(List.of(first, second, epf));
        when(priceService.getCurrentPrice(anyString(), any())).thenReturn(null);

        service().updateAllHoldingPrices(alice.toString());

        // The Holdings page posts this on every load, so an unconditional fetch per holding meant a
        // burst of duplicate provider calls each visit.
        verify(priceService, times(1)).refreshPriceIfStale("INFY", AssetType.EQUITY);
        verify(priceService, never()).refreshPriceIfStale(eq("EPF-1234"), any());
        verify(priceService, never()).refreshPrice(anyString(), any());
    }

    private Holding holding(String symbol, AssetType assetType, String currentPrice) {
        return Holding.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .symbol(symbol)
                .assetType(assetType)
                .name(symbol)
                .quantity(new BigDecimal("10"))
                .averageBuyPrice(new BigDecimal("1000.00"))
                .currentPrice(new BigDecimal(currentPrice))
                .build();
    }

    private HoldingRepository.HeldSymbol heldSymbol(String symbol, String isin, AssetType assetType) {
        return new HoldingRepository.HeldSymbol() {
            @Override
            public String getSymbol() {
                return symbol;
            }

            @Override
            public String getIsin() {
                return isin;
            }

            @Override
            public AssetType getAssetType() {
                return assetType;
            }
        };
    }
}
