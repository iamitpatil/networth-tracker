package com.networth.controller;

import com.networth.model.dto.*;
import com.networth.service.FamilyDataService;
import com.networth.service.FamilyService;
import com.networth.service.InvestmentOverTimeService;
import com.networth.service.portfolio.CorporateActionService;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.PortfolioSummaryService;
import com.networth.service.importservice.TransactionImportService;
import com.networth.service.portfolio.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.StockPriceHistoryRepository;
import com.networth.repository.SymbolRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final HoldingService holdingService;
    private final TransactionService transactionService;
    private final CorporateActionService corporateActionService;
    private final TransactionImportService transactionImportService;
    private final PortfolioSummaryService portfolioSummaryService;
    private final FamilyDataService familyDataService;
    private final FamilyService familyService;
    private final InvestmentOverTimeService investmentOverTimeService;
    private final HoldingRepository holdingRepository;
    private final StockPriceHistoryRepository stockPriceHistoryRepository;
    private final SymbolRepository symbolRepository;

    private boolean isFam(Map<String, String> params) {
        return "f".equals(params.getOrDefault("view", ""));
    }

    @GetMapping("/holdings")
    public ResponseEntity<List<HoldingResponse>> getHoldings(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Map<String, String> params) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(familyDataService.getHoldings(uid, isFam(params)));
    }

    @PostMapping("/holdings")
    public ResponseEntity<HoldingResponse> createHolding(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody HoldingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(holdingService.createHolding(userDetails.getUsername(), request));
    }

    @GetMapping("/holdings/{id}")
    public ResponseEntity<HoldingResponse> getHolding(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(holdingService.getHolding(userDetails.getUsername(), id));
    }

    @PutMapping("/holdings/{id}")
    public ResponseEntity<HoldingResponse> updateHolding(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody HoldingRequest request) {
        return ResponseEntity.ok(holdingService.updateHolding(userDetails.getUsername(), id, request));
    }

    @DeleteMapping("/holdings/{id}")
    public ResponseEntity<Void> deleteHolding(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        holdingService.deleteHolding(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh-prices")
    public ResponseEntity<Void> refreshPrices(@AuthenticationPrincipal UserDetails userDetails) {
        holdingService.updateAllHoldingPrices(userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getAllTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Map<String, String> params) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(familyDataService.getTransactions(uid, isFam(params)));
    }

    @GetMapping("/holdings/{id}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        return ResponseEntity.ok(transactionService.getHoldingTransactions(userDetails.getUsername(), id));
    }

    /**
     * Bulk transaction import from a CSV.
     *
     * <p>Rows are independent: valid ones are imported and failures come back with their row
     * number and reason, rather than one bad date costing the whole file.
     */
    @PostMapping("/transactions/import")
    public ResponseEntity<Map<String, Object>> importTransactions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choose a CSV file to import"));
        }
        return ResponseEntity.ok(transactionImportService
                .importTransactions(UUID.fromString(userDetails.getUsername()), file)
                .toMap());
    }

    /** The CSV template, so the expected columns are never guesswork. */
    @GetMapping("/transactions/import/sample")
    public ResponseEntity<String> sampleTransactionCsv() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions-sample.csv\"")
                .body(transactionImportService.sampleCsv());
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> addTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.addTransaction(userDetails.getUsername(), request));
    }

    /**
     * Applies a bonus issue, split or demerger to a holding.
     *
     * <p>Separate from {@code POST /transactions} because these are not trades: a bonus share
     * has no price, a split has no quantity of its own, and a demerger writes to two holdings
     * at once. The response reports the position before and after plus a plain-language summary,
     * so the user can check the maths did what they expected.
     */
    @PostMapping("/holdings/{id}/corporate-actions")
    public ResponseEntity<Map<String, Object>> applyCorporateAction(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @Valid @RequestBody CorporateActionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(corporateActionService.apply(userDetails.getUsername(), id, request));
    }

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummary> getSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Map<String, String> params) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(familyDataService.getSummary(uid, isFam(params)));
    }

    /**
     * Daily price history for a holding's symbol.
     *
     * <p>The series itself is public market data, but the holding is not: without a check this
     * answered for any holding UUID, so it confirmed whether a given holding existed and what
     * asset class it was. Access is allowed to the owner and to approved family members, which is
     * what the family view needs — it renders other members' holdings.
     */
    @GetMapping("/holdings/{id}/price-history")
    public ResponseEntity<List<Map<String, Object>>> getPriceHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestParam(defaultValue = "90") int days) {
        com.networth.model.entity.Holding holding = holdingRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new com.networth.exception.ResourceNotFoundException("Holding", id));

        UUID viewerId = UUID.fromString(userDetails.getUsername());
        if (!holding.getUserId().equals(viewerId)
                && !familyService.getApprovedMemberIds(viewerId).contains(holding.getUserId())) {
            // Same exception as a missing holding, deliberately: a distinct "forbidden" would
            // still confirm the holding exists, which is the leak being closed.
            throw new com.networth.exception.ResourceNotFoundException("Holding", id);
        }
        if (holding.getAssetType() != AssetType.EQUITY && holding.getAssetType() != AssetType.ETF
                && holding.getAssetType() != AssetType.MUTUAL_FUND) {
            return ResponseEntity.ok(List.of());
        }
        String symbol = holdingService.getEffectiveSymbolForPricing(holding);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);
        List<Map<String, Object>> result = stockPriceHistoryRepository
                .findBySymbolAndPriceDateBetweenOrderByPriceDate(symbol, from, to)
                .stream().map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", h.getPriceDate().toString());
                    m.put("open", h.getOpen());
                    m.put("high", h.getHigh());
                    m.put("low", h.getLow());
                    m.put("close", h.getClose());
                    m.put("volume", h.getVolume());
                    return m;
                }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/investment-over-time")
    public ResponseEntity<List<Map<String, Object>>> getInvestmentOverTime(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "365") int days,
            @RequestParam(required = false) AssetType assetType) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(investmentOverTimeService.getInvestmentOverTime(uid, days, assetType));
    }
}
