package com.networth.service.importservice;

import com.networth.model.dto.HoldingResponse;
import com.networth.model.dto.TransactionRequest;
import com.networth.model.entity.DematAccount;
import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.DematAccountRepository;
import com.networth.repository.HoldingRepository;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionImportServiceTest {

    @Mock HoldingRepository holdingRepository;
    @Mock DematAccountRepository dematAccountRepository;
    @Mock HoldingService holdingService;
    @Mock TransactionService transactionService;
    @InjectMocks TransactionImportService service;

    private final UUID userId = UUID.randomUUID();
    private static final String HEADER = "symbol,assetType,transactionType,quantity,price,transactionDate,broker,notes\n";

    @BeforeEach
    void setUp() {
        DematAccount demat = new DematAccount();
        demat.setId(UUID.randomUUID());
        demat.setUserId(userId);
        demat.setIsDefault(true);
        when(dematAccountRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(demat));

        // createHolding returns a response; the service then reads the holding back by id
        when(holdingService.createHolding(anyString(), any())).thenAnswer(inv -> {
            HoldingResponse r = new HoldingResponse();
            r.setId(UUID.randomUUID().toString());
            return r;
        });
        when(holdingRepository.findById(any())).thenAnswer(inv -> {
            Holding h = new Holding();
            h.setId(inv.getArgument(0));
            h.setUserId(userId);
            return Optional.of(h);
        });
        when(holdingRepository.findByUserId(userId)).thenReturn(List.of());
    }

    private TransactionImportService.ImportResult importCsv(String body) {
        return service.importTransactions(userId,
                new MockMultipartFile("file", "t.csv", "text/csv", body.getBytes(StandardCharsets.UTF_8)));
    }

    // ── happy path ────────────────────────────────────────────────────

    @Test
    @DisplayName("each row becomes a transaction")
    void rowsBecomeTransactions() {
        var result = importCsv(HEADER
                + "RELIANCE,EQUITY,BUY,10,2850.50,2025-04-01,Zerodha,note\n"
                + "RELIANCE,EQUITY,SELL,4,2990,2025-06-01,Zerodha,\n");

        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getFailed()).isZero();

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService, times(2)).addTransaction(eq(userId.toString()), captor.capture());
        assertThat(captor.getAllValues().get(0).getTransactionType()).isEqualTo(TransactionType.BUY);
        assertThat(captor.getAllValues().get(0).getQuantity()).isEqualByComparingTo("10");
        assertThat(captor.getAllValues().get(0).getPrice()).isEqualByComparingTo("2850.50");
        assertThat(captor.getAllValues().get(1).getTransactionType()).isEqualTo(TransactionType.SELL);
    }

    @Test
    @DisplayName("a new symbol creates its holding at zero quantity, not the row's quantity")
    void newHoldingStartsEmpty() {
        // Creating it with the row's quantity would record an opening lot on top of the very
        // transaction being imported, leaving the ledger at double the position.
        importCsv(HEADER + "NEWCO,EQUITY,BUY,10,100,2025-04-01,,\n");

        ArgumentCaptor<com.networth.model.dto.HoldingRequest> captor =
                ArgumentCaptor.forClass(com.networth.model.dto.HoldingRequest.class);
        verify(holdingService).createHolding(eq(userId.toString()), captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getAverageBuyPrice()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("an existing holding is reused rather than duplicated")
    void existingHoldingReused() {
        Holding existing = new Holding();
        existing.setId(UUID.randomUUID());
        existing.setUserId(userId);
        existing.setSymbol("RELIANCE");
        existing.setAssetType(AssetType.EQUITY);
        when(holdingRepository.findByUserId(userId)).thenReturn(List.of(existing));

        var result = importCsv(HEADER
                + "RELIANCE,EQUITY,BUY,10,100,2025-04-01,,\n"
                + "reliance,EQUITY,BUY,5,110,2025-05-01,,\n");   // case-insensitive match

        assertThat(result.getImported()).isEqualTo(2);
        verify(holdingService, never()).createHolding(anyString(), any());
        assertThat(result.getCreatedHoldings()).isEmpty();
    }

    // ── partial success ───────────────────────────────────────────────

    @Test
    @DisplayName("a bad row is reported with its number and reason; the rest still import")
    void badRowDoesNotStopTheFile() {
        var result = importCsv(HEADER
                + "GOOD1,GOLD,BUY,1,100,2025-04-01,,\n"
                + "BAD,GOLD,BUY,1,100,not-a-date,,\n"
                + "GOOD2,GOLD,BUY,1,100,2025-04-02,,\n");

        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getRow()).isEqualTo(3);       // header is row 1
        assertThat(result.getErrors().get(0).getSymbol()).isEqualTo("BAD");
        assertThat(result.getErrors().get(0).getReason()).contains("not a recognised date");
    }

    @Test
    @DisplayName("unknown enums and non-numbers are named in the error, with the valid options")
    void invalidValuesExplained() {
        var result = importCsv(HEADER
                + "A,NOTATYPE,BUY,1,100,2025-04-01,,\n"
                + "B,GOLD,GIFTED,1,100,2025-04-01,,\n"
                + "C,GOLD,BUY,abc,100,2025-04-01,,\n"
                + "D,GOLD,BUY,0,100,2025-04-01,,\n");

        assertThat(result.getImported()).isZero();
        assertThat(result.getFailed()).isEqualTo(4);
        assertThat(result.getErrors().get(0).getReason()).contains("assetType", "not valid");
        assertThat(result.getErrors().get(1).getReason()).contains("transactionType");
        assertThat(result.getErrors().get(2).getReason()).contains("quantity", "not a number");
        assertThat(result.getErrors().get(3).getReason()).contains("greater than zero");
    }

    @Test
    @DisplayName("a missing required value is reported rather than silently defaulted")
    void missingValueReported() {
        var result = importCsv(HEADER + ",GOLD,BUY,1,100,2025-04-01,,\n");
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getReason()).contains("symbol is required");
    }

    // ── file-level problems ───────────────────────────────────────────

    @Test
    @DisplayName("a file missing required columns is rejected with the names")
    void missingColumnsRejected() {
        assertThatThrownBy(() -> importCsv("symbol,quantity\nRELIANCE,10\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assetType")
                .hasMessageContaining("transactionDate");
    }

    @Test
    @DisplayName("an empty file is rejected clearly")
    void emptyFileRejected() {
        assertThatThrownBy(() -> importCsv(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    // ── tolerance of real-world files ─────────────────────────────────

    @Test
    @DisplayName("headers survive different case, spacing and an Excel byte-order mark")
    void headerVariationsTolerated() {
        var result = importCsv("﻿Symbol, Asset Type ,TRANSACTION_TYPE,Quantity,Price,Transaction Date\n"
                + "GOLDX,GOLD,BUY,1,100,2025-04-01\n");
        assertThat(result.getImported()).isEqualTo(1);
    }

    @Test
    @DisplayName("amounts pasted from a statement keep their meaning")
    void formattedAmountsParsed() {
        var result = importCsv(HEADER + "GOLDX,GOLD,BUY,2,\"1,250.75\",2025-04-01,,\n");
        assertThat(result.getImported()).isEqualTo(1);

        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).addTransaction(anyString(), captor.capture());
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("1250.75");
    }

    @Test
    @DisplayName("common date formats are accepted, not just ISO")
    void dateFormatsAccepted() {
        var result = importCsv(HEADER
                + "A,GOLD,BUY,1,100,2025-04-01,,\n"
                + "B,GOLD,BUY,1,100,01-04-2025,,\n"
                + "C,GOLD,BUY,1,100,01/04/2025,,\n"
                + "D,GOLD,BUY,1,100,01-Apr-2025,,\n");
        assertThat(result.getImported()).isEqualTo(4);
        assertThat(result.getFailed()).isZero();
    }

    @Test
    @DisplayName("blank lines are skipped rather than counted as failures")
    void blankLinesSkipped() {
        var result = importCsv(HEADER + "A,GOLD,BUY,1,100,2025-04-01,,\n\n\nB,GOLD,BUY,1,100,2025-04-02,,\n");
        assertThat(result.getImported()).isEqualTo(2);
        assertThat(result.getFailed()).isZero();
    }

    @Test
    @DisplayName("equity import without any demat account explains what to do")
    void missingDematExplained() {
        when(dematAccountRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        var result = importCsv(HEADER + "RELIANCE,EQUITY,BUY,1,100,2025-04-01,,\n");
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getReason()).contains("demat account");
    }

    // ── the sample ────────────────────────────────────────────────────

    @Test
    @DisplayName("the sample template is importable as-is")
    void sampleIsValid() {
        Holding reliance = new Holding();
        reliance.setId(UUID.randomUUID());
        reliance.setUserId(userId);
        reliance.setSymbol("RELIANCE");
        reliance.setAssetType(AssetType.EQUITY);

        String sample = service.sampleCsv();
        assertThat(sample.lines().findFirst().orElseThrow())
                .isEqualTo(String.join(",", TransactionImportService.COLUMNS));

        var result = importCsv(sample);
        assertThat(result.getFailed()).as("sample rows: %s", result.getErrors()).isZero();
        assertThat(result.getImported()).isEqualTo(4);
    }
}
