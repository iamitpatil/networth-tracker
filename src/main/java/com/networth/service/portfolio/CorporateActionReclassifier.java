package com.networth.service.portfolio;

import com.networth.model.entity.Holding;
import com.networth.model.entity.SymbolEvent;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.SymbolEventRepository;
import com.networth.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Retypes the transactions that record a corporate action as a purchase at zero rupees.
 *
 * <p>Twelve rows across seven symbols in the live database were {@code BUY} at {@code price = 0} when each
 * was really a bonus, a split or a demerger. The types existed and
 * {@code V36__corporate_actions.sql} had already added {@code acquisition_date} and
 * {@code adjustment_factor} for them; nothing had ever written one.
 *
 * <p>It is not only that the ledger reads as junk. A demerged share inherits the original holding period
 * under s.2(42A), so ITC Hotels and Kwality Wall's shares dated at the demerger look short-term when they
 * are long-term. And a split recorded as a nil-cost purchase leaves each earlier lot's per-share cost
 * unscaled, so FIFO capital gains come out wrong even though the totals happen to agree.
 *
 * <p><b>Two steps on purpose.</b> {@link #propose} reports what it would change and {@link #apply} writes
 * only what was reported. A wrong {@code adjustment_factor} silently corrupts every later capital-gains
 * figure for that symbol, and there is no way to notice from the outside, so nothing is retyped without
 * the report having been available to read first.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorporateActionReclassifier {

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final SymbolEventRepository symbolEventRepository;

    /**
     * Which symbol a demerged company's shares came out of, and at what entitlement ratio.
     *
     * <p>Needed because a demerger is published on the <em>parent's</em> corporate-action record, not the
     * new company's: asking NSE about ITCHOTELS returns nothing at all. The pairing therefore cannot be
     * discovered from the resulting symbol and has to be stated.
     *
     * <p>Format {@code CHILD:PARENT:newPerParentShare}. Both defaults were confirmed against the holdings
     * themselves rather than taken from a press release — 407 ITC shares produced 40 ITCHOTELS (1 per 10),
     * and 51 HUL shares produced 51 KWIL (1 per 1).
     */
    @Value("${app.corporate-actions.demerger-parents:ITCHOTELS.NS:ITC.NS:0.1,KWIL.NS:HINDUNILVR.NS:1.0}")
    private String demergerParents;

    /**
     * How far a credit can sit from the exchange's ex-date and still be the same event.
     *
     * <p>Shares are credited after the ex-date, not on it: RELIANCE went ex on 28 October 2024 and the
     * bonus landed on the 30th. Seven days covers a settlement holiday run without reaching the next
     * event, which for any real company is months away.
     */
    private static final int MATCH_WINDOW_DAYS = 7;

    /** A proposed change to one transaction, or a note about why none could be proposed. */
    public record Proposal(
            String transactionId, String symbol, LocalDate date, BigDecimal quantity,
            String currentType, String proposedType,
            BigDecimal ratio, BigDecimal adjustmentFactor, LocalDate inheritedAcquisitionDate,
            String parentSymbol, String matchedEvent, boolean actionable, String note) {}

    // ── dry run ───────────────────────────────────────────────────────────────

    /**
     * What would change, without changing anything.
     *
     * <p>Every zero-price {@code BUY} is reported whether or not a proposal could be made, because a row
     * this cannot explain is exactly the one somebody needs to look at.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> propose(UUID userId) {
        Map<UUID, Holding> holdings = new LinkedHashMap<>();
        for (Holding h : holdingRepository.findByUserId(userId)) {
            holdings.put(h.getId(), h);
        }

        List<Proposal> proposals = new ArrayList<>();
        for (Transaction t : transactionRepository.findByUserId(userId)) {
            if (!isZeroPriceBuy(t)) continue;
            Holding holding = holdings.get(t.getHoldingId());
            if (holding == null) continue;
            proposals.add(propose(t, holding));
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("zeroPriceRows", proposals.size());
        report.put("actionable", proposals.stream().filter(Proposal::actionable).count());
        report.put("needsReview", proposals.stream().filter(p -> !p.actionable()).count());
        report.put("proposals", proposals);
        report.put("possibleDuplicates", possibleDuplicates(userId, holdings));
        return report;
    }

    private boolean isZeroPriceBuy(Transaction t) {
        return t.getTransactionType() == TransactionType.BUY
                && (t.getPrice() == null || t.getPrice().signum() == 0);
    }

    private Proposal propose(Transaction t, Holding holding) {
        String symbol = holding.getSymbol();
        LocalDate date = t.getTransactionDate().toLocalDate();

        // A demerger first: its event lives under the parent, so looking under this symbol finds nothing.
        Demerger demerger = demergerFor(symbol);
        if (demerger != null) {
            LocalDate inherited = earliestAcquisition(holding.getUserId(), demerger.parent());
            return new Proposal(t.getId().toString(), symbol, date, t.getQuantity(),
                    t.getTransactionType().name(), "DEMERGER_IN",
                    demerger.ratio(), null, inherited, demerger.parent(),
                    "configured demerger from " + demerger.parent(),
                    inherited != null,
                    inherited != null
                            ? "Holding period inherited from " + demerger.parent() + " under s.2(42A)"
                            : "No " + demerger.parent() + " transaction found to inherit an acquisition date from");
        }

        // Otherwise match the exchange's own record for this symbol.
        List<SymbolEvent> candidates = symbolEventRepository.findBySymbolOrderByExDateDesc(symbol).stream()
                .filter(e -> "BONUS".equals(e.getEventType()) || "SPLIT".equals(e.getEventType()))
                .filter(e -> e.getExDate() != null
                        && Math.abs(ChronoUnit.DAYS.between(e.getExDate(), date)) <= MATCH_WINDOW_DAYS)
                .sorted(Comparator.comparingLong(e -> Math.abs(ChronoUnit.DAYS.between(e.getExDate(), date))))
                .toList();

        if (candidates.isEmpty()) {
            return new Proposal(t.getId().toString(), symbol, date, t.getQuantity(),
                    t.getTransactionType().name(), null, null, null, null, null, null, false,
                    "No stored bonus or split within " + MATCH_WINDOW_DAYS + " days. Run the event sync, or "
                            + "this may be a compound action recorded as one row.");
        }

        // More than one match means the row cannot be attributed to a single event -- Bajaj Finance did a
        // bonus and a split in the same week, and its one +72 row is really two events per demat account.
        if (candidates.size() > 1) {
            String found = candidates.stream()
                    .map(e -> e.getEventType() + " x" + e.getRatio() + " ex " + e.getExDate())
                    .reduce((a, b) -> a + "; " + b).orElse("");
            return new Proposal(t.getId().toString(), symbol, date, t.getQuantity(),
                    t.getTransactionType().name(), null, null, null, null, null, found, false,
                    "Matches " + candidates.size() + " events, so one row cannot represent it. Split the "
                            + "transaction per event before retyping.");
        }

        SymbolEvent event = candidates.get(0);
        boolean isSplit = "SPLIT".equals(event.getEventType());
        // A split re-denominates existing shares, so each earlier lot's per-share cost is scaled by the
        // inverse of the multiplier. A bonus adds nil-cost shares instead and leaves earlier lots alone,
        // which is why adjustment_factor stays null for it.
        BigDecimal adjustment = isSplit && event.getRatio() != null && event.getRatio().signum() > 0
                ? BigDecimal.ONE.divide(event.getRatio(), 8, RoundingMode.HALF_UP)
                : null;

        return new Proposal(t.getId().toString(), symbol, date, t.getQuantity(),
                t.getTransactionType().name(), isSplit ? "SPLIT" : "BONUS",
                event.getRatio(), adjustment, null, null,
                event.getDescription() + " (ex " + event.getExDate() + ")", true,
                isSplit ? "Earlier lots' per-share cost scaled by " + adjustment
                        : "Nil cost of acquisition under s.55(2)(aa)");
    }

    // ── apply ─────────────────────────────────────────────────────────────────

    /**
     * Write the actionable proposals, and nothing else.
     *
     * <p>Quantities are never touched. The share counts in these rows are already right — it was only ever
     * their type, and the tax fields that depend on it, that were wrong.
     */
    @Transactional
    public Map<String, Object> apply(UUID userId) {
        Map<String, Object> report = propose(userId);
        @SuppressWarnings("unchecked")
        List<Proposal> proposals = (List<Proposal>) report.get("proposals");

        int applied = 0;
        List<String> skipped = new ArrayList<>();
        for (Proposal p : proposals) {
            if (!p.actionable() || p.proposedType() == null) {
                skipped.add(p.symbol() + " " + p.date() + ": " + p.note());
                continue;
            }
            Transaction t = transactionRepository.findById(UUID.fromString(p.transactionId())).orElse(null);
            if (t == null) continue;

            t.setTransactionType(TransactionType.valueOf(p.proposedType()));
            if (p.adjustmentFactor() != null) {
                t.setAdjustmentFactor(p.adjustmentFactor());
            }
            if (p.inheritedAcquisitionDate() != null) {
                t.setAcquisitionDate(p.inheritedAcquisitionDate().atStartOfDay());
            }
            transactionRepository.save(t);
            applied++;
            log.info("Retyped {} {} from {} to {}{}", p.symbol(), p.date(), p.currentType(), p.proposedType(),
                    p.inheritedAcquisitionDate() != null
                            ? ", acquisition date inherited as " + p.inheritedAcquisitionDate() : "");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("applied", applied);
        result.put("skipped", skipped);
        return result;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private record Demerger(String child, String parent, BigDecimal ratio) {}

    private Demerger demergerFor(String symbol) {
        if (demergerParents == null || demergerParents.isBlank()) return null;
        for (String entry : demergerParents.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length >= 2 && parts[0].equalsIgnoreCase(symbol)) {
                BigDecimal ratio = parts.length > 2 ? new BigDecimal(parts[2]) : null;
                return new Demerger(parts[0], parts[1], ratio);
            }
        }
        return null;
    }

    /**
     * The oldest transaction date on the parent holding, which is the date the demerged shares inherit.
     *
     * <p>Earliest rather than latest: s.2(42A) gives the resulting shares the period the original shares
     * were held, and taking the oldest lot is the reading that does not shorten it.
     */
    private LocalDate earliestAcquisition(UUID userId, String parentSymbol) {
        return holdingRepository.findByUserIdAndSymbol(userId, parentSymbol).stream()
                .flatMap(h -> transactionRepository.findByHoldingId(h.getId()).stream())
                .filter(t -> t.getTransactionDate() != null)
                .map(t -> t.getTransactionDate().toLocalDate())
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /**
     * Rows that look like a repeated import, reported without any proposal attached.
     *
     * <p>Four one-share purchases at the same price on one day is entirely plausible, so this cannot be
     * decided automatically. Naming them is useful; acting on them is not.
     */
    private List<Map<String, Object>> possibleDuplicates(UUID userId, Map<UUID, Holding> holdings) {
        Map<String, List<Transaction>> groups = new LinkedHashMap<>();
        for (Transaction t : transactionRepository.findByUserId(userId)) {
            Holding h = holdings.get(t.getHoldingId());
            if (h == null) continue;
            String key = h.getSymbol() + "|" + t.getTransactionDate().toLocalDate()
                    + "|" + t.getTransactionType() + "|" + t.getQuantity() + "|" + t.getPrice();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        groups.forEach((key, rows) -> {
            if (rows.size() > 1) {
                String[] parts = key.split("\\|");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("symbol", parts[0]);
                row.put("date", parts[1]);
                row.put("quantity", parts[3]);
                row.put("price", parts[4]);
                row.put("copies", rows.size());
                row.put("note", "Identical rows. Could be a repeated import or genuinely separate buys — "
                        + "no change proposed.");
                out.add(row);
            }
        });
        return out;
    }
}
