package com.networth.service.portfolio;

import com.networth.model.dto.CorporateActionRequest;
import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.CorporateActionType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolRepository;
import com.networth.repository.TransactionRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;

/**
 * Bonus, split and demerger handling.
 *
 * <p>The invariant these tests defend is that a corporate action never creates or destroys cost:
 * it only rearranges quantity and per-share cost. Getting that wrong does not fail loudly — it
 * quietly misstates the gain years later, when the shares are sold.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorporateActionServiceTest {

    @Mock HoldingRepository holdingRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock HoldingService holdingService;
    @Mock SymbolRepository symbolRepository;
    @Mock PriceService priceService;

    CorporateActionService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID holdingId = UUID.randomUUID();
    private final UUID dematId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // A real CostBasisService, not a mock: the position arithmetic is exactly what is under
        // test here, and stubbing it would assert only that a method was called.
        service = new CorporateActionService(holdingRepository, transactionRepository,
                holdingService, new CostBasisService(), symbolRepository, priceService);
    }

    private Holding holding(String quantity, String averagePrice) {
        Holding h = new Holding();
        h.setId(holdingId);
        h.setUserId(userId);
        h.setAssetType(AssetType.EQUITY);
        h.setSymbol("PARENTCO");
        h.setQuantity(new BigDecimal(quantity));
        h.setAverageBuyPrice(new BigDecimal(averagePrice));
        h.setDematAccountId(dematId);
        return h;
    }

    private Holding given(String quantity, String averagePrice) {
        Holding h = holding(quantity, averagePrice);
        when(holdingService.findOwnedHolding(userId.toString(), holdingId.toString())).thenReturn(h);
        // Assigns an ID on save, as JPA does for a new row. The demerger path writes a
        // transaction against the new holding, so an ID-less echo would fail on a null.
        when(holdingRepository.save(any(Holding.class))).thenAnswer(i -> {
            Holding saved = i.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(symbolRepository.findById(anyString())).thenReturn(java.util.Optional.empty());
        return h;
    }

    private Map<String, Object> apply(CorporateActionRequest request) {
        return service.apply(userId.toString(), holdingId.toString(), request);
    }

    private CorporateActionRequest.CorporateActionRequestBuilder base(CorporateActionType type) {
        return CorporateActionRequest.builder().type(type).actionDate(LocalDate.of(2024, 6, 1));
    }

    private List<Transaction> savedTransactions() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private BigDecimal totalCost(Holding h) {
        return h.getQuantity().multiply(h.getAverageBuyPrice());
    }

    // ── bonus ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a 1:1 bonus doubles the quantity and halves the average, leaving total cost alone")
    void bonusKeepsTotalCost() {
        Holding h = given("100", "200");                      // total cost 20,000

        apply(base(CorporateActionType.BONUS).sharesReceived(BigDecimal.ONE)
                .sharesHeld(BigDecimal.ONE).build());

        assertThat(h.getQuantity()).isEqualByComparingTo("200");
        assertThat(h.getAverageBuyPrice()).isEqualByComparingTo("100");
        assertThat(totalCost(h)).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("the bonus row is written at nil cost, not at the market price")
    void bonusRowHasZeroPrice() {
        given("100", "200");

        apply(base(CorporateActionType.BONUS).sharesReceived(BigDecimal.ONE)
                .sharesHeld(BigDecimal.ONE).build());

        Transaction row = savedTransactions().get(0);
        assertThat(row.getTransactionType()).isEqualTo(TransactionType.BONUS);
        assertThat(row.getQuantity()).isEqualByComparingTo("100");
        assertThat(row.getPrice()).isEqualByComparingTo("0");
        assertThat(row.getAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a 1:3 bonus drops the fraction rather than issuing a part share")
    void bonusTruncatesFractions() {
        Holding h = given("100", "300");

        apply(base(CorporateActionType.BONUS).sharesReceived(BigDecimal.ONE)
                .sharesHeld(new BigDecimal("3")).build());

        // 100/3 = 33.33; 33 shares are allotted, not 33.33.
        assertThat(h.getQuantity()).isEqualByComparingTo("133");
        assertThat(savedTransactions().get(0).getQuantity()).isEqualByComparingTo("33");
    }

    @Test
    @DisplayName("without a ratio, sharesReceived is the absolute allotment a statement reports")
    void bonusAcceptsAbsoluteAllotment() {
        Holding h = given("100", "200");

        apply(base(CorporateActionType.BONUS).sharesReceived(new BigDecimal("7")).build());

        assertThat(h.getQuantity()).isEqualByComparingTo("107");
        assertThat(savedTransactions().get(0).getQuantity()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("a bonus too small to yield one share is refused, with the arithmetic explained")
    void bonusBelowOneShareIsRefused() {
        given("10", "200");

        assertThatThrownBy(() -> apply(base(CorporateActionType.BONUS)
                .sharesReceived(BigDecimal.ONE).sharesHeld(new BigDecimal("100")).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("less than one share");
    }

    // ── split ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a 1:2 split doubles quantity, halves the average, and keeps total cost")
    void splitKeepsTotalCost() {
        Holding h = given("100", "1000");                     // total cost 100,000

        apply(base(CorporateActionType.SPLIT).fromQuantity(BigDecimal.ONE)
                .toQuantity(new BigDecimal("2")).build());

        assertThat(h.getQuantity()).isEqualByComparingTo("200");
        assertThat(h.getAverageBuyPrice()).isEqualByComparingTo("500");
        assertThat(totalCost(h)).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("a 2:1 consolidation halves quantity and doubles the average")
    void consolidationIsTheSameMathsInReverse() {
        Holding h = given("100", "500");

        apply(base(CorporateActionType.SPLIT).fromQuantity(new BigDecimal("2"))
                .toQuantity(BigDecimal.ONE).build());

        assertThat(h.getQuantity()).isEqualByComparingTo("50");
        assertThat(h.getAverageBuyPrice()).isEqualByComparingTo("1000");
        assertThat(totalCost(h)).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("the split row carries the cost factor and the change in share count")
    void splitRowCarriesFactorAndDelta() {
        given("100", "1000");

        apply(base(CorporateActionType.SPLIT).fromQuantity(BigDecimal.ONE)
                .toQuantity(new BigDecimal("2")).build());

        Transaction row = savedTransactions().get(0);
        assertThat(row.getTransactionType()).isEqualTo(TransactionType.SPLIT);
        assertThat(row.getAdjustmentFactor()).isEqualByComparingTo("0.5");
        assertThat(row.getQuantity()).isEqualByComparingTo("100");   // +100 shares
    }

    @Test
    @DisplayName("a split that changes nothing is refused rather than silently applied")
    void noOpSplitIsRefused() {
        given("100", "1000");

        assertThatThrownBy(() -> apply(base(CorporateActionType.SPLIT)
                .fromQuantity(BigDecimal.ONE).toQuantity(BigDecimal.ONE).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changes nothing");
    }

    // ── demerger ──────────────────────────────────────────────────────

    @Test
    @DisplayName("a demerger splits the cost between the two companies, creating none and losing none")
    void demergerApportionsCost() {
        Holding parent = given("100", "1000");               // total cost 100,000
        when(holdingRepository.findByUserIdAndSymbol(userId, "NEWCO")).thenReturn(List.of());

        Map<String, Object> result = apply(base(CorporateActionType.DEMERGER)
                .resultingSymbol("NEWCO")
                .resultingQuantity(new BigDecimal("100"))
                .costApportionmentPercent(new BigDecimal("20")).build());

        // Parent keeps 80%: same 100 shares at 800 each = 80,000.
        assertThat(parent.getQuantity()).isEqualByComparingTo("100");
        assertThat(parent.getAverageBuyPrice()).isEqualByComparingTo("800");
        // The other 20,000 lands on the new holding: 100 shares at 200.
        assertThat((BigDecimal) result.get("resultingCostPerShare")).isEqualByComparingTo("200");
        assertThat((BigDecimal) result.get("apportionedCost")).isEqualByComparingTo("20000");
    }

    @Test
    @DisplayName("the parent keeps every share it had: a demerger takes nothing away")
    void demergerLeavesParentQuantityAlone() {
        Holding parent = given("250", "400");
        when(holdingRepository.findByUserIdAndSymbol(userId, "NEWCO")).thenReturn(List.of());

        apply(base(CorporateActionType.DEMERGER)
                .resultingSymbol("NEWCO")
                .resultingQuantity(new BigDecimal("25"))
                .costApportionmentPercent(new BigDecimal("10")).build());

        assertThat(parent.getQuantity()).isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("the new shares inherit the earliest acquisition date, so they can be long-term at once")
    void demergedSharesInheritAcquisitionDate() {
        given("100", "1000");
        when(holdingRepository.findByUserIdAndSymbol(userId, "NEWCO")).thenReturn(List.of());
        // The parent was bought in 2019 and topped up in 2023; the earliest is what carries over.
        Transaction old = new Transaction();
        old.setTransactionType(TransactionType.BUY);
        old.setTransactionDate(LocalDateTime.parse("2019-04-01T10:00:00"));
        Transaction recent = new Transaction();
        recent.setTransactionType(TransactionType.BUY);
        recent.setTransactionDate(LocalDateTime.parse("2023-04-01T10:00:00"));
        when(transactionRepository.findByHoldingId(holdingId)).thenReturn(List.of(recent, old));

        Map<String, Object> result = apply(base(CorporateActionType.DEMERGER)
                .resultingSymbol("NEWCO")
                .resultingQuantity(new BigDecimal("100"))
                .costApportionmentPercent(new BigDecimal("20")).build());

        assertThat(result.get("inheritedAcquisitionDate")).isEqualTo(LocalDate.of(2019, 4, 1));
        Transaction in = savedTransactions().stream()
                .filter(t -> t.getTransactionType() == TransactionType.DEMERGER_IN)
                .findFirst().orElseThrow();
        assertThat(in.getAcquisitionDate()).isEqualTo(LocalDateTime.parse("2019-04-01T10:00:00"));
    }

    @Test
    @DisplayName("both legs of a demerger are written to the ledger")
    void demergerWritesBothLegs() {
        given("100", "1000");
        when(holdingRepository.findByUserIdAndSymbol(userId, "NEWCO")).thenReturn(List.of());

        apply(base(CorporateActionType.DEMERGER)
                .resultingSymbol("NEWCO")
                .resultingQuantity(new BigDecimal("100"))
                .costApportionmentPercent(new BigDecimal("20")).build());

        assertThat(savedTransactions()).extracting(Transaction::getTransactionType)
                .contains(TransactionType.DEMERGER_OUT, TransactionType.DEMERGER_IN);
    }

    @Test
    @DisplayName("an existing holding in the new symbol is topped up rather than duplicated")
    void demergerReusesAnExistingHolding() {
        given("100", "1000");
        Holding existing = new Holding();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setSymbol("NEWCO");
        existing.setAssetType(AssetType.EQUITY);
        existing.setQuantity(new BigDecimal("50"));
        existing.setAverageBuyPrice(new BigDecimal("100"));
        existing.setDematAccountId(dematId);
        when(holdingRepository.findByUserIdAndSymbol(userId, "NEWCO")).thenReturn(List.of(existing));

        Map<String, Object> result = apply(base(CorporateActionType.DEMERGER)
                .resultingSymbol("NEWCO")
                .resultingQuantity(new BigDecimal("100"))
                .costApportionmentPercent(new BigDecimal("20")).build());

        assertThat(result.get("resultingHoldingId")).isEqualTo(existing.getId().toString());
        // 50 at 100 (5,000) plus 100 at 200 (20,000) = 150 shares carrying 25,000.
        assertThat(existing.getQuantity()).isEqualByComparingTo("150");
        // To the rupee, not to the paisa: 25,000 over 150 shares is 166.6667 recurring, and the
        // average is stored at 4 decimal places, so quantity x average cannot reproduce the
        // total exactly. Tax uses the exact per-lot costs in CapitalGainsCalculator; this
        // average is the position summary.
        assertThat(totalCost(existing)).isCloseTo(new BigDecimal("25000"), within(new BigDecimal("1")));
    }

    @Test
    @DisplayName("a cost apportionment outside 0-100 percent is refused")
    void impossibleApportionmentIsRefused() {
        given("100", "1000");

        assertThatThrownBy(() -> apply(base(CorporateActionType.DEMERGER)
                .resultingSymbol("NEWCO").resultingQuantity(BigDecimal.TEN)
                .costApportionmentPercent(new BigDecimal("120")).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 100");
    }

    @Test
    @DisplayName("demerging into the same symbol is refused")
    void demergerIntoItselfIsRefused() {
        given("100", "1000");

        assertThatThrownBy(() -> apply(base(CorporateActionType.DEMERGER)
                .resultingSymbol("parentco").resultingQuantity(BigDecimal.TEN)
                .costApportionmentPercent(new BigDecimal("20")).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ");
    }

    // ── guards ────────────────────────────────────────────────────────

    @Test
    @DisplayName("an action on a holding with no shares is refused: there is nothing to act on")
    void emptyHoldingIsRefused() {
        given("0", "1000");

        assertThatThrownBy(() -> apply(base(CorporateActionType.BONUS)
                .sharesReceived(BigDecimal.ONE).sharesHeld(BigDecimal.ONE).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hold no");
    }

    @Test
    @DisplayName("an action dated in the future is refused")
    void futureActionIsRefused() {
        given("100", "1000");

        assertThatThrownBy(() -> service.apply(userId.toString(), holdingId.toString(),
                CorporateActionRequest.builder().type(CorporateActionType.BONUS)
                        .actionDate(LocalDate.now().plusDays(1))
                        .sharesReceived(BigDecimal.ONE).sharesHeld(BigDecimal.ONE).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("an asset that cannot split or spin off is refused")
    void unsupportedAssetTypeIsRefused() {
        Holding h = holding("100", "1000");
        h.setAssetType(AssetType.FD);
        when(holdingService.findOwnedHolding(userId.toString(), holdingId.toString())).thenReturn(h);

        assertThatThrownBy(() -> apply(base(CorporateActionType.BONUS)
                .sharesReceived(BigDecimal.ONE).sharesHeld(BigDecimal.ONE).build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shares, ETFs and mutual funds");
    }
}
