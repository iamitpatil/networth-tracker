package com.networth.service.portfolio;

import com.networth.model.entity.Holding;
import com.networth.model.entity.SymbolEvent;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolEventRepository;
import com.networth.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Matching a zero-rupee "purchase" to the corporate action it actually was.
 *
 * <p>Twelve such rows existed across seven symbols. Five were plain bonuses, two were demergers whose event
 * is published under the parent company, and one — Bajaj Finance — was a bonus and a split in the same week
 * recorded as a single row. The last of those is the reason this reports rather than simply rewrites: a row
 * that cannot be attributed to one event must be left alone and shown to somebody.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorporateActionReclassifierTest {

    @Mock HoldingRepository holdingRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock SymbolEventRepository symbolEventRepository;

    private CorporateActionReclassifier service;

    private final UUID userId = UUID.randomUUID();
    private final UUID holdingId = UUID.randomUUID();

    /** RELIANCE went ex on the 28th; the shares were credited on the 30th. */
    private static final LocalDate EX = LocalDate.of(2024, 10, 28);
    private static final LocalDate CREDITED = LocalDate.of(2024, 10, 30);

    @BeforeEach
    void setUp() {
        service = new CorporateActionReclassifier(holdingRepository, transactionRepository,
                symbolEventRepository);
        ReflectionTestUtils.setField(service, "demergerParents",
                "ITCHOTELS.NS:ITC.NS:0.1,KWIL.NS:HINDUNILVR.NS:1.0");
    }

    private Holding holding(String symbol, UUID id) {
        Holding h = new Holding();
        h.setId(id);
        h.setUserId(userId);
        h.setSymbol(symbol);
        h.setAssetType(AssetType.EQUITY);
        return h;
    }

    private Transaction zeroPriceBuy(String qty, LocalDate date) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .holdingId(holdingId)
                .userId(userId)
                .transactionType(TransactionType.BUY)
                .quantity(new BigDecimal(qty))
                .price(BigDecimal.ZERO)
                .amount(BigDecimal.ZERO)
                .transactionDate(date.atStartOfDay())
                .build();
    }

    private SymbolEvent event(String type, String ratio, LocalDate exDate) {
        return SymbolEvent.builder()
                .symbol("RELIANCE.NS").eventType(type).eventSubtype("")
                .ratio(ratio == null ? null : new BigDecimal(ratio))
                .exDate(exDate).description(type + " " + ratio).source("NSE").build();
    }

    private void portfolio(String symbol, Transaction... txns) {
        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(holding(symbol, holdingId)));
        when(transactionRepository.findByUserId(userId)).thenReturn(List.of(txns));
    }

    @SuppressWarnings("unchecked")
    private List<CorporateActionReclassifier.Proposal> proposalsOf(Map<String, Object> report) {
        return (List<CorporateActionReclassifier.Proposal>) report.get("proposals");
    }

    @Test
    @DisplayName("a bonus credited two days after the ex-date is matched")
    void bonusMatchedAcrossTheSettlementGap() {
        portfolio("RELIANCE.NS", zeroPriceBuy("16", CREDITED));
        when(symbolEventRepository.findBySymbolOrderByExDateDesc("RELIANCE.NS"))
                .thenReturn(List.of(event("BONUS", "2.0", EX)));

        var p = proposalsOf(service.propose(userId)).get(0);

        // Credit lands after the ex-date, so an exact-date match would find nothing at all.
        assertThat(p.proposedType()).isEqualTo("BONUS");
        assertThat(p.ratio()).isEqualByComparingTo("2.0");
        assertThat(p.actionable()).isTrue();
        // A bonus adds nil-cost shares and leaves earlier lots alone, so there is nothing to scale.
        assertThat(p.adjustmentFactor()).isNull();
    }

    @Test
    @DisplayName("a split proposes the inverse of its multiplier as the cost adjustment")
    void splitCarriesTheCostAdjustment() {
        portfolio("RELIANCE.NS", zeroPriceBuy("16", CREDITED));
        when(symbolEventRepository.findBySymbolOrderByExDateDesc("RELIANCE.NS"))
                .thenReturn(List.of(event("SPLIT", "2.0", EX)));

        var p = proposalsOf(service.propose(userId)).get(0);

        assertThat(p.proposedType()).isEqualTo("SPLIT");
        // Twice the shares at half the per-share cost. Getting this the wrong way up would double the
        // recorded cost of every earlier lot.
        assertThat(p.adjustmentFactor()).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("a row matching two events is flagged instead of retyped")
    void compoundActionsAreFlagged() {
        // Bajaj Finance: 8 shares became 80 in one week through a 4:1 bonus and a 1:2 split, recorded as a
        // single +72 row. No one type is right for it.
        portfolio("RELIANCE.NS", zeroPriceBuy("72", CREDITED));
        when(symbolEventRepository.findBySymbolOrderByExDateDesc("RELIANCE.NS"))
                .thenReturn(List.of(event("BONUS", "5.0", EX), event("SPLIT", "2.0", EX.plusDays(1))));

        var p = proposalsOf(service.propose(userId)).get(0);

        assertThat(p.actionable()).isFalse();
        assertThat(p.proposedType()).isNull();
        assertThat(p.note()).contains("2 events");
        assertThat(p.matchedEvent()).contains("BONUS").contains("SPLIT");
    }

    @Test
    @DisplayName("a demerger inherits its holding period from the parent's oldest lot")
    void demergerInheritsTheAcquisitionDate() {
        UUID parentHoldingId = UUID.randomUUID();
        when(holdingRepository.findByUserId(userId))
                .thenReturn(List.of(holding("ITCHOTELS.NS", holdingId)));
        when(transactionRepository.findByUserId(userId))
                .thenReturn(List.of(zeroPriceBuy("40", LocalDate.of(2025, 2, 7))));
        when(holdingRepository.findByUserIdAndSymbol(userId, "ITC.NS"))
                .thenReturn(List.of(holding("ITC.NS", parentHoldingId)));
        when(transactionRepository.findByHoldingId(parentHoldingId)).thenReturn(List.of(
                Transaction.builder().id(UUID.randomUUID()).holdingId(parentHoldingId).userId(userId)
                        .transactionType(TransactionType.BUY).quantity(new BigDecimal("200"))
                        .price(new BigDecimal("200")).amount(new BigDecimal("40000"))
                        .transactionDate(LocalDate.of(2020, 2, 18).atStartOfDay()).build(),
                Transaction.builder().id(UUID.randomUUID()).holdingId(parentHoldingId).userId(userId)
                        .transactionType(TransactionType.BUY).quantity(new BigDecimal("207"))
                        .price(new BigDecimal("300")).amount(new BigDecimal("62100"))
                        .transactionDate(LocalDate.of(2023, 5, 1).atStartOfDay()).build()));

        var p = proposalsOf(service.propose(userId)).get(0);

        assertThat(p.proposedType()).isEqualTo("DEMERGER_IN");
        assertThat(p.parentSymbol()).isEqualTo("ITC.NS");
        // The oldest parent lot, not the newest: s.2(42A) gives the resulting shares the period the
        // original shares were held, and taking 2023 instead would shorten it and cost tax.
        assertThat(p.inheritedAcquisitionDate()).isEqualTo(LocalDate.of(2020, 2, 18));
        assertThat(p.actionable()).isTrue();
        assertThat(p.note()).contains("s.2(42A)");
    }

    @Test
    @DisplayName("a demerger with no parent transaction is reported, not guessed")
    void demergerWithoutAParentIsNotActionable() {
        when(holdingRepository.findByUserId(userId))
                .thenReturn(List.of(holding("KWIL.NS", holdingId)));
        when(transactionRepository.findByUserId(userId))
                .thenReturn(List.of(zeroPriceBuy("51", LocalDate.of(2025, 12, 24))));
        when(holdingRepository.findByUserIdAndSymbol(userId, "HINDUNILVR.NS")).thenReturn(List.of());

        var p = proposalsOf(service.propose(userId)).get(0);

        // Without the parent's date the holding period cannot be inherited, and dating the shares at the
        // demerger would silently make a long-term gain short-term.
        assertThat(p.actionable()).isFalse();
        assertThat(p.note()).contains("HINDUNILVR.NS");
    }

    @Test
    @DisplayName("no stored event means no proposal, with a note saying why")
    void unmatchedRowsSayWhy() {
        portfolio("RELIANCE.NS", zeroPriceBuy("16", CREDITED));
        when(symbolEventRepository.findBySymbolOrderByExDateDesc("RELIANCE.NS")).thenReturn(List.of());

        var p = proposalsOf(service.propose(userId)).get(0);

        assertThat(p.actionable()).isFalse();
        assertThat(p.note()).contains("event sync");
    }

    @Test
    @DisplayName("an event months away is not treated as a match")
    void distantEventsAreNotMatched() {
        portfolio("RELIANCE.NS", zeroPriceBuy("16", CREDITED));
        when(symbolEventRepository.findBySymbolOrderByExDateDesc("RELIANCE.NS"))
                .thenReturn(List.of(event("BONUS", "2.0", EX.minusMonths(6))));

        assertThat(proposalsOf(service.propose(userId)).get(0).actionable()).isFalse();
    }

    @Test
    @DisplayName("a normal purchase is left entirely alone")
    void pricedPurchasesAreIgnored() {
        Transaction normal = Transaction.builder()
                .id(UUID.randomUUID()).holdingId(holdingId).userId(userId)
                .transactionType(TransactionType.BUY).quantity(new BigDecimal("10"))
                .price(new BigDecimal("1400")).amount(new BigDecimal("14000"))
                .transactionDate(CREDITED.atStartOfDay()).build();
        portfolio("RELIANCE.NS", normal);

        assertThat(service.propose(userId).get("zeroPriceRows")).isEqualTo(0);
    }

    @Test
    @DisplayName("apply writes the actionable rows and skips the rest, never touching quantity")
    void applyWritesOnlyWhatWasProposed() {
        Transaction bonusRow = zeroPriceBuy("16", CREDITED);
        portfolio("RELIANCE.NS", bonusRow);
        when(symbolEventRepository.findBySymbolOrderByExDateDesc("RELIANCE.NS"))
                .thenReturn(List.of(event("BONUS", "2.0", EX)));
        when(transactionRepository.findById(bonusRow.getId())).thenReturn(java.util.Optional.of(bonusRow));

        Map<String, Object> result = service.apply(userId);

        assertThat(result.get("applied")).isEqualTo(1);
        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getTransactionType()).isEqualTo(TransactionType.BONUS);
        // The share count was always right; only the type and the tax fields were wrong.
        assertThat(saved.getValue().getQuantity()).isEqualByComparingTo("16");
    }

    @Test
    @DisplayName("apply refuses a row it could not attribute")
    void applySkipsTheUnattributable() {
        portfolio("RELIANCE.NS", zeroPriceBuy("72", CREDITED));
        when(symbolEventRepository.findBySymbolOrderByExDateDesc("RELIANCE.NS"))
                .thenReturn(List.of(event("BONUS", "5.0", EX), event("SPLIT", "2.0", EX.plusDays(1))));

        Map<String, Object> result = service.apply(userId);

        assertThat(result.get("applied")).isEqualTo(0);
        verify(transactionRepository, never()).save(any());
        assertThat((List<?>) result.get("skipped")).hasSize(1);
    }
}
