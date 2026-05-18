package com.networth.service.portfolio;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.dto.HoldingRequest;
import com.networth.model.dto.HoldingResponse;
import com.networth.model.entity.DematAccount;
import com.networth.model.entity.Holding;
import com.networth.repository.DematAccountRepository;
import com.networth.repository.HoldingRepository;
import com.networth.repository.MarketPriceRepository;
import com.networth.service.market.PriceService;
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

    @Transactional
    public HoldingResponse createHolding(String userId, HoldingRequest request) {
        UUID uid = UUID.fromString(userId);

        // Verify demat account ownership if provided
        if (request.getDematAccountId() != null) {
            UUID dematId = UUID.fromString(request.getDematAccountId());
            DematAccount demat = dematAccountRepository.findById(dematId)
                    .orElseThrow(() -> new ResourceNotFoundException("DematAccount", request.getDematAccountId()));
            if (!demat.getUserId().equals(uid)) {
                throw new AccessDeniedException("DematAccount", request.getDematAccountId());
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
                .isin(request.getIsin())
                .lockInUntil(request.getLockInUntil())
                .dematAccountId(request.getDematAccountId() != null ? UUID.fromString(request.getDematAccountId()) : null)
                .metadata(request.getMetadata())
                .build();

        holding = holdingRepository.save(holding);

        priceService.refreshPrice(holding.getSymbol(), holding.getAssetType());
        updateHoldingPrice(holding);

        return toResponse(holding, batchFetchDematAccounts(List.of(holding)));
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
            priceService.refreshPrice(holding.getSymbol(), holding.getAssetType());
            updateHoldingPrice(holding);
        }
    }

    private void updateHoldingPrice(Holding holding) {
        BigDecimal currentPrice = priceService.getCurrentPrice(holding.getSymbol(), holding.getAssetType());
        if (currentPrice != null) {
            holding.setCurrentPrice(currentPrice);
            holding.setCurrentValue(holding.getQuantity().multiply(currentPrice));
            BigDecimal costBasis = holding.getQuantity().multiply(holding.getAverageBuyPrice());
            holding.setUnrealizedPnl(holding.getCurrentValue().subtract(costBasis));

            BigDecimal prevClose = priceService.getPreviousClose(holding.getSymbol(), holding.getAssetType());
            if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = currentPrice.subtract(prevClose);
                holding.setDayChange(change);
                holding.setDayChangePct(change.divide(prevClose, 4, java.math.RoundingMode.HALF_UP).multiply(new BigDecimal("100")));
            }

            holdingRepository.save(holding);
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
                dematAccountNumber = da.getAccountNumber();
            }
        }
        return HoldingResponse.builder()
                .id(holding.getId().toString())
                .assetType(holding.getAssetType())
                .symbol(holding.getSymbol())
                .name(holding.getName())
                .quantity(holding.getQuantity())
                .averageBuyPrice(holding.getAverageBuyPrice())
                .currentPrice(holding.getCurrentPrice())
                .currentValue(holding.getCurrentValue())
                .realizedPnl(holding.getRealizedPnl())
                .unrealizedPnl(holding.getUnrealizedPnl())
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
}
