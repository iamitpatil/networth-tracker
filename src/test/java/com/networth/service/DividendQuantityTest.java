package com.networth.service;

import com.networth.model.dto.TransactionResponse;
import com.networth.model.entity.Dividend;
import com.networth.model.entity.Holding;
import com.networth.model.entity.SymbolEvent;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.DividendRepository;
import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolEventRepository;
import com.networth.service.portfolio.TransactionService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * How many shares were on the register on the record date.
 *
 * <p>The payout is that number times the amount per share, so getting it wrong understates real money.
 * {@code computeQtyOnDate} counted only BUY, SIP, LUMPSUM and SELL, which meant a bonus issue, a share
 * split, a demerger or a transfer between demat accounts was invisible to it. Every one of the tests
 * below fails against that version.
 *
 * <p>Latent rather than active when written — the live database held only BUY and SELL rows — but a
 * bonus issue or a broker transfer would have started quietly understating payouts with no error
 * anywhere, which is the same failure mode as the bug this whole change came from.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DividendQuantityTest {

    @Mock HoldingRepository holdingRepository;
    @Mock DividendRepository dividendRepository;
    @Mock SymbolEventRepository symbolEventRepository;
    @Mock TransactionService transactionService;

    private DividendCalculationService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID holdingId = UUID.randomUUID();
    private static final String SYMBOL = "RELIANCE.NS";

    /** The record date every test values against. */
    private final LocalDate recordDate = LocalDate.now().minusDays(100);
    /** Everything below is dated before the record date so it counts towards it. */
    private final LocalDate earlier = recordDate.minusDays(30);

    private final List<TransactionResponse> txns = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DividendCalculationService(holdingRepository, dividendRepository,
                symbolEventRepository, transactionService);

        Holding holding = new Holding();
        holding.setId(holdingId);
        holding.setUserId(userId);
        holding.setAssetType(AssetType.EQUITY);
        holding.setSymbol(SYMBOL);
        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(holding));
        when(dividendRepository.findByHoldingId(holdingId)).thenReturn(List.of());
        when(transactionService.getUserTransactions(userId.toString())).thenAnswer(i -> txns);

        // Rs 10 per share, so a payout of 1000.00 means 100 shares were counted.
        when(symbolEventRepository.findBySymbolsAndType(any(), anyString())).thenReturn(List.of(
                SymbolEvent.builder().symbol(SYMBOL).eventType("DIVIDEND").eventSubtype("Final")
                        .amountPerShare(new BigDecimal("10")).exDate(recordDate).recordDate(recordDate)
                        .source("NSE").build()));

        // The baseline position: 100 shares bought well before the record date.
        txns.add(txn(TransactionType.BUY, "100", earlier.minusDays(60), null));
    }

    private TransactionResponse txn(TransactionType type, String qty, LocalDate date, String adjustmentFactor) {
        return TransactionResponse.builder()
                .id(UUID.randomUUID().toString())
                .holdingId(holdingId.toString())
                .transactionType(type)
                .quantity(new BigDecimal(qty))
                .price(new BigDecimal("1000"))
                .adjustmentFactor(adjustmentFactor == null ? null : new BigDecimal(adjustmentFactor))
                .transactionDate(date.atStartOfDay())
                .build();
    }

    /** The payout that was actually written. */
    private BigDecimal payout() {
        service.calculateDividends(userId);
        ArgumentCaptor<Dividend> saved = ArgumentCaptor.forClass(Dividend.class);
        verify(dividendRepository).save(saved.capture());
        return saved.getValue().getDividendAmount();
    }

    @Test
    @DisplayName("the baseline: 100 shares held on the record date pay 100 x Rs 10")
    void baseline() {
        assertThat(payout()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("shares transferred in before the record date earn the dividend")
    void transferInCounts() {
        // A transfer is neither a purchase nor a disposal, so it was omitted entirely. But whoever is on
        // the register on the record date is paid, and after a transfer in that is you.
        txns.add(txn(TransactionType.TRANSFER_IN, "50", earlier, null));

        assertThat(payout()).as("150 shares, not 100").isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("shares transferred out before the record date do not")
    void transferOutReduces() {
        txns.add(txn(TransactionType.TRANSFER_OUT, "40", earlier, null));

        assertThat(payout()).as("60 shares remain on the register").isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("bonus shares issued before the record date earn the dividend")
    void bonusCounts() {
        // A 1:1 bonus doubles the holding. No money changed hands, which is why it was not in the
        // buy list -- but the register did change, and that is what a dividend follows.
        txns.add(txn(TransactionType.BONUS, "100", earlier, null));

        assertThat(payout()).as("200 shares after the bonus").isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("a split's extra shares count, and are not counted twice")
    void splitCountsOnce() {
        // The stored quantity for a SPLIT is the signed delta -- +100 for a 1:2 split on 100 shares --
        // and adjustmentFactor 0.5 rescales the per-share cost. Only the delta affects the count.
        txns.add(txn(TransactionType.SPLIT, "100", earlier, "0.5"));

        assertThat(payout()).as("200 shares, not 300").isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("a consolidation reduces the count instead of increasing it")
    void consolidationReducesTheCount() {
        // The delta is negative for a reverse split. Taking abs() -- as the old code did for every
        // non-corporate type -- would have turned a halving into a doubling.
        txns.add(txn(TransactionType.SPLIT, "-50", earlier, "2"));

        assertThat(payout()).as("50 shares after consolidation").isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("a transaction after the record date does not affect the payout")
    void laterTransactionsAreIgnored() {
        // Buying the day after the record date earns nothing on that dividend, which is the entire
        // reason the quantity is replayed to a date rather than read off the holding.
        txns.add(txn(TransactionType.BUY, "500", recordDate.plusDays(1), null));

        assertThat(payout()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("a position sold out before the record date pays nothing at all")
    void soldOutPositionPaysNothing() {
        txns.add(txn(TransactionType.SELL, "100", earlier, null));

        service.calculateDividends(userId);

        verify(dividendRepository, org.mockito.Mockito.never()).save(any());
    }
}