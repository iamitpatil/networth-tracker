package com.networth.service.portfolio;

import com.networth.model.dto.HoldingRequest;
import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.*;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The opening lot recorded when a holding is created.
 *
 * <p>Exists because this is easy to get wrong in two opposite directions: recording nothing
 * leaves FIFO cost basis and holding period with no history to work from, while recording it
 * twice inflates the ledger above the position the holding reports.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldingServiceOpeningLotTest {

    @Mock HoldingRepository holdingRepository;
    @Mock MarketPriceRepository marketPriceRepository;
    @Mock PriceService priceService;
    @Mock DematAccountRepository dematAccountRepository;
    @Mock SymbolRepository symbolRepository;
    @Mock TransactionRepository transactionRepository;

    /**
     * The real policy, not a mock: it is pure and calendar-driven, and every response this service
     * builds now asks it whether the price it carries is stale. A mock would answer "not priceable"
     * to everything and quietly stop exercising the branch.
     *
     * <p>The validator is real for a related reason. These tests use {@code GOLD}, which is not a gated
     * asset type, so the real validator passes the symbol straight through without touching the
     * repository — while a mock would return null from {@code requireKnown} and fail on the resolution
     * rather than on anything this test is about.
     */
    private HoldingService holdingService;

    @BeforeEach
    void setUp() {
        holdingService = new HoldingService(holdingRepository, marketPriceRepository, priceService,
                dematAccountRepository, transactionRepository,
                new PriceFreshnessPolicy(new MarketCalendar("Asia/Kolkata")),
                new com.networth.service.SymbolValidator(symbolRepository));
    }

    private final UUID userId = UUID.randomUUID();

    private HoldingRequest request(String qty, String price, LocalDate purchaseDate) {
        return HoldingRequest.builder()
                .assetType(AssetType.GOLD)          // no demat account required
                .symbol("TESTGOLD")
                .quantity(new BigDecimal(qty))
                .averageBuyPrice(new BigDecimal(price))
                .purchaseDate(purchaseDate)
                .build();
    }

    private void holdingSavesWithId() {
        when(holdingRepository.save(any(Holding.class))).thenAnswer(inv -> {
            Holding h = inv.getArgument(0);
            if (h.getId() == null) h.setId(UUID.randomUUID());
            return h;
        });
    }

    @Test
    @DisplayName("an opening BUY is recorded, dated from the supplied purchase date")
    void openingLotRecorded() {
        holdingSavesWithId();
        holdingService.createHolding(userId.toString(), request("10", "5000", LocalDate.of(2021, 3, 15)));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction txn = captor.getValue();

        assertThat(txn.getTransactionType()).isEqualTo(TransactionType.BUY);
        assertThat(txn.getQuantity()).isEqualByComparingTo("10");
        assertThat(txn.getPrice()).isEqualByComparingTo("5000");
        assertThat(txn.getAmount()).isEqualByComparingTo("50000");
        assertThat(txn.getTransactionDate().toLocalDate()).isEqualTo(LocalDate.of(2021, 3, 15));
        assertThat(txn.getNotes()).contains("Opening balance");
    }

    @Test
    @DisplayName("no purchase date falls back to today rather than leaving it unset")
    void defaultsToToday() {
        holdingSavesWithId();
        holdingService.createHolding(userId.toString(), request("5", "100", null));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getTransactionDate().toLocalDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("a zero-quantity holding records nothing, so the web form is unaffected")
    void zeroQuantityRecordsNothing() {
        // The Add Holding page creates the holding with quantity 0 and posts the transaction
        // separately. Recording an opening lot here would double the position.
        holdingSavesWithId();
        holdingService.createHolding(userId.toString(), request("0", "0", null));

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("the ledger total matches the holding quantity - exactly one lot, not two")
    void ledgerMatchesPosition() {
        holdingSavesWithId();
        holdingService.createHolding(userId.toString(), request("25", "400", LocalDate.now().minusDays(30)));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(1)).save(captor.capture());
        List<Transaction> written = captor.getAllValues();

        BigDecimal ledgerQty = written.stream()
                .map(Transaction::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(ledgerQty).as("ledger must equal the position, not double it")
                .isEqualByComparingTo("25");
    }
}
