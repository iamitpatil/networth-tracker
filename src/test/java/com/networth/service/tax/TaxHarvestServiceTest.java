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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaxHarvestServiceTest {

    @Mock HoldingRepository holdingRepository;
    @Mock TransactionRepository transactionRepository;
    TaxHarvestService service;

    private final UUID userId = UUID.randomUUID();
    /** FY 2025-26 is a verified year: LTCG 12.5% over Rs 1.25L, STCG 20%. */
    private static final String FY = "2025-2026";

    @BeforeEach
    void setUp() {
        TaxRuleRegistry registry = new TaxRuleRegistry();
        CapitalGainsCalculator capitalGains =
                new CapitalGainsCalculator(holdingRepository, transactionRepository, registry);
        service = new TaxHarvestService(holdingRepository, transactionRepository, registry, capitalGains);
    }

    // ── fixtures ──────────────────────────────────────────────────────

    private Holding holding(String symbol, String qty, String avgBuy, String currentValue) {
        Holding h = new Holding();
        h.setId(UUID.randomUUID());
        h.setUserId(userId);
        h.setAssetType(AssetType.EQUITY);
        h.setSymbol(symbol);
        h.setQuantity(new BigDecimal(qty));
        h.setAverageBuyPrice(new BigDecimal(avgBuy));
        h.setCurrentPrice(new BigDecimal(currentValue).divide(new BigDecimal(qty)));
        h.setCurrentValue(new BigDecimal(currentValue));
        // Row written now. The service must NOT use this as the purchase date.
        h.setCreatedAt(Instant.now());
        return h;
    }

    private Transaction buy(UUID holdingId, String qty, String price, LocalDate date) {
        Transaction t = new Transaction();
        t.setId(UUID.randomUUID());
        t.setHoldingId(holdingId);
        t.setUserId(userId);
        t.setTransactionType(TransactionType.BUY);
        t.setQuantity(new BigDecimal(qty));
        t.setPrice(new BigDecimal(price));
        t.setAmount(new BigDecimal(qty).multiply(new BigDecimal(price)));
        t.setTransactionDate(date.atStartOfDay());
        return t;
    }

    private List<Map<String, Object>> run(List<Holding> holdings, List<Transaction> txns) {
        when(holdingRepository.findByUserId(userId)).thenReturn(holdings);
        when(transactionRepository.findByUserId(userId)).thenReturn(txns);
        return service.findHarvestingOpportunities(userId, FY);
    }

    private Map<String, Object> only(List<Map<String, Object>> opps) {
        assertThat(opps).hasSize(1);
        return opps.get(0);
    }

    private BigDecimal num(Map<String, Object> m, String key) {
        return (BigDecimal) m.get(key);
    }

    // ── loss harvesting ───────────────────────────────────────────────

    @Test
    @DisplayName("a holding that is down is surfaced as a loss to book")
    void lossHarvestIsSurfaced() {
        // Bought 100 @ 100 = 10,000 cost. Now worth 6,000 -> a 4,000 loss, held 60 days.
        Holding h = holding("LOSER", "100", "100", "6000");
        Map<String, Object> opp = only(run(List.of(h),
                List.of(buy(h.getId(), "100", "100", LocalDate.now().minusDays(60)))));

        assertThat(opp.get("type")).isEqualTo("LOSS_HARVEST");
        assertThat(num(opp, "currentLoss")).isEqualByComparingTo("4000");
        assertThat(num(opp, "unrealizedGain")).isEqualByComparingTo("-4000");
        assertThat(opp.get("action")).isEqualTo("sell_to_book_loss");
        // Short-term loss relieves short-term gains at 20%.
        assertThat(num(opp, "potentialSavings")).isEqualByComparingTo("800.00");
        assertThat(opp.get("isLongTerm")).isEqualTo(false);
    }

    @Test
    @DisplayName("a long-term loss is worth less than a short-term loss of the same size")
    void longTermLossReliefIsLower() {
        Holding shortTerm = holding("ST", "100", "100", "6000");
        Holding longTerm = holding("LT", "100", "100", "6000");

        BigDecimal stSavings = num(only(run(List.of(shortTerm),
                List.of(buy(shortTerm.getId(), "100", "100", LocalDate.now().minusDays(60))))), "potentialSavings");
        BigDecimal ltSavings = num(only(run(List.of(longTerm),
                List.of(buy(longTerm.getId(), "100", "100", LocalDate.now().minusDays(500))))), "potentialSavings");

        assertThat(stSavings).isEqualByComparingTo("800.00");   // 4000 at 20%
        assertThat(ltSavings).isEqualByComparingTo("500.00");   // 4000 at 12.5%
        assertThat(ltSavings).isLessThan(stSavings);
    }

    @Test
    @DisplayName("rates render cleanly in the reason text - 12.5%, not 12.500%")
    void ratePercentagesAreFormattedCleanly() {
        Holding longTerm = holding("LT", "100", "100", "6000");
        String reason = (String) only(run(List.of(longTerm),
                List.of(buy(longTerm.getId(), "100", "100", LocalDate.now().minusDays(500))))).get("reason");
        assertThat(reason).contains("12.5%").doesNotContain("12.500");

        Holding shortTerm = holding("ST", "100", "100", "6000");
        String stReason = (String) only(run(List.of(shortTerm),
                List.of(buy(shortTerm.getId(), "100", "100", LocalDate.now().minusDays(60))))).get("reason");
        assertThat(stReason).contains("20%").doesNotContain("20.0%");
    }

    // ── gain harvesting ───────────────────────────────────────────────

    @Test
    @DisplayName("a long-term gain inside the exemption is surfaced as tax-free to book")
    void gainHarvestWithinExemption() {
        // Cost 10,000, now 60,000 -> 50,000 long-term gain, comfortably inside the 1.25L exemption.
        Holding h = holding("WINNER", "100", "100", "60000");
        Map<String, Object> opp = only(run(List.of(h),
                List.of(buy(h.getId(), "100", "100", LocalDate.now().minusDays(500)))));

        assertThat(opp.get("type")).isEqualTo("GAIN_HARVEST");
        assertThat(num(opp, "unrealizedGain")).isEqualByComparingTo("50000");
        assertThat(num(opp, "exemptionUsed")).isEqualByComparingTo("50000");
        assertThat(num(opp, "potentialSavings")).isEqualByComparingTo("6250.00");   // 50000 at 12.5%
        assertThat(opp.get("action")).isEqualTo("sell_and_rebuy");
    }

    @Test
    @DisplayName("the exemption is shared across holdings, not offered to each in full")
    void exemptionHeadroomIsConsumed() {
        // Two winners each with a 100,000 long-term gain. The 125,000 exemption cannot cover
        // both, so the second may only claim the remaining 25,000.
        Holding a = holding("WIN-A", "100", "100", "110000");
        Holding b = holding("WIN-B", "100", "100", "110000");
        List<Map<String, Object>> opps = run(List.of(a, b), List.of(
                buy(a.getId(), "100", "100", LocalDate.now().minusDays(500)),
                buy(b.getId(), "100", "100", LocalDate.now().minusDays(500))));

        assertThat(opps).hasSize(2);
        BigDecimal claimed = num(opps.get(0), "exemptionUsed").add(num(opps.get(1), "exemptionUsed"));
        assertThat(claimed).isEqualByComparingTo("125000");
        assertThat(num(opps.get(0), "exemptionUsed")).isEqualByComparingTo("100000");
        assertThat(num(opps.get(1), "exemptionUsed")).isEqualByComparingTo("25000");
    }

    // ── the advisory case, and the bug it replaces ─────────────────────

    @Test
    @DisplayName("a holding near the long-term threshold advises waiting and claims no saving")
    void nearThresholdAdvisesWaiting() {
        // 350 days held: 15 days short of long-term.
        Holding h = holding("ALMOST", "100", "100", "20000");
        Map<String, Object> opp = only(run(List.of(h),
                List.of(buy(h.getId(), "100", "100", LocalDate.now().minusDays(350)))));

        assertThat(opp.get("type")).isEqualTo("WAIT_FOR_LTCG");
        assertThat(opp.get("action")).isEqualTo("wait_for_ltcg");
        // The old code reported gain * 20% here as a "saving" - it is a cost, not a saving.
        assertThat(num(opp, "potentialSavings")).isEqualByComparingTo("0");
        assertThat(num(opp, "taxIfSoldNow")).isEqualByComparingTo("2000.00");   // 10000 at 20%
        assertThat((String) opp.get("reason")).contains("15 more days");
    }

    @Test
    @DisplayName("a short-term winner well short of the threshold is not an opportunity")
    void shortTermWinnerIsNotSuggested() {
        // 100 days held with a gain: selling costs tax and it is too early to advise waiting.
        Holding h = holding("EARLY", "100", "100", "20000");
        assertThat(run(List.of(h),
                List.of(buy(h.getId(), "100", "100", LocalDate.now().minusDays(100))))).isEmpty();
    }

    // ── holding period source ─────────────────────────────────────────

    @Test
    @DisplayName("holding period comes from the purchase date, not the row's createdAt")
    void holdingPeriodUsesPurchaseDate() {
        // The row was written today (see the fixture) but the asset was bought 800 days ago.
        // Using createdAt would report 0 days and misclassify this as short-term.
        Holding h = holding("IMPORTED", "100", "100", "60000");
        Map<String, Object> opp = only(run(List.of(h),
                List.of(buy(h.getId(), "100", "100", LocalDate.now().minusDays(800)))));

        assertThat((Long) opp.get("holdingDays")).isEqualTo(800L);
        assertThat(opp.get("isLongTerm")).isEqualTo(true);
        assertThat(opp.get("type")).isEqualTo("GAIN_HARVEST");
    }

    @Test
    @DisplayName("with no purchase transaction it falls back to createdAt rather than failing")
    void fallsBackToCreatedAtWhenNoTransactions() {
        Holding h = holding("NOTXN", "100", "100", "6000");
        Map<String, Object> opp = only(run(List.of(h), List.of()));
        assertThat((Long) opp.get("holdingDays")).isEqualTo(0L);
        assertThat(opp.get("type")).isEqualTo("LOSS_HARVEST");
    }

    // ── field contract the UI depends on ──────────────────────────────

    @Test
    @DisplayName("every opportunity carries the fields the UI reads")
    void responseCarriesUiFields() {
        Holding loser = holding("L", "100", "100", "6000");
        Holding winner = holding("W", "100", "100", "60000");
        List<Map<String, Object>> opps = run(List.of(loser, winner), List.of(
                buy(loser.getId(), "100", "100", LocalDate.now().minusDays(60)),
                buy(winner.getId(), "100", "100", LocalDate.now().minusDays(500))));

        assertThat(opps).hasSize(2);
        for (Map<String, Object> opp : opps) {
            // The UI previously read currentLoss, potentialSavings and quantity, none of which
            // the backend returned, so every card rendered zeros.
            assertThat(opp).containsKeys("symbol", "quantity", "currentLoss", "potentialSavings",
                    "unrealizedGain", "type", "action", "reason", "holdingDays", "isLongTerm");
            assertThat(opp.get("quantity")).isNotNull();
        }
    }

    @Test
    @DisplayName("non-equity holdings and zero-movement holdings are skipped")
    void irrelevantHoldingsSkipped() {
        Holding gold = holding("GOLD", "100", "100", "6000");
        gold.setAssetType(AssetType.GOLD);
        Holding flat = holding("FLAT", "100", "100", "10000");   // no gain, no loss

        assertThat(run(List.of(gold, flat), List.of(
                buy(gold.getId(), "100", "100", LocalDate.now().minusDays(60)),
                buy(flat.getId(), "100", "100", LocalDate.now().minusDays(60))))).isEmpty();
    }
}
