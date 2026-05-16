package com.networth.controller;

import com.networth.model.dto.*;
import com.networth.service.FamilyDataService;
import com.networth.service.InvestmentOverTimeService;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.PortfolioSummaryService;
import com.networth.service.portfolio.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.networth.model.enums.AssetType;
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
    private final PortfolioSummaryService portfolioSummaryService;
    private final FamilyDataService familyDataService;
    private final InvestmentOverTimeService investmentOverTimeService;
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
    public ResponseEntity<HoldingResponse> getHolding(@PathVariable String id) {
        return ResponseEntity.ok(holdingService.getHolding(id));
    }

    @PutMapping("/holdings/{id}")
    public ResponseEntity<HoldingResponse> updateHolding(
            @PathVariable String id,
            @Valid @RequestBody HoldingRequest request) {
        return ResponseEntity.ok(holdingService.updateHolding(id, request));
    }

    @DeleteMapping("/holdings/{id}")
    public ResponseEntity<Void> deleteHolding(@PathVariable String id) {
        holdingService.deleteHolding(id);
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
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable String id) {
        return ResponseEntity.ok(transactionService.getHoldingTransactions(id));
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> addTransaction(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.addTransaction(userDetails.getUsername(), request));
    }

    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummary> getSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Map<String, String> params) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(familyDataService.getSummary(uid, isFam(params)));
    }

    @GetMapping("/holdings/{id}/price-history")
    public ResponseEntity<List<Map<String, Object>>> getPriceHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "90") int days) {
        com.networth.model.dto.HoldingResponse holding = holdingService.getHolding(id);
        String symbol = holding.getSymbol();
        if (holding.getAssetType().name().equals("MUTUAL_FUND")) {
            String isin = holding.getIsin();
            if (isin != null && !isin.isBlank()) {
                symbol = isin;
            } else if (holding.getSymbol() != null && holding.getSymbol().length() == 12) {
                symbol = holding.getSymbol();
            } else {
                var found = symbolRepository.findById(holding.getSymbol());
                if (found.isPresent()) {
                    symbol = found.get().getSymbol();
                }
            }
        } else if (!"EQUITY".equals(holding.getAssetType().name()) && !"ETF".equals(holding.getAssetType().name())) {
            return ResponseEntity.ok(List.of());
        }
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
