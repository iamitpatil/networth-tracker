package com.networth.service.portfolio;

import com.networth.model.dto.CorporateActionRequest;
import com.networth.model.entity.Holding;
import com.networth.model.entity.Symbol;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolRepository;
import com.networth.repository.TransactionRepository;
import com.networth.service.market.PriceService;
import com.networth.service.market.MarketCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies bonus issues, splits and demergers to a holding.
 *
 * <p>Each action is written to the ledger as real transactions, then the position is adjusted.
 * Nothing is recorded only as a position change, because everything downstream — capital gains,
 * holding period, XIRR — reads the ledger, not the position. An action applied silently to
 * {@code holdings.quantity} would make the position and the tax report disagree.
 *
 * <h2>Why the cost treatment differs per action</h2>
 * <ul>
 *   <li><b>Bonus</b> — s.55(2)(aa)(iiia): the cost of a bonus share is <b>nil</b>. Selling one
 *       is therefore all gain. Its holding period starts at allotment, not at the original
 *       purchase, so bonus shares sold soon after allotment are short-term.</li>
 *   <li><b>Split</b> — no cost is created or destroyed. The same money now buys more (or fewer)
 *       shares, so per-share cost is rescaled and the holding period is untouched: a split does
 *       not restart the clock.</li>
 *   <li><b>Demerger</b> — s.49(2C)/(2D): the original cost is <b>apportioned</b> between the
 *       parent and the resulting company by their net asset values. The new shares are not free.
 *       Under s.2(42A)(g) they inherit the period the original shares were held, so they can be
 *       long-term on day one.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorporateActionService {

    /** Actions only make sense for share-like assets that can be split or spun off. */
    private static final Set<AssetType> SUPPORTED_ASSETS =
            Set.of(AssetType.EQUITY, AssetType.ETF, AssetType.MUTUAL_FUND);

    /** Transaction types that create a lot, and so carry an acquisition date to inherit. */
    private static final Set<TransactionType> ACQUISITIONS = Set.of(
            TransactionType.BUY, TransactionType.SIP, TransactionType.LUMPSUM,
            TransactionType.BONUS, TransactionType.DEMERGER_IN, TransactionType.TRANSFER_IN);

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final HoldingService holdingService;
    private final CostBasisService costBasisService;
    private final SymbolRepository symbolRepository;
    private final PriceService priceService;

    @Transactional
    public Map<String, Object> apply(String userId, String holdingId, CorporateActionRequest request) {
        Holding holding = holdingService.findOwnedHolding(userId, holdingId);

        if (!SUPPORTED_ASSETS.contains(holding.getAssetType())) {
            throw new IllegalArgumentException("Corporate actions apply to shares, ETFs and mutual "
                    + "funds. " + holding.getSymbol() + " is a " + holding.getAssetType() + " holding.");
        }
        if (holding.getQuantity() == null || holding.getQuantity().signum() <= 0) {
            throw new IllegalArgumentException("You hold no " + holding.getSymbol()
                    + " shares, so there is nothing for this action to apply to.");
        }
        if (request.getActionDate().isAfter(LocalDate.now(MarketCalendar.ZONE))) {
            throw new IllegalArgumentException("Action date cannot be in the future.");
        }

        return switch (request.getType()) {
            case BONUS -> applyBonus(holding, request);
            case SPLIT -> applySplit(holding, request);
            case DEMERGER -> applyDemerger(holding, request);
        };
    }

    // ── bonus ─────────────────────────────────────────────────────────

    private Map<String, Object> applyBonus(Holding holding, CorporateActionRequest request) {
        BigDecimal received = positive(request.getSharesReceived(), "Bonus shares received");
        BigDecimal held = request.getSharesHeld();

        BigDecimal quantityBefore = holding.getQuantity();
        BigDecimal averageBefore = holding.getAverageBuyPrice();

        BigDecimal bonusQuantity;
        String description;
        if (held == null) {
            // No ratio given, so sharesReceived is the absolute allotment. This is the form a
            // demat statement uses -- "BONUS 100" -- and what a CSV import supplies.
            bonusQuantity = received;
            description = strip(received) + " bonus shares allotted";
        } else {
            positive(held, "Shares held in the bonus ratio");
            // Truncated, not rounded: a 1:3 bonus on 100 shares gives 33 shares, not 33.33. No
            // registrar issues fractional shares.
            bonusQuantity = quantityBefore.multiply(received).divide(held, 0, RoundingMode.DOWN);
            description = String.format("%s:%s bonus issue", strip(received), strip(held));
            if (bonusQuantity.signum() <= 0) {
                throw new IllegalArgumentException(String.format(
                        "A %s:%s bonus on %s shares works out to less than one share, so there is "
                                + "nothing to record.", strip(received), strip(held), strip(quantityBefore)));
            }
        }

        transactionRepository.save(Transaction.builder()
                .userId(holding.getUserId())
                .holdingId(holding.getId())
                .transactionType(TransactionType.BONUS)
                .quantity(bonusQuantity)
                .price(BigDecimal.ZERO)     // s.55(2)(aa): nil cost of acquisition
                .amount(BigDecimal.ZERO)
                .transactionDate(request.getActionDate().atStartOfDay())
                .notes(note(request, description))
                .build());

        costBasisService.applyBonus(holding, bonusQuantity);
        holdingRepository.save(holding);

        Map<String, Object> result = summary("BONUS", holding, quantityBefore, averageBefore);
        result.put("sharesAdded", bonusQuantity);
        result.put("summary", String.format(
                "%s bonus shares added at nil cost. Average price falls from %s to %s because the "
                        + "same total cost now covers %s shares.",
                strip(bonusQuantity), money(averageBefore), money(holding.getAverageBuyPrice()),
                strip(holding.getQuantity())));
        return result;
    }

    // ── split ─────────────────────────────────────────────────────────

    private Map<String, Object> applySplit(Holding holding, CorporateActionRequest request) {
        BigDecimal from = positive(request.getFromQuantity(), "Shares before the split");
        BigDecimal to = positive(request.getToQuantity(), "Shares after the split");
        if (from.compareTo(to) == 0) {
            throw new IllegalArgumentException("A split from " + strip(from) + " to " + strip(to)
                    + " changes nothing.");
        }

        BigDecimal quantityBefore = holding.getQuantity();
        BigDecimal averageBefore = holding.getAverageBuyPrice();

        // The per-share cost multiplier. 1 → 2 gives 0.5: each share now costs half as much and
        // you hold twice as many, leaving the total cost identical.
        BigDecimal costFactor = from.divide(to, 10, RoundingMode.HALF_UP);

        costBasisService.applySplit(holding, costFactor);
        holdingRepository.save(holding);

        BigDecimal quantityDelta = holding.getQuantity().subtract(quantityBefore);

        transactionRepository.save(Transaction.builder()
                .userId(holding.getUserId())
                .holdingId(holding.getId())
                .transactionType(TransactionType.SPLIT)
                // The change in share count, so the ledger reads as "+100 shares". Positive for
                // a sub-division, negative for a consolidation. The gains calculator ignores
                // this and uses adjustmentFactor, which is the authoritative figure.
                .quantity(quantityDelta)
                .price(BigDecimal.ZERO)
                .amount(BigDecimal.ZERO)
                .adjustmentFactor(costFactor)
                .transactionDate(request.getActionDate().atStartOfDay())
                .notes(note(request, String.format("%s:%s %s", strip(from), strip(to),
                        to.compareTo(from) > 0 ? "share split" : "share consolidation")))
                .build());

        Map<String, Object> result = summary("SPLIT", holding, quantityBefore, averageBefore);
        result.put("costFactor", costFactor.stripTrailingZeros());
        result.put("summary", String.format(
                "%s shares became %s. Average price moves from %s to %s, so your total cost is "
                        + "unchanged and your holding period is not reset.",
                strip(quantityBefore), strip(holding.getQuantity()),
                money(averageBefore), money(holding.getAverageBuyPrice())));
        return result;
    }

    // ── demerger ──────────────────────────────────────────────────────

    private Map<String, Object> applyDemerger(Holding holding, CorporateActionRequest request) {
        String resultingSymbol = required(request.getResultingSymbol(), "Resulting company symbol");
        BigDecimal resultingQuantity = positive(request.getResultingQuantity(), "Shares received");
        BigDecimal apportionPercent = request.getCostApportionmentPercent();
        if (apportionPercent == null || apportionPercent.signum() < 0
                || apportionPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Cost apportionment must be between 0 and 100 percent. "
                    + "The demerging company publishes this figure — it is the resulting company's "
                    + "share of the pre-demerger net asset value.");
        }
        if (resultingSymbol.equalsIgnoreCase(holding.getSymbol())) {
            throw new IllegalArgumentException("The resulting company must differ from "
                    + holding.getSymbol() + ".");
        }

        BigDecimal quantityBefore = holding.getQuantity();
        BigDecimal averageBefore = holding.getAverageBuyPrice();
        BigDecimal totalCostBefore = quantityBefore.multiply(averageBefore);

        BigDecimal apportionedFraction = apportionPercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal retainedFraction = BigDecimal.ONE.subtract(apportionedFraction);
        BigDecimal apportionedCost = totalCostBefore.multiply(apportionedFraction);
        BigDecimal resultingCostPerShare = apportionedCost.divide(resultingQuantity, 4, RoundingMode.HALF_UP);

        // s.2(42A)(g): the new shares inherit the period the original shares were held.
        LocalDateTime inheritedAcquisition = earliestAcquisition(holding)
                .orElse(request.getActionDate().atStartOfDay());

        // Parent side: same share count, less cost.
        transactionRepository.save(Transaction.builder()
                .userId(holding.getUserId())
                .holdingId(holding.getId())
                .transactionType(TransactionType.DEMERGER_OUT)
                .quantity(BigDecimal.ZERO)   // no shares leave the parent holding
                .price(BigDecimal.ZERO)
                .amount(apportionedCost.setScale(2, RoundingMode.HALF_UP))
                .adjustmentFactor(retainedFraction)
                .transactionDate(request.getActionDate().atStartOfDay())
                .notes(note(request, String.format("%s%% of cost apportioned to %s on demerger",
                        strip(apportionPercent), resultingSymbol)))
                .build());

        costBasisService.applyDemergerOut(holding, retainedFraction);
        holdingRepository.save(holding);

        // Resulting side: a new holding, or a top-up of one already there.
        Holding resulting = findOrCreateResulting(holding, request, resultingSymbol, resultingCostPerShare);

        transactionRepository.save(Transaction.builder()
                .userId(holding.getUserId())
                .holdingId(resulting.getId())
                .transactionType(TransactionType.DEMERGER_IN)
                .quantity(resultingQuantity)
                .price(resultingCostPerShare)
                .amount(apportionedCost.setScale(2, RoundingMode.HALF_UP))
                .acquisitionDate(inheritedAcquisition)
                .transactionDate(request.getActionDate().atStartOfDay())
                .notes(note(request, "Received on demerger from " + holding.getSymbol()
                        + "; holding period inherited from " + inheritedAcquisition.toLocalDate()))
                .build());

        costBasisService.updateBuy(resulting, resultingQuantity, resultingCostPerShare);
        holdingRepository.save(resulting);
        refreshPrice(resulting);

        Map<String, Object> result = summary("DEMERGER", holding, quantityBefore, averageBefore);
        result.put("resultingHoldingId", resulting.getId().toString());
        result.put("resultingSymbol", resulting.getSymbol());
        result.put("resultingQuantity", resultingQuantity);
        result.put("resultingCostPerShare", resultingCostPerShare);
        result.put("apportionedCost", apportionedCost.setScale(2, RoundingMode.HALF_UP));
        result.put("inheritedAcquisitionDate", inheritedAcquisition.toLocalDate());
        result.put("summary", String.format(
                "%s shares of %s received at %s each, being %s%% of your %s cost. %s keeps its %s "
                        + "shares at a reduced average of %s. The new shares count as held since %s, "
                        + "so they are already long-term if that date is old enough.",
                strip(resultingQuantity), resulting.getSymbol(), money(resultingCostPerShare),
                strip(apportionPercent), holding.getSymbol(), holding.getSymbol(),
                strip(holding.getQuantity()), money(holding.getAverageBuyPrice()),
                inheritedAcquisition.toLocalDate()));
        return result;
    }

    /**
     * The resulting company's holding, reusing an existing one where the user already owns the
     * symbol in the same demat account.
     *
     * <p>Created at quantity zero and then filled by {@code updateBuy}, so the position is built
     * from the transaction rather than set twice — the mistake that made broker-synced holdings
     * show double their real quantity.
     */
    private Holding findOrCreateResulting(Holding parent, CorporateActionRequest request,
                                          String symbol, BigDecimal costPerShare) {
        java.util.UUID dematId = parent.getDematAccountId();
        if (request.getResultingDematAccountId() != null && !request.getResultingDematAccountId().isBlank()) {
            try {
                dematId = java.util.UUID.fromString(request.getResultingDematAccountId());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid demat account ID format");
            }
        }
        final java.util.UUID targetDemat = dematId;

        return holdingRepository.findByUserIdAndSymbol(parent.getUserId(), symbol).stream()
                .filter(h -> java.util.Objects.equals(h.getDematAccountId(), targetDemat))
                .findFirst()
                .orElseGet(() -> holdingRepository.save(Holding.builder()
                        .userId(parent.getUserId())
                        .assetType(request.getResultingAssetType() != null
                                ? request.getResultingAssetType() : parent.getAssetType())
                        .symbol(symbol)
                        .name(resolveName(request, symbol))
                        .quantity(BigDecimal.ZERO)
                        .averageBuyPrice(BigDecimal.ZERO)
                        .currentPrice(costPerShare)
                        .currentValue(BigDecimal.ZERO)
                        .realizedPnl(BigDecimal.ZERO)
                        .unrealizedPnl(BigDecimal.ZERO)
                        .currency(parent.getCurrency() != null ? parent.getCurrency() : "INR")
                        .exchange(parent.getExchange())
                        .sector(parent.getSector())
                        .isin(resolveIsin(symbol))
                        .dematAccountId(targetDemat)
                        .build()));
    }

    // ── helpers ───────────────────────────────────────────────────────

    /**
     * When the holding period of the oldest lot began — the acquisition date the resulting
     * company's shares inherit.
     */
    private java.util.Optional<LocalDateTime> earliestAcquisition(Holding holding) {
        return transactionRepository.findByHoldingId(holding.getId()).stream()
                .filter(t -> ACQUISITIONS.contains(t.getTransactionType()))
                .map(t -> t.getAcquisitionDate() != null ? t.getAcquisitionDate() : t.getTransactionDate())
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder());
    }

    /**
     * Display name for the new holding: what the user typed, else the symbols table, else the
     * ticker itself. Never null, so the holdings list cannot show a blank row.
     */
    private String resolveName(CorporateActionRequest request, String symbol) {
        if (request.getResultingName() != null && !request.getResultingName().isBlank()) {
            return request.getResultingName().trim();
        }
        return lookupSymbol(symbol)
                .map(s -> s.getName() != null && !s.getName().isBlank() ? s.getName() : symbol)
                .orElse(symbol);
    }

    private String resolveIsin(String symbol) {
        return lookupSymbol(symbol)
                .map(Symbol::getIsin)
                .filter(isin -> isin != null && !isin.isBlank())
                .orElse(null);
    }

    /**
     * The symbols row for a ticker, trying the ".NS" form too.
     *
     * <p>Users type "RELIANCE" while the reference table keys NSE equities as "RELIANCE.NS", so
     * a plain primary-key lookup misses. Left null when unknown rather than invented: an ISIN
     * is a real identifier and a guess would be worse than its absence.
     */
    private java.util.Optional<Symbol> lookupSymbol(String symbol) {
        return symbolRepository.findById(symbol)
                .or(() -> symbol.endsWith(".NS")
                        ? java.util.Optional.empty()
                        : symbolRepository.findById(symbol + ".NS"));
    }

    /** Best-effort: a missing price must not roll back a correctly recorded action. */
    private void refreshPrice(Holding holding) {
        try {
            priceService.refreshPrice(holding.getSymbol(), holding.getAssetType());
        } catch (RuntimeException e) {
            log.warn("Could not fetch a price for the new holding {}: {}",
                    holding.getSymbol(), e.getMessage());
        }
    }

    private Map<String, Object> summary(String type, Holding holding,
                                       BigDecimal quantityBefore, BigDecimal averageBefore) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("holdingId", holding.getId().toString());
        m.put("symbol", holding.getSymbol());
        m.put("quantityBefore", quantityBefore);
        m.put("quantityAfter", holding.getQuantity());
        m.put("averagePriceBefore", averageBefore);
        m.put("averagePriceAfter", holding.getAverageBuyPrice());
        return m;
    }

    private String note(CorporateActionRequest request, String generated) {
        return (request.getNotes() != null && !request.getNotes().isBlank())
                ? generated + " — " + request.getNotes()
                : generated;
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be greater than zero.");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim().toUpperCase();
    }

    private static String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal value) {
        return "Rs. " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
