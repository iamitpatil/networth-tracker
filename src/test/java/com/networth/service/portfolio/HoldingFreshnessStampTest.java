package com.networth.service.portfolio;

import com.networth.model.dto.HoldingResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the holdings list says about the age of the numbers on it.
 *
 * <p>The screen used to present a price from February and a price from this morning identically, and
 * {@code updatedAt} could not tell them apart: it moves when anybody edits a quantity. These assert
 * the two fields that fixed it -- when a provider last confirmed the figure, and whether this
 * application's own freshness policy thinks that is too long ago -- and that the whole list costs one
 * query to answer, since a stamp per row would put a query per row on every page load.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldingFreshnessStampTest {

    @Mock HoldingRepository holdingRepository;
    @Mock MarketPriceRepository marketPriceRepository;
    @Mock PriceService priceService;
    @Mock DematAccountRepository dematAccountRepository;
    @Mock SymbolRepository symbolRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock com.networth.repository.DividendRepository dividendRepository;

    private final PriceFreshnessPolicy freshnessPolicy =
            new PriceFreshnessPolicy(new MarketCalendar("Asia/Kolkata"));

    private final UUID userId = UUID.randomUUID();

    private HoldingService service() {
        return new HoldingService(holdingRepository, marketPriceRepository, priceService,
                dematAccountRepository, transactionRepository, dividendRepository, freshnessPolicy,
                new com.networth.service.SymbolValidator(symbolRepository));
    }

    @Test
    @DisplayName("a price confirmed moments ago is stamped and not flagged")
    void aFreshPriceCarriesItsAge() {
        Instant justNow = Instant.now().minus(2, ChronoUnit.MINUTES);
        when(holdingRepository.findByUserId(userId))
                .thenReturn(List.of(holding("INFY", AssetType.EQUITY, null)));
        when(marketPriceRepository.findLastConfirmedBySymbolIn(anyCollection()))
                .thenReturn(List.of(lastConfirmed("INFY", AssetType.EQUITY, justNow)));

        HoldingResponse response = service().getUserHoldings(userId.toString()).get(0);

        assertThat(response.getPriceAsOf()).isEqualTo(justNow);
        assertThat(response.getPriceStale()).isFalse();
    }

    @Test
    @DisplayName("a price from months ago is flagged however recently the row was touched")
    void anOldPriceIsFlagged() {
        Instant february = Instant.now().minus(180, ChronoUnit.DAYS);
        Holding holding = holding("INFY", AssetType.EQUITY, null);
        // Edited this morning: updatedAt is current, the price it displays is not.
        holding.setUpdatedAt(Instant.now());
        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(holding));
        when(marketPriceRepository.findLastConfirmedBySymbolIn(anyCollection()))
                .thenReturn(List.of(lastConfirmed("INFY", AssetType.EQUITY, february)));

        HoldingResponse response = service().getUserHoldings(userId.toString()).get(0);

        assertThat(response.getPriceStale()).isTrue();
        assertThat(response.getPriceAsOf()).isEqualTo(february);
        assertThat(response.getUpdatedAt()).isNotEqualTo(response.getPriceAsOf());
    }

    @Test
    @DisplayName("a fund is stamped from the ISIN it prices from, not from its symbol")
    void aFundIsStampedFromItsIsin() {
        Instant lastNight = Instant.now().minus(2, ChronoUnit.HOURS);
        when(holdingRepository.findByUserId(userId))
                .thenReturn(List.of(holding("AXISBLUECHIP", AssetType.MUTUAL_FUND, "INF846K01EW2")));
        when(marketPriceRepository.findLastConfirmedBySymbolIn(anyCollection()))
                .thenReturn(List.of(lastConfirmed("INF846K01EW2", AssetType.MUTUAL_FUND, lastNight)));

        HoldingResponse response = service().getUserHoldings(userId.toString()).get(0);

        // NAVs are stored under the ISIN, so asking for the symbol would come back empty and the row
        // would claim no provider had ever priced a fund that is priced nightly.
        ArgumentCaptor<Collection<String>> asked = ArgumentCaptor.forClass(Collection.class);
        verify(marketPriceRepository).findLastConfirmedBySymbolIn(asked.capture());
        assertThat(asked.getValue()).containsExactly("INF846K01EW2");
        assertThat(response.getPriceAsOf()).isEqualTo(lastNight);
    }

    @Test
    @DisplayName("a provident fund balance is neither fresh nor stale")
    void anUnpriceableTypeSaysNothing() {
        when(holdingRepository.findByUserId(userId))
                .thenReturn(List.of(holding("EPF-1234", AssetType.EPF, null)));
        when(marketPriceRepository.findLastConfirmedBySymbolIn(anyCollection())).thenReturn(List.of());

        HoldingResponse response = service().getUserHoldings(userId.toString()).get(0);

        // Null, not false: no provider quotes an EPF balance, so "up to date" is not a claim anyone can
        // make about it, and the screen shows nothing rather than a reassuring badge it cannot support.
        assertThat(response.getPriceStale()).isNull();
        assertThat(response.getPriceAsOf()).isNull();
    }

    @Test
    @DisplayName("a price no provider ever confirmed is stale")
    void anUnconfirmedPriceIsStale() {
        when(holdingRepository.findByUserId(userId))
                .thenReturn(List.of(holding("INFY", AssetType.EQUITY, null)));
        when(marketPriceRepository.findLastConfirmedBySymbolIn(anyCollection())).thenReturn(List.of());

        HoldingResponse response = service().getUserHoldings(userId.toString()).get(0);

        assertThat(response.getPriceAsOf()).isNull();
        assertThat(response.getPriceStale()).isTrue();
    }

    @Test
    @DisplayName("the whole list is one query, and holdings of one instrument share its stamp")
    void theWholeListCostsOneQuery() {
        Instant justNow = Instant.now().minus(1, ChronoUnit.MINUTES);
        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(
                holding("INFY", AssetType.EQUITY, null),
                // Same stock, second demat account: one stamp, and both rows must show it.
                holding("INFY", AssetType.EQUITY, null),
                holding("TCS", AssetType.EQUITY, null),
                holding("EPF-1234", AssetType.EPF, null)));
        when(marketPriceRepository.findLastConfirmedBySymbolIn(anyCollection()))
                .thenReturn(List.of(lastConfirmed("INFY", AssetType.EQUITY, justNow)));

        List<HoldingResponse> responses = service().getUserHoldings(userId.toString());

        verify(marketPriceRepository, times(1)).findLastConfirmedBySymbolIn(any());
        assertThat(responses).extracting(HoldingResponse::getSymbol, HoldingResponse::getPriceAsOf)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("INFY", justNow),
                        org.assertj.core.api.Assertions.tuple("INFY", justNow),
                        org.assertj.core.api.Assertions.tuple("TCS", null),
                        org.assertj.core.api.Assertions.tuple("EPF-1234", null));
    }

    private Holding holding(String symbol, AssetType assetType, String isin) {
        return Holding.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .symbol(symbol)
                .assetType(assetType)
                .isin(isin)
                .name(symbol)
                .quantity(new BigDecimal("10"))
                .averageBuyPrice(new BigDecimal("100.00"))
                .currentPrice(new BigDecimal("150.00"))
                .createdAt(Instant.now())
                .build();
    }

    private MarketPriceRepository.LastConfirmed lastConfirmed(String symbol, AssetType assetType, Instant at) {
        return new MarketPriceRepository.LastConfirmed() {
            @Override public String getSymbol() { return symbol; }
            @Override public AssetType getAssetType() { return assetType; }
            @Override public Instant getLastConfirmedAt() { return at; }
        };
    }
}
