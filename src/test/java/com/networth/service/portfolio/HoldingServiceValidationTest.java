package com.networth.service.portfolio;

import com.networth.model.dto.HoldingRequest;
import com.networth.model.dto.HoldingResponse;
import com.networth.model.entity.DematAccount;
import com.networth.model.entity.Holding;
import com.networth.model.entity.Symbol;
import com.networth.model.enums.AssetType;
import com.networth.repository.*;
import com.networth.service.SymbolValidator;
import com.networth.service.market.MarketCalendar;
import com.networth.service.market.PriceFreshnessPolicy;
import com.networth.service.market.PriceService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Creating a holding with a ticker nobody can price.
 *
 * <p>This is the endpoint that returned {@code 201 Created} for {@code FAKETICKER999}. The row it wrote
 * was permanently {@code priceStale: true} with {@code priceAsOf: null}, and the five-minute sweep
 * retried it during every market session at two provider attempts per failure — a typo turning into a
 * permanent background load and a position that could never grow.
 *
 * <p>The "saves nothing" assertion is the point of the first test. Rejecting after the insert would
 * leave exactly the row we are trying to prevent.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldingServiceValidationTest {

    @Mock HoldingRepository holdingRepository;
    @Mock MarketPriceRepository marketPriceRepository;
    @Mock PriceService priceService;
    @Mock DematAccountRepository dematAccountRepository;
    @Mock SymbolRepository symbolRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock com.networth.repository.DividendRepository dividendRepository;

    private HoldingService holdingService;

    private final UUID userId = UUID.randomUUID();
    private final UUID dematId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // The validator is real, over a mocked repository: what is under test is the decision it makes
        // and whether this service acts on it, not the lookup itself (SymbolValidatorTest covers that).
        holdingService = new HoldingService(holdingRepository, marketPriceRepository, priceService,
                dematAccountRepository, transactionRepository, dividendRepository,
                new PriceFreshnessPolicy(new MarketCalendar("Asia/Kolkata")),
                new SymbolValidator(symbolRepository));

        DematAccount demat = new DematAccount();
        demat.setId(dematId);
        demat.setUserId(userId);
        when(dematAccountRepository.findById(dematId)).thenReturn(Optional.of(demat));

        when(holdingRepository.save(any())).thenAnswer(inv -> {
            Holding h = inv.getArgument(0);
            if (h.getId() == null) h.setId(UUID.randomUUID());
            return h;
        });
    }

    private HoldingRequest request(String symbol, AssetType assetType) {
        return HoldingRequest.builder()
                .assetType(assetType)
                .symbol(symbol)
                .name(symbol)
                .quantity(new BigDecimal("10"))
                .averageBuyPrice(new BigDecimal("100"))
                .dematAccountId(dematId.toString())
                .build();
    }

    @Test
    @DisplayName("an unrecognised equity is refused, and nothing is written")
    void anUnknownSymbolIsRefusedAndNothingIsSaved() {
        when(symbolRepository.findById(anyString())).thenReturn(Optional.empty());
        when(symbolRepository.findByIsin(anyString())).thenReturn(List.of());
        when(symbolRepository.findBySchemeCode(anyString())).thenReturn(List.of());
        when(symbolRepository.countByCategory(anyString())).thenReturn(2_075L);

        assertThatThrownBy(() -> holdingService.createHolding(userId.toString(), request("FAKETICKER999", AssetType.EQUITY)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown EQUITY symbol 'FAKETICKER999'");

        verify(holdingRepository, never()).save(any());
        // No opening lot either: a transaction against a holding that does not exist is worse than none.
        verify(transactionRepository, never()).save(any());
        // And no provider call, which is the load the bad row used to create forever.
        verify(priceService, never()).refreshPriceIfStale(anyString(), any());
    }

    @Test
    @DisplayName("a recognised symbol is stored in its canonical form, with the ISIN the list knows")
    void aKnownSymbolIsStoredCanonically() {
        when(symbolRepository.findById("RELIANCE")).thenReturn(Optional.empty());
        when(symbolRepository.findById("RELIANCE.NS")).thenReturn(Optional.of(Symbol.builder()
                .symbol("RELIANCE.NS").name("Reliance Industries").category("EQUITY")
                .isin("INE002A01018").build()));

        HoldingResponse response =
                holdingService.createHolding(userId.toString(), request("RELIANCE", AssetType.EQUITY));

        assertThat(response).isNotNull();
        ArgumentCaptor<Holding> saved = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(saved.capture());
        // Stored suffixed, because that is how stock_price_history is keyed. Storing what the user typed
        // would produce a holding no price lookup can reach.
        assertThat(saved.getValue().getSymbol()).isEqualTo("RELIANCE.NS");
        assertThat(saved.getValue().getIsin()).isEqualTo("INE002A01018");
    }

    @Test
    @DisplayName("an NPS holding is accepted only for a listed scheme code")
    void npsIsRestrictedToListedSchemes() {
        when(symbolRepository.findById("SM001001")).thenReturn(Optional.of(Symbol.builder()
                .symbol("SM001001").name("SBI PENSION FUND SCHEME - CENTRAL GOVT")
                .category("NPS").schemeCode("SM001001").build()));
        when(symbolRepository.findById("SM999999")).thenReturn(Optional.empty());
        when(symbolRepository.findByIsin(anyString())).thenReturn(List.of());
        when(symbolRepository.findBySchemeCode("SM999999")).thenReturn(List.of());
        when(symbolRepository.countByCategory("NPS")).thenReturn(282L);

        HoldingRequest good = HoldingRequest.builder().assetType(AssetType.NPS).symbol("SM001001")
                .quantity(new BigDecimal("100")).averageBuyPrice(new BigDecimal("40")).build();
        HoldingRequest bad = HoldingRequest.builder().assetType(AssetType.NPS).symbol("SM999999")
                .quantity(new BigDecimal("100")).averageBuyPrice(new BigDecimal("40")).build();

        // NPS needs no demat account, so these exercise the symbol check alone.
        assertThat(holdingService.createHolding(userId.toString(), good)).isNotNull();
        assertThatThrownBy(() -> holdingService.createHolding(userId.toString(), bad))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown NPS symbol 'SM999999'");

        // Exactly one write: the listed scheme. And its symbol is the scheme code, which is what
        // getEffectiveSymbolForPricing hands to the NAV lookup and to stock_price_history.
        ArgumentCaptor<Holding> saved = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getSymbol()).isEqualTo("SM001001");
        assertThat(holdingService.getEffectiveSymbolForPricing(saved.getValue())).isEqualTo("SM001001");
    }

    @Test
    @DisplayName("an ungated type is still accepted with a symbol nobody lists")
    void goldIsUngated() {
        HoldingRequest request = HoldingRequest.builder().assetType(AssetType.GOLD).symbol("GOLD-22K")
                .quantity(new BigDecimal("5")).averageBuyPrice(new BigDecimal("7000")).build();

        assertThat(holdingService.createHolding(userId.toString(), request)).isNotNull();

        verify(holdingRepository).save(any());
        verifyNoInteractions(symbolRepository);
    }
}
