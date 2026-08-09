package com.networth.service.portfolio;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.dto.HoldingRequest;
import com.networth.model.dto.HoldingResponse;
import com.networth.model.entity.DematAccount;
import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.model.entity.Symbol;
import com.networth.repository.DematAccountRepository;
import com.networth.repository.HoldingRepository;
import com.networth.repository.MarketPriceRepository;
import com.networth.repository.SymbolRepository;
import com.networth.service.market.PriceService;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.TransactionType;
import com.networth.repository.TransactionRepository;
import com.networth.service.market.MarketCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HoldingService {

    private final HoldingRepository holdingRepository;
    private final MarketPriceRepository marketPriceRepository;
    private final PriceService priceService;
    private final DematAccountRepository dematAccountRepository;
    private final SymbolRepository symbolRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<HoldingResponse> getUserHoldings(String userId) {
        UUID uid = UUID.fromString(userId);
        List<Holding> holdings = holdingRepository.findByUserId(uid);
        // Batch-fetch demat accounts to avoid N+1
        Map<UUID, DematAccount> dematMap = batchFetchDematAccounts(holdings);
        return holdings.stream().map(h -> toResponse(h, dematMap)).toList();
    }

    @Transactional(readOnly = true)
    public List<Holding> getHoldingsBySymbol(String userId, String symbol) {
        return holdingRepository.findByUserIdAndSymbol(UUID.fromString(userId), symbol);
    }

    /**
     * Get a holding by ID, ensuring ownership.
     * @throws ResourceNotFoundException if holding doesn't exist or user doesn't own it
     */
    @Transactional(readOnly = true)
    public HoldingResponse getHolding(String userId, String holdingId) {
        Holding holding = findOwnedHolding(userId, holdingId);
        return toResponse(holding, batchFetchDematAccounts(List.of(holding)));
    }

    /**
     * Internal method to fetch a holding ensuring ownership.
     * Used by other services that need to verify holding ownership.
     */
    @Transactional(readOnly = true)
    public Holding findOwnedHolding(String userId, String holdingId) {
        UUID hid;
        UUID uid;
        try {
            hid = UUID.fromString(holdingId);
            uid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Holding", holdingId);
        }
        Holding holding = holdingRepository.findById(hid)
                .orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId));
        if (!holding.getUserId().equals(uid)) {
            log.warn("User {} attempted to access holding {} owned by {}", userId, holdingId, holding.getUserId());
            throw new AccessDeniedException("Holding", holdingId);
        }
        return holding;
    }

    /**
     * Asset types that REQUIRE a demat account (traded via broker).
     * Other types (PPF, EPF, FD, Real Estate, Cash) don't have demat accounts.
     */
    private static final java.util.Set<AssetType> ASSET_TYPES_REQUIRING_DEMAT = java.util.EnumSet.of(
            AssetType.EQUITY,
            AssetType.ETF,
            AssetType.MUTUAL_FUND
    );

    private boolean requiresDematAccount(AssetType assetType) {
        return ASSET_TYPES_REQUIRING_DEMAT.contains(assetType);
    }

    @Transactional
    public HoldingResponse createHolding(String userId, HoldingRequest request) {
        UUID uid = UUID.fromString(userId);

        // Validate: demat account is REQUIRED for tradeable assets
        if (requiresDematAccount(request.getAssetType())) {
            if (request.getDematAccountId() == null || request.getDematAccountId().isBlank()) {
                throw new IllegalArgumentException(
                        "Demat account is required for " + request.getAssetType() +
                        " holdings. Please select a demat account.");
            }
        }

        // Verify demat account ownership if provided
        if (request.getDematAccountId() != null && !request.getDematAccountId().isBlank()) {
            UUID dematId;
            try {
                dematId = UUID.fromString(request.getDematAccountId());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid demat account ID format");
            }
            DematAccount demat = dematAccountRepository.findById(dematId)
                    .orElseThrow(() -> new ResourceNotFoundException("DematAccount", request.getDematAccountId()));
            if (!demat.getUserId().equals(uid)) {
                throw new AccessDeniedException("DematAccount", request.getDematAccountId());
            }
        }

        // Auto-resolve ISIN from symbols table if not provided
        String isin = request.getIsin();
        if ((isin == null || isin.isBlank()) && request.getSymbol() != null) {
            isin = resolveIsin(request.getSymbol());
            if (isin == null && ASSET_TYPES_REQUIRING_DEMAT.contains(request.getAssetType())) {
                log.warn("Symbol '{}' not found in symbols table — ISIN could not be resolved", request.getSymbol());
            }
        }

        Holding holding = Holding.builder()
                .userId(uid)
                .assetType(request.getAssetType())
                .symbol(request.getSymbol())
                .name(request.getName())
                .quantity(request.getQuantity())
                .averageBuyPrice(request.getAverageBuyPrice())
                .currentPrice(request.getAverageBuyPrice())
                .currentValue(request.getQuantity().multiply(request.getAverageBuyPrice()))
                .realizedPnl(BigDecimal.ZERO)
                .unrealizedPnl(BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .exchange(request.getExchange())
                .sector(request.getSector())
                .isin(isin)
                .lockInUntil(request.getLockInUntil())
                .dematAccountId(request.getDematAccountId() != null ? UUID.fromString(request.getDematAccountId()) : null)
                .metadata(request.getMetadata())
                .build();

        holding = holdingRepository.save(holding);
        recordOpeningLot(holding, request.getPurchaseDate());

        String pricingSymbol = getEffectiveSymbolForPricing(holding);
        priceService.refreshPrice(pricingSymbol, holding.getAssetType());
        updateHoldingPrice(holding);

        return toResponse(holding, batchFetchDematAccounts(List.of(holding)));
    }

    /**
     * Records the position a holding is created with as an opening BUY transaction.
     *
     * <p>Without this a holding created through the UI has no transactions at all, so anything
     * that reasons from transaction history — FIFO cost basis, capital gains, holding period,
     * XIRR — either sees nothing or falls back to the row's creation date. Tax harvesting was
     * classifying long-held imported positions as short-term for exactly this reason.
     *
     * <p>Inserted directly rather than through {@code CostBasisService.updateBuy}, because the
     * holding's quantity and average price already reflect this lot; running it through the
     * cost-basis path would double the position.
     */
    private void recordOpeningLot(Holding holding, java.time.LocalDate purchaseDate) {
        if (holding.getQuantity() == null || holding.getQuantity().compareTo(BigDecimal.ZERO) <= 0
                || holding.getAverageBuyPrice() == null) {
            return;
        }
        java.time.LocalDateTime when = (purchaseDate != null ? purchaseDate : java.time.LocalDate.now(MarketCalendar.ZONE))
                .atStartOfDay();

        transactionRepository.save(Transaction.builder()
                .userId(holding.getUserId())
                .holdingId(holding.getId())
                .transactionType(TransactionType.BUY)
                .quantity(holding.getQuantity())
                .price(holding.getAverageBuyPrice())
                .amount(holding.getQuantity().multiply(holding.getAverageBuyPrice()))
                .transactionDate(when)
                .notes("Opening balance recorded when the holding was created")
                .build());
    }

    @Transactional
    public HoldingResponse updateHolding(String userId, String holdingId, HoldingRequest request) {
        Holding holding = findOwnedHolding(userId, holdingId);

        if (request.getName() != null) holding.setName(request.getName());
        if (request.getSector() != null) holding.setSector(request.getSector());
        if (request.getIsin() != null) holding.setIsin(request.getIsin());
        if (request.getLockInUntil() != null) holding.setLockInUntil(request.getLockInUntil());
        if (request.getMetadata() != null) holding.setMetadata(request.getMetadata());

        holding = holdingRepository.save(holding);
        return toResponse(holding, batchFetchDematAccounts(List.of(holding)));
    }

    @Transactional
    public void deleteHolding(String userId, String holdingId) {
        Holding holding = findOwnedHolding(userId, holdingId);
        // Soft delete: preserve for tax/audit history
        holding.setDeletedAt(java.time.LocalDateTime.now());
        holdingRepository.save(holding);
        log.info("Soft-deleted holding {} for user {}", holdingId, userId);
    }

    @Transactional
    public void updateAllHoldingPrices(String userId) {
        UUID uid = UUID.fromString(userId);
        List<Holding> holdings = holdingRepository.findByUserId(uid);
        for (Holding holding : holdings) {
            String pricingSymbol = getEffectiveSymbolForPricing(holding);
            priceService.refreshPrice(pricingSymbol, holding.getAssetType());
            updateHoldingPrice(holding);
        }
    }

    private void updateHoldingPrice(Holding holding) {
        String pricingSymbol = getEffectiveSymbolForPricing(holding);
        BigDecimal currentPrice = priceService.getCurrentPrice(pricingSymbol, holding.getAssetType());

        // Use fetched price if available, otherwise keep stored price (don't reset to 0)
        BigDecimal priceToUse = currentPrice != null ? currentPrice : holding.getCurrentPrice();

        if (priceToUse != null && priceToUse.compareTo(BigDecimal.ZERO) > 0) {
            holding.setCurrentPrice(priceToUse);
            // Always recompute currentValue from quantity × price
            // Fixes stale data where currentPrice exists but currentValue is 0
            holding.setCurrentValue(holding.getQuantity().multiply(priceToUse));

            BigDecimal costBasis = holding.getQuantity().multiply(holding.getAverageBuyPrice());
            holding.setUnrealizedPnl(holding.getCurrentValue().subtract(costBasis));

            // Only update day change if we successfully fetched a new price
            if (currentPrice != null) {
                BigDecimal prevClose = priceService.getPreviousClose(pricingSymbol, holding.getAssetType());
                if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal change = currentPrice.subtract(prevClose);
                    holding.setDayChange(change);
                    holding.setDayChangePct(change.divide(prevClose, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100")));
                }
            }

            holdingRepository.save(holding);
        } else {
            log.debug("Skipping price update for {} - no valid price available", holding.getSymbol());
        }
    }

    /**
     * Batch-fetch demat accounts for multiple holdings to avoid N+1 queries.
     */
    private Map<UUID, DematAccount> batchFetchDematAccounts(List<Holding> holdings) {
        List<UUID> dematIds = holdings.stream()
                .map(Holding::getDematAccountId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (dematIds.isEmpty()) return new HashMap<>();
        return dematAccountRepository.findAllById(dematIds).stream()
                .collect(Collectors.toMap(DematAccount::getId, da -> da));
    }

    private HoldingResponse toResponse(Holding holding, Map<UUID, DematAccount> dematMap) {
        String dematBroker = null;
        String dematAccountNumber = null;
        if (holding.getDematAccountId() != null) {
            DematAccount da = dematMap.get(holding.getDematAccountId());
            if (da != null) {
                dematBroker = da.getBrokerName();
                dematAccountNumber = maskAccountNumber(da.getAccountNumber());
            }
        }

        // Compute currentValue and PnL on-the-fly to handle stale stored values
        BigDecimal currentPrice = holding.getCurrentPrice();
        BigDecimal currentValue = holding.getCurrentValue();
        BigDecimal unrealizedPnl = holding.getUnrealizedPnl();

        if (currentPrice != null && currentPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal computedValue = holding.getQuantity().multiply(currentPrice);
            if (currentValue == null || currentValue.compareTo(BigDecimal.ZERO) <= 0) {
                currentValue = computedValue;
            }
            if (unrealizedPnl == null && holding.getAverageBuyPrice() != null) {
                BigDecimal costBasis = holding.getQuantity().multiply(holding.getAverageBuyPrice());
                unrealizedPnl = currentValue.subtract(costBasis);
            }
        }

        return HoldingResponse.builder()
                .id(holding.getId().toString())
                .assetType(holding.getAssetType())
                .symbol(holding.getSymbol())
                .name(holding.getName())
                .quantity(holding.getQuantity())
                .averageBuyPrice(holding.getAverageBuyPrice())
                .currentPrice(currentPrice)
                .currentValue(currentValue)
                .realizedPnl(holding.getRealizedPnl())
                .unrealizedPnl(unrealizedPnl)
                .dayChange(holding.getDayChange())
                .dayChangePct(holding.getDayChangePct())
                .currency(holding.getCurrency())
                .exchange(holding.getExchange())
                .sector(holding.getSector())
                .isin(holding.getIsin())
                .dematAccountId(holding.getDematAccountId() != null ? holding.getDematAccountId().toString() : null)
                .dematAccountBroker(dematBroker)
                .dematAccountNumber(dematAccountNumber)
                .createdAt(holding.getCreatedAt())
                .updatedAt(holding.getUpdatedAt())
                .build();
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    private String resolveIsin(String symbol) {
        return resolveIsin(symbol, null, null);
    }

    private String resolveIsin(String symbol, AssetType assetType) {
        return resolveIsin(symbol, assetType, null);
    }

    /**
     * Resolve ISIN for a holding symbol.
     * For equities: direct PK lookup (TCS.NS → ISIN from isin column).
     * For MFs: the symbols table stores ISIN as PK and scheme name as name,
     * so we need a fuzzy name search to find the ISIN.
     * @param holdingName optional — used as fallback for fuzzy search when symbol doesn't match
     */
    private String resolveIsin(String symbol, AssetType assetType, String holdingName) {
        // 1. Direct lookup by PK (works for equities; also works if symbol IS already an ISIN)
        var found = symbolRepository.findById(symbol);
        if (found.isPresent()) {
            Symbol sym = found.get();
            // For MF symbols, the PK itself is the ISIN
            if ("MUTUAL_FUND".equals(sym.getCategory())) {
                return sym.getSymbol(); // PK = ISIN for MFs
            }
            if (sym.getIsin() != null && !sym.getIsin().isBlank()) {
                return sym.getIsin();
            }
        }

        // 2. Try with .NS suffix (equities: "TCS" → "TCS.NS")
        if (!symbol.endsWith(".NS")) {
            var nsFound = symbolRepository.findById(symbol + ".NS");
            if (nsFound.isPresent() && nsFound.get().getIsin() != null && !nsFound.get().getIsin().isBlank()) {
                return nsFound.get().getIsin();
            }
        }

        // 3. For MFs: fuzzy name search (symbol is a scheme name like "Axis Bluechip Fund Direct Growth")
        if (assetType == AssetType.MUTUAL_FUND || (symbol.length() > 15 && !symbol.contains("."))) {
            // Try searching by symbol first, then by holdingName as fallback
            String[] searchTerms = holdingName != null && !holdingName.equals(symbol)
                    ? new String[]{symbol, holdingName}
                    : new String[]{symbol};

            for (String term : searchTerms) {
                String keyword = term.split("\\s*[-–]\\s*(Direct|Regular|Growth|IDCW|Plan|Dividend)")[0].trim();
                if (keyword.length() > 5) {
                    var matches = symbolRepository.searchByCategoryAndName("MUTUAL_FUND", keyword);
                    // Prefer Direct Growth variant
                    var match = matches.stream()
                            .filter(s -> s.getName().toLowerCase().contains("direct") &&
                                    s.getName().toLowerCase().contains("growth"))
                            .findFirst()
                            .or(() -> matches.stream().filter(s -> s.getName().toLowerCase().contains("direct")).findFirst())
                            .or(() -> matches.stream().findFirst());
                    if (match.isPresent()) {
                        return match.get().getSymbol(); // PK = ISIN for MFs
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get the effective symbol to use for price/NAV lookups.
     * For equities: returns the symbol (e.g. "TCS.NS")
     * For MFs: returns the ISIN (e.g. "INF846K01EW2") which is what AMFI/Upstox use.
     */
    public String getEffectiveSymbolForPricing(Holding holding) {
        if (holding.getAssetType() == AssetType.MUTUAL_FUND) {
            // Use ISIN for MF price lookups
            if (holding.getIsin() != null && !holding.getIsin().isBlank()) {
                return holding.getIsin();
            }
            // Try to resolve it — pass both symbol and name for better fuzzy matching
            String isin = resolveIsin(holding.getSymbol(), AssetType.MUTUAL_FUND, holding.getName());
            if (isin != null) {
                // Persist for future lookups
                holding.setIsin(isin);
                holdingRepository.save(holding);
                return isin;
            }
        }
        return holding.getSymbol();
    }

    /**
     * Backfill ISIN for all existing holdings that are missing it.
     * Called on startup or on-demand.
     */
    @Transactional
    public int backfillMissingIsins() {
        List<Holding> holdings = holdingRepository.findAll();
        int fixed = 0;
        for (Holding h : holdings) {
            if ((h.getIsin() == null || h.getIsin().isBlank()) && h.getSymbol() != null) {
                String isin = resolveIsin(h.getSymbol(), h.getAssetType(), h.getName());
                if (isin != null) {
                    h.setIsin(isin);
                    holdingRepository.save(h);
                    fixed++;
                    log.info("Backfilled ISIN for {}: {}", h.getSymbol(), isin);
                }
            }
        }
        if (fixed > 0) log.info("Backfilled ISIN for {} holdings", fixed);
        return fixed;
    }
}
