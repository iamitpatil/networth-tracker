package com.networth.service.importservice;

import com.networth.model.dto.HoldingResponse;
import com.networth.model.entity.DematAccount;
import com.networth.model.entity.Holding;
import com.networth.model.entity.Symbol;
import com.networth.repository.DematAccountRepository;
import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolRepository;
import com.networth.service.SymbolValidator;
import com.networth.service.portfolio.CorporateActionService;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * A CSV with one real ticker and one invented one.
 *
 * <p>Verified live before validation existed: a file containing two garbage tickers reported
 * {@code imported: 2, failed: 0} and created two permanently unpriceable holdings. This is the same
 * file shape, and it must now report {@code imported: 1, failed: 1} with a reason on the bad row.
 *
 * <p>The other half of what is being tested is subtler and is the reason the check sits in
 * {@code importRow} rather than in {@code findOrCreateHolding}. {@code createHolding} is
 * {@code @Transactional} and joins the importer's transaction; a rejection thrown from inside it marks
 * that shared transaction rollback-only, so the importer would catch the exception, record a row error,
 * and then fail its own commit with {@code UnexpectedRollbackException}. One bad row would take the
 * whole file down — the opposite of per-row reporting.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionImportValidationTest {

    @Mock HoldingRepository holdingRepository;
    @Mock DematAccountRepository dematAccountRepository;
    @Mock HoldingService holdingService;
    @Mock TransactionService transactionService;
    @Mock CorporateActionService corporateActionService;
    @Mock SymbolRepository symbolRepository;

    private TransactionImportService service;

    private final UUID userId = UUID.randomUUID();
    private static final String HEADER =
            "symbol,assetType,transactionType,quantity,price,transactionDate,ratio,broker,notes\n";

    private final List<Holding> stored = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new TransactionImportService(holdingRepository, dematAccountRepository, holdingService,
                transactionService, corporateActionService, new SymbolValidator(symbolRepository));

        DematAccount demat = new DematAccount();
        demat.setId(UUID.randomUUID());
        demat.setUserId(userId);
        demat.setIsDefault(true);
        when(dematAccountRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(demat));

        stored.clear();
        when(holdingService.createHolding(anyString(), any())).thenAnswer(inv -> {
            com.networth.model.dto.HoldingRequest request = inv.getArgument(1);
            Holding h = new Holding();
            h.setId(UUID.randomUUID());
            h.setUserId(userId);
            h.setSymbol(request.getSymbol());
            h.setAssetType(request.getAssetType());
            stored.add(h);
            HoldingResponse r = new HoldingResponse();
            r.setId(h.getId().toString());
            return r;
        });
        when(holdingRepository.findById(any())).thenAnswer(inv -> stored.stream()
                .filter(h -> h.getId().equals(inv.getArgument(0)))
                .findFirst());
        when(holdingRepository.findByUserId(userId)).thenAnswer(inv -> List.copyOf(stored));

        // The reference list: INFY.NS is listed, everything else is not.
        when(symbolRepository.findById(anyString())).thenReturn(Optional.empty());
        when(symbolRepository.findById("INFY.NS")).thenReturn(Optional.of(Symbol.builder()
                .symbol("INFY.NS").name("Infosys").category("EQUITY").isin("INE009A01021").build()));
        when(symbolRepository.findByIsin(anyString())).thenReturn(List.of());
        when(symbolRepository.findBySchemeCode(anyString())).thenReturn(List.of());
        when(symbolRepository.countByCategory(anyString())).thenReturn(2_075L);
    }

    private TransactionImportService.ImportResult importCsv(String body) {
        return service.importTransactions(userId,
                new MockMultipartFile("file", "t.csv", "text/csv", body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("the bad row fails with a reason and the good row is still imported")
    void oneGoodRowOneBadRow() {
        TransactionImportService.ImportResult result = importCsv(HEADER
                + "INFY,EQUITY,BUY,10,1500,2026-01-15,,Zerodha,\n"
                + "FAKETICKER999,EQUITY,BUY,5,100,2026-01-16,,Zerodha,\n");

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getSymbol()).isEqualTo("FAKETICKER999");
        assertThat(result.getErrors().get(0).getReason())
                .contains("Unknown EQUITY symbol 'FAKETICKER999'")
                .contains("/api/v1/symbols/refresh");
        // Row 1 is the header, so the bad row is row 3.
        assertThat(result.getErrors().get(0).getRow()).isEqualTo(3);

        // The good row is persisted, and under the canonical key: INFY in the file, INFY.NS on the
        // holding, which is what price history is keyed by.
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getSymbol()).isEqualTo("INFY.NS");
        verify(transactionService, times(1)).addTransaction(anyString(), any());
    }

    @Test
    @DisplayName("a bad row does not roll back the good one")
    void aRejectedRowDoesNotAbortTheImport() {
        // The bad row comes first here on purpose: if the rejection poisoned the transaction, nothing
        // after it would be written and the result would report a failure for every row.
        TransactionImportService.ImportResult result = importCsv(HEADER
                + "NOTAREALSTOCK,EQUITY,BUY,5,100,2026-01-16,,Zerodha,\n"
                + "INFY,EQUITY,BUY,10,1500,2026-01-15,,Zerodha,\n");

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(stored).extracting(Holding::getSymbol).containsExactly("INFY.NS");
    }

    @Test
    @DisplayName("an ungated type is imported without consulting a list")
    void ungatedRowsStillImport() {
        TransactionImportService.ImportResult result = importCsv(HEADER
                + "SOMEGOLDBAR,GOLD,BUY,5,7000,2026-01-15,,,\n");

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getFailed()).isZero();
        assertThat(stored).extracting(Holding::getSymbol).containsExactly("SOMEGOLDBAR");
    }
}
