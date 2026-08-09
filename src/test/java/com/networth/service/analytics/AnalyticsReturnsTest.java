package com.networth.service.analytics;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.MarketPriceRepository;
import com.networth.repository.TransactionRepository;
import com.networth.service.portfolio.HoldingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Returns must be computed from money that actually moved.
 *
 * <p>A corporate action changes what a position looks like without any cash behind it. Counting
 * one as a cash flow invents a return that never happened, and a demerger leg is the dangerous
 * case because it carries a real, non-zero amount -- the apportioned cost -- so it looks exactly
 * like a purchase to anything that only checks whether the amount is set.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyticsReturnsTest {

    @Mock HoldingRepository holdingRepository;
    @Mock HoldingService holdingService;
    @Mock TransactionRepository transactionRepository;
    @Mock MarketPriceRepository marketPriceRepository;
    @Mock RiskService riskService;

    private final UUID userId = UUID.randomUUID();
    private final UUID holdingId = UUID.randomUUID();

    private AnalyticsService service() {
        return new AnalyticsService(holdingRepository, holdingService, transactionRepository,
                marketPriceRepository, new XIRRCalculator(), riskService);
    }

    private Holding holding(String currentValue) {
        Holding h = new Holding();
        h.setId(holdingId);
        h.setUserId(userId);
        h.setAssetType(AssetType.EQUITY);
        h.setSymbol("TESTCO");
        h.setCurrentValue(new BigDecimal(currentValue));
        return h;
    }

    private Transaction txn(TransactionType type, String amount, String date) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setHoldingId(holdingId);
        t.setUserId(userId);
        t.setTransactionType(type);
        t.setQuantity(BigDecimal.ONE);
        t.setPrice(new BigDecimal(amount));
        t.setAmount(new BigDecimal(amount));
        t.setTransactionDate(LocalDateTime.parse(date + "T10:00:00"));
        return t;
    }

    private void given(String currentValue, List<Transaction> txns) {
        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(holding(currentValue)));
        when(transactionRepository.findByUserId(userId)).thenReturn(txns);
    }

    private String oneYearAgo() {
        return LocalDate.now().minusDays(365).toString();
    }

    // ── XIRR ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a bonus issue does not change XIRR: no money moved")
    void bonusDoesNotAffectXirr() {
        given("2000", List.of(txn(TransactionType.BUY, "1000", oneYearAgo())));
        BigDecimal withoutBonus = service().calculateXIRR(userId);

        given("2000", List.of(
                txn(TransactionType.BUY, "1000", oneYearAgo()),
                txn(TransactionType.BONUS, "0", oneYearAgo())));
        BigDecimal withBonus = service().calculateXIRR(userId);

        assertThat(withBonus).isEqualByComparingTo(withoutBonus);
    }

    @Test
    @DisplayName("a demerger leg does not change XIRR, despite carrying a non-zero amount")
    void demergerDoesNotAffectXirr() {
        given("2000", List.of(txn(TransactionType.BUY, "1000", oneYearAgo())));
        BigDecimal before = service().calculateXIRR(userId);

        // 300 of apportioned cost on each leg. Read as a purchase and a sale, these would move
        // the answer; read correctly, they are invisible to it.
        given("2000", List.of(
                txn(TransactionType.BUY, "1000", oneYearAgo()),
                txn(TransactionType.DEMERGER_OUT, "300", oneYearAgo()),
                txn(TransactionType.DEMERGER_IN, "300", oneYearAgo())));
        BigDecimal after = service().calculateXIRR(userId);

        assertThat(after).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("a split does not change XIRR")
    void splitDoesNotAffectXirr() {
        given("2000", List.of(txn(TransactionType.BUY, "1000", oneYearAgo())));
        BigDecimal before = service().calculateXIRR(userId);

        given("2000", List.of(
                txn(TransactionType.BUY, "1000", oneYearAgo()),
                txn(TransactionType.SPLIT, "0", oneYearAgo())));

        assertThat(service().calculateXIRR(userId)).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("doubling over a year is roughly a 100% XIRR")
    void baselineXirrIsSane() {
        given("2000", List.of(txn(TransactionType.BUY, "1000", oneYearAgo())));

        // Sanity anchor: without it the "unchanged" assertions above would also hold if XIRR
        // returned null for everything.
        assertThat(service().calculateXIRR(userId)).isNotNull();
        assertThat(service().calculateXIRR(userId).doubleValue()).isCloseTo(1.0, within());
    }

    @Test
    @DisplayName("a dividend counts as a return: money genuinely came back")
    void dividendIsAnInflow() {
        given("1000", List.of(txn(TransactionType.BUY, "1000", oneYearAgo())));
        BigDecimal withoutDividend = service().calculateXIRR(userId);

        given("1000", List.of(
                txn(TransactionType.BUY, "1000", oneYearAgo()),
                txn(TransactionType.DIVIDEND, "100", LocalDate.now().minusDays(180).toString())));

        assertThat(service().calculateXIRR(userId)).isGreaterThan(withoutDividend);
    }

    // ── CAGR ────────────────────────────────────────────────────────

    @Test
    @DisplayName("CAGR counts every contribution, not just the first")
    void cagrCountsEveryContribution() {
        // Two purchases of 1,000 each, now worth 2,000 -- that is break-even, so 0%.
        // Counting only the first purchase would report a 100% gain on a portfolio that made
        // nothing.
        given("2000", List.of(
                txn(TransactionType.BUY, "1000", oneYearAgo()),
                txn(TransactionType.BUY, "1000", LocalDate.now().minusDays(180).toString())));

        assertThat(service().calculateCAGR(userId).doubleValue()).isCloseTo(0.0, within());
    }

    @Test
    @DisplayName("CAGR ignores corporate actions")
    void cagrIgnoresCorporateActions() {
        given("2000", List.of(txn(TransactionType.BUY, "1000", oneYearAgo())));
        BigDecimal before = service().calculateCAGR(userId);

        given("2000", List.of(
                txn(TransactionType.BUY, "1000", oneYearAgo()),
                txn(TransactionType.BONUS, "0", oneYearAgo()),
                txn(TransactionType.DEMERGER_IN, "300", oneYearAgo())));

        assertThat(service().calculateCAGR(userId)).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("CAGR says nothing when more has been withdrawn than invested")
    void cagrDeclinesToGuessOnANegativeBase() {
        // Sold for more than the purchase cost, so net invested is negative. A negative base
        // raised to a fractional power is NaN; reporting 0 is the honest answer.
        given("0", List.of(
                txn(TransactionType.BUY, "1000", oneYearAgo()),
                txn(TransactionType.SELL, "1500", LocalDate.now().minusDays(30).toString())));

        assertThat(service().calculateCAGR(userId)).isEqualByComparingTo("0");
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(0.02);
    }
}
