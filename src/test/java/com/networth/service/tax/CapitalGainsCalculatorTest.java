package com.networth.service.tax;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.TransactionRepository;
import com.networth.service.tax.rules.TaxRuleRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapitalGainsCalculatorTest {

    @Mock HoldingRepository holdingRepository;
    @Mock TransactionRepository transactionRepository;
    CapitalGainsCalculator calculator;

    @BeforeEach
    void setUp() {
        // A real registry rather than a mock: these tests exist to pin the actual rate data
        // and the date resolution, which a stubbed registry would hide.
        calculator = new CapitalGainsCalculator(
                holdingRepository, transactionRepository, new TaxRuleRegistry());
    }

    private static final String FY = "2024-2025";
    private final UUID userId = UUID.randomUUID();
    private final UUID holdingId = UUID.randomUUID();

    // ── fixtures ──────────────────────────────────────────────────────

    private Holding holding(AssetType type) {
        Holding h = new Holding();
        h.setId(holdingId);
        h.setUserId(userId);
        h.setAssetType(type);
        h.setSymbol("TESTCO");
        return h;
    }

    private Transaction txn(TransactionType type, String qty, String price, String date) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setHoldingId(holdingId);
        t.setTransactionType(type);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setTransactionDate(LocalDateTime.parse(date + "T10:00:00"));
        return t;
    }

    private Map<String, Object> run(AssetType type, List<Transaction> txns) {
        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(holding(type)));
        // The calculator fetches the whole portfolio's transactions once and groups by
        // holding, rather than querying per holding.
        when(transactionRepository.findByUserId(userId)).thenReturn(txns);
        return calculator.calculateCapitalGains(userId, FY);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> equity(Map<String, Object> result) {
        return (Map<String, Object>) result.get("equity");
    }

    private BigDecimal num(Map<String, Object> m, String key) {
        return (BigDecimal) m.get(key);
    }

    // ── FIFO lot consumption ──────────────────────────────────────────

    @Test
    @DisplayName("a partially consumed buy lot is not reusable at full quantity by later sells")
    void partiallyConsumedLotIsDecremented() {
        // One lot of 100 @ 10. Two sells of 60 each = 120 units demanded from a 100-unit lot.
        // Only 100 units have a known cost basis; the 20-unit excess must be excluded.
        Map<String, Object> eq = equity(run(AssetType.EQUITY, List.of(
                txn(TransactionType.BUY,  "100", "10", "2024-04-01"),
                txn(TransactionType.SELL,  "60", "50", "2024-06-01"),
                txn(TransactionType.SELL,  "60", "50", "2024-08-01"))));

        // 100 matched units at +40/unit = 4000. Previously reported 4800 by re-consuming the lot.
        assertThat(num(eq, "stcg")).isEqualByComparingTo("4000");
    }

    @Test
    @DisplayName("FIFO consumes the earliest lot first, then moves to the next")
    void fifoOrderingAcrossLots() {
        // Lots: 100 @ 10 then 100 @ 30. Sell 150 @ 50.
        // FIFO: 100 from lot1 (+40 each = 4000) + 50 from lot2 (+20 each = 1000) = 5000.
        Map<String, Object> eq = equity(run(AssetType.EQUITY, List.of(
                txn(TransactionType.BUY,  "100", "10", "2024-04-01"),
                txn(TransactionType.BUY,  "100", "30", "2024-05-01"),
                txn(TransactionType.SELL, "150", "50", "2024-07-01"))));

        assertThat(num(eq, "stcg")).isEqualByComparingTo("5000");
    }

    // ── losses ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a realised loss is reported, not discarded")
    void realisedLossIsReported() {
        // Buy 100 @ 100, sell 100 @ 40 -> loss of 6000.
        Map<String, Object> eq = equity(run(AssetType.EQUITY, List.of(
                txn(TransactionType.BUY,  "100", "100", "2024-04-01"),
                txn(TransactionType.SELL, "100",  "40", "2024-09-01"))));

        assertThat(num(eq, "stcl")).isEqualByComparingTo("6000");
        assertThat(num(eq, "stcg")).isEqualByComparingTo("0");
        assertThat(num(eq, "taxOnSTCG")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("short-term loss is set off against short-term gain before tax")
    void shortTermLossOffsetsShortTermGain() {
        // Lot A: gain of 4000. Lot B: loss of 1000. Net STCG = 3000.
        // Both sales fall before 23 Jul 2024, so the pre-Budget 15% rate applies: 450.
        Map<String, Object> eq = equity(run(AssetType.EQUITY, List.of(
                txn(TransactionType.BUY,  "100", "10", "2024-04-01"),
                txn(TransactionType.SELL, "100", "50", "2024-06-01"),
                txn(TransactionType.BUY,  "100", "20", "2024-06-10"),
                txn(TransactionType.SELL, "100", "10", "2024-07-10"))));

        assertThat(num(eq, "stcg")).isEqualByComparingTo("4000");
        assertThat(num(eq, "stcl")).isEqualByComparingTo("1000");
        assertThat(num(eq, "stcgAfterSetOff")).isEqualByComparingTo("3000");
        assertThat(num(eq, "taxOnSTCG")).isEqualByComparingTo("450.00");
    }

    @Test
    @DisplayName("the 23 July 2024 rate change is applied by sale date, not by financial year")
    void ratesResolveBySaleDate() {
        // Identical disposals either side of the Budget: 100 units bought at 10, sold at 50,
        // a 4000 short-term gain each. Pre-Budget attracts 15%, post-Budget 20%.
        Map<String, Object> before = equity(run(AssetType.EQUITY, List.of(
                txn(TransactionType.BUY,  "100", "10", "2024-04-01"),
                txn(TransactionType.SELL, "100", "50", "2024-07-01"))));
        assertThat(num(before, "stcg")).isEqualByComparingTo("4000");
        assertThat(num(before, "taxOnSTCG")).isEqualByComparingTo("600.00");   // 15%

        Map<String, Object> after = equity(run(AssetType.EQUITY, List.of(
                txn(TransactionType.BUY,  "100", "10", "2024-04-01"),
                txn(TransactionType.SELL, "100", "50", "2024-08-01"))));
        assertThat(num(after, "stcg")).isEqualByComparingTo("4000");
        assertThat(num(after, "taxOnSTCG")).isEqualByComparingTo("800.00");    // 20%
    }

    @Test
    @DisplayName("a year spanning both rate periods taxes each disposal at its own rate")
    void mixedRatePeriodsWithinOneYear() {
        // 4000 gain before the Budget at 15% (600) plus 4000 after it at 20% (800) = 1400.
        Map<String, Object> r = run(AssetType.EQUITY, List.of(
                txn(TransactionType.BUY,  "100", "10", "2024-04-01"),
                txn(TransactionType.SELL, "100", "50", "2024-07-01"),
                txn(TransactionType.BUY,  "100", "10", "2024-07-25"),
                txn(TransactionType.SELL, "100", "50", "2024-08-01")));
        Map<String, Object> eq = equity(r);

        assertThat(num(eq, "stcg")).isEqualByComparingTo("8000");
        assertThat(num(eq, "taxOnSTCG")).isEqualByComparingTo("1400.00");
        // The response reports which rate periods contributed, so a reviewer can see why.
        assertThat((List<?>) r.get("ratePeriods")).hasSize(2);
    }

    @Test
    @DisplayName("a financial year with no rules defined is rejected, not computed with another year's rates")
    void unsupportedFinancialYearRejected() {
        assertThatThrownBy(() -> calculator.calculateCapitalGains(userId, "1999-2000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1999-2000");
    }

    @Test
    @DisplayName("long-term gain below the 1.25L exemption attracts no tax")
    void ltcgExemptionApplies() {
        // Held >365 days: buy 1000 @ 100, sell 1000 @ 200 -> LTCG 100000, under the 125000 exemption.
        Map<String, Object> eq = equity(run(AssetType.EQUITY, List.of(
                txn(TransactionType.BUY,  "1000", "100", "2023-05-01"),
                txn(TransactionType.SELL, "1000", "200", "2024-06-01"))));

        assertThat(num(eq, "ltcg")).isEqualByComparingTo("100000");
        assertThat(num(eq, "taxableLTCG")).isEqualByComparingTo("0");
        assertThat(num(eq, "taxOnLTCG")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("crypto losses are not set off, per section 115BBH")
    void cryptoLossesAreNotSetOff() {
        // Gain of 5000 on one lot, loss of 2000 on another. Tax must apply to the full 5000.
        Map<String, Object> r = run(AssetType.CRYPTO, List.of(
                txn(TransactionType.BUY,  "10", "1000", "2024-04-01"),
                txn(TransactionType.SELL, "10", "1500", "2024-06-01"),
                txn(TransactionType.BUY,  "10", "1500", "2024-06-10"),
                txn(TransactionType.SELL, "10", "1300", "2024-07-10")));
        @SuppressWarnings("unchecked") Map<String, Object> crypto = (Map<String, Object>) r.get("crypto");

        assertThat(num(crypto, "gains")).isEqualByComparingTo("5000");
        assertThat(num(crypto, "losses")).isEqualByComparingTo("2000");
        assertThat(num(crypto, "tax")).isEqualByComparingTo("1500.00");   // 30% of 5000, loss ignored
        assertThat(crypto.get("lossSetOffAllowed")).isEqualTo(false);
    }

    // ── financial year parsing ────────────────────────────────────────

    @Test
    @DisplayName("both YYYY-YYYY and the Indian YYYY-YY form select the same window")
    void financialYearFormatsAreEquivalent() {
        List<Transaction> txns = List.of(
                txn(TransactionType.BUY,  "100", "10", "2024-04-01"),
                txn(TransactionType.SELL, "100", "50", "2024-06-01"));

        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(holding(AssetType.EQUITY)));
        when(transactionRepository.findByUserId(userId)).thenReturn(txns);

        BigDecimal fourDigit = num(equity(calculator.calculateCapitalGains(userId, "2024-2025")), "stcg");
        BigDecimal twoDigit  = num(equity(calculator.calculateCapitalGains(userId, "2024-25")),   "stcg");

        assertThat(fourDigit).isEqualByComparingTo("4000");
        assertThat(twoDigit).isEqualByComparingTo(fourDigit);
    }

    @Test
    @DisplayName("an unparseable or non-consecutive financial year fails loudly")
    void invalidFinancialYearRejected() {
        assertThatThrownBy(() -> calculator.calculateCapitalGains(userId, "2024"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected YYYY-YYYY");
        assertThatThrownBy(() -> calculator.calculateCapitalGains(userId, "2024-2030"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consecutive");
        assertThatThrownBy(() -> calculator.calculateCapitalGains(userId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sells outside the requested financial year are excluded")
    void sellsOutsideFinancialYearExcluded() {
        Map<String, Object> eq = equity(run(AssetType.EQUITY, List.of(
                txn(TransactionType.BUY,  "100", "10", "2023-04-01"),
                txn(TransactionType.SELL, "100", "50", "2023-06-01"))));   // FY 2023-24, not 2024-25

        assertThat(num(eq, "stcg")).isEqualByComparingTo("0");
        assertThat(num(eq, "stcl")).isEqualByComparingTo("0");
    }

    // ── asset classes beyond equity and crypto ────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> assetClass(Map<String, Object> result, String key) {
        return (Map<String, Object>) result.get(key);
    }

    @Test
    @DisplayName("long-term gold gain is taxed at the fixed rate, not left untaxed")
    void goldLongTermIsTaxed() {
        // Gold long-term threshold is 1095 days. Bought at 100, sold at 200 after ~4 years.
        Map<String, Object> r = run(AssetType.GOLD, List.of(
                txn(TransactionType.BUY,  "100", "100", "2021-04-01"),
                txn(TransactionType.SELL, "100", "200", "2024-08-01")));
        Map<String, Object> gold = assetClass(r, "gold");

        assertThat(num(gold, "gains")).isEqualByComparingTo("10000");
        assertThat(num(gold, "longTermGains")).isEqualByComparingTo("10000");
        // Sold after 23 Jul 2024, so 12.5% applies: previously this was reported as zero tax.
        assertThat(num(gold, "tax")).isEqualByComparingTo("1250.00");
        assertThat(gold.get("taxAtSlabRate")).isEqualTo(false);
    }

    @Test
    @DisplayName("short-term gold gain is flagged as slab-rated rather than given a made-up figure")
    void goldShortTermIsSlabRated() {
        Map<String, Object> r = run(AssetType.GOLD, List.of(
                txn(TransactionType.BUY,  "100", "100", "2024-04-01"),
                txn(TransactionType.SELL, "100", "200", "2024-08-01")));
        Map<String, Object> gold = assetClass(r, "gold");

        assertThat(num(gold, "shortTermGains")).isEqualByComparingTo("10000");
        assertThat(num(gold, "tax")).isEqualByComparingTo("0");
        assertThat(gold.get("taxAtSlabRate")).isEqualTo(true);
        assertThat((String) gold.get("slabRateNote")).contains("slab rate");
    }

    @Test
    @DisplayName("debt fund gains are always slab-rated")
    void debtIsSlabRated() {
        Map<String, Object> r = run(AssetType.BOND, List.of(
                txn(TransactionType.BUY,  "100", "100", "2020-04-01"),
                txn(TransactionType.SELL, "100", "150", "2024-08-01")));
        Map<String, Object> debt = assetClass(r, "debt");

        assertThat(num(debt, "gains")).isEqualByComparingTo("5000");
        assertThat(num(debt, "tax")).isEqualByComparingTo("0");
        assertThat(debt.get("taxAtSlabRate")).isEqualTo(true);
    }

    @Test
    @DisplayName("gold sold before 23 July 2024 uses the older 20% rate")
    void goldRateFollowsTheBudgetDate() {
        Map<String, Object> r = run(AssetType.GOLD, List.of(
                txn(TransactionType.BUY,  "100", "100", "2021-04-01"),
                txn(TransactionType.SELL, "100", "200", "2024-07-01")));
        assertThat(num(assetClass(r, "gold"), "tax")).isEqualByComparingTo("2000.00");   // 20%
    }

    @Test
    @DisplayName("tax on other asset classes is included in cess and the total")
    void otherAssetTaxFeedsCessAndTotal() {
        Map<String, Object> r = run(AssetType.GOLD, List.of(
                txn(TransactionType.BUY,  "100", "100", "2021-04-01"),
                txn(TransactionType.SELL, "100", "200", "2024-08-01")));

        assertThat(num(r, "otherAssetTax")).isEqualByComparingTo("1250.00");
        // 4% cess on the 1250, which the old code omitted entirely for these classes.
        assertThat(num(r, "cess")).isEqualByComparingTo("50.00");
        assertThat(num(r, "totalTax")).isEqualByComparingTo("1300.00");
    }

    @Test
    @DisplayName("a loss in these classes reduces the tax rather than being ignored")
    void lossReducesOtherAssetTax() {
        // One gold lot gains 10,000 long-term, another loses 4,000 long-term -> 6,000 net.
        Map<String, Object> r = run(AssetType.GOLD, List.of(
                txn(TransactionType.BUY,  "100", "100", "2021-04-01"),
                txn(TransactionType.SELL, "100", "200", "2024-08-01"),
                txn(TransactionType.BUY,  "100", "200", "2021-05-01"),
                txn(TransactionType.SELL, "100", "160", "2024-09-01")));
        Map<String, Object> gold = assetClass(r, "gold");

        assertThat(num(gold, "longTermGains")).isEqualByComparingTo("10000");
        assertThat(num(gold, "losses")).isEqualByComparingTo("4000");
        assertThat(num(gold, "longTermAfterSetOff")).isEqualByComparingTo("6000");
        assertThat(num(gold, "tax")).isEqualByComparingTo("750.00");   // 12.5% of 6000
    }

    // ── precision ─────────────────────────────────────────────────────

    @Test
    @DisplayName("fractional quantities and prices stay exact (no binary floating point drift)")
    void moneyArithmeticIsExact() {
        // 0.1 and 0.2 are the classic double-precision trap.
        // 3 units at cost 0.1 sold at 0.3 -> exactly 0.6 gain.
        Map<String, Object> eq = equity(run(AssetType.EQUITY, List.of(
                txn(TransactionType.BUY,  "3", "0.1", "2024-04-01"),
                txn(TransactionType.SELL, "3", "0.3", "2024-06-01"))));

        assertThat(num(eq, "stcg")).isEqualByComparingTo("0.6");
        assertThat(num(eq, "stcg").toPlainString()).doesNotContain("0.6000000");
    }
}
