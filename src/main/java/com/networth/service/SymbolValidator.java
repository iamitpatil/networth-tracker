package com.networth.service;

import com.networth.model.entity.Symbol;
import com.networth.model.enums.AssetType;
import com.networth.repository.SymbolRepository;
import com.networth.service.market.MarketCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether a ticker is one we can actually price, and what its canonical form is.
 *
 * <p>Nothing used to check this. {@code TransactionService.addTransaction} never looked at a symbol at
 * all, and {@code HoldingService.createHolding} consulted the reference list only to auto-resolve an
 * ISIN — logging a warning on a miss and saving the holding anyway. Verified against the running
 * stack: {@code FAKETICKER999}, {@code RELIENCE} and {@code NOTAREALSTOCK} all came back
 * {@code 201 Created}, and a CSV of two invented tickers reported {@code imported: 2, failed: 0}.
 *
 * <p>The cost of that was not cosmetic. Such a holding is permanently {@code priceStale: true} with
 * {@code priceAsOf: null}, and the five-minute sweep retries it during every market session at two
 * provider attempts per failure, for as long as the row exists. A typo became a permanent background
 * load and a position that silently never grows.
 *
 * <p>Two things this returns, both of which matter:
 *
 * <ul>
 *   <li>the <b>canonical key</b> — so {@code RELIANCE} and {@code RELIANCE.NS} cannot become two
 *       holdings of the same company, and so the symbol stored on the holding is the one
 *       {@code stock_price_history} is keyed by
 *   <li>the <b>ISIN</b>, where the reference row has one, replacing the separate resolution pass that
 *       used to run after the fact
 * </ul>
 *
 * <p>Not cached. A lookup is one or two primary-key hits on an indexed table, on a path that runs when
 * somebody adds a holding or imports a CSV row — a Redis copy of 22.6k keys would add an invalidation
 * problem to buy microseconds nobody is waiting on.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolValidator {

    private final SymbolRepository symbolRepository;

    /**
     * Asset types whose symbols must appear in a reference list.
     *
     * <p>Everything else passes through untouched, because there is no list to check it against: gold
     * is grams, a fixed deposit is a bank's own reference, real estate is an address. {@code BOND} is
     * deliberately absent too — NSE's {@code DEBT.csv} omits many listed bonds, so gating it would
     * reject instruments that genuinely exist.
     */
    private static final Set<AssetType> GATED =
            Set.of(AssetType.EQUITY, AssetType.ETF, AssetType.MUTUAL_FUND, AssetType.NPS);

    /**
     * A symbol that was found, and what it resolved to.
     *
     * @param symbol the canonical key to store on the holding
     * @param isin   the instrument's ISIN, or null where the reference row has none (NPS schemes)
     */
    public record Resolution(String symbol, String isin) {}

    /**
     * Resolve a symbol, or refuse it.
     *
     * <p>Throws {@link IllegalArgumentException} deliberately: {@code GlobalExceptionHandler} already
     * maps it to {@code 400 Bad Request} with the application's own error body, so a rejected ticker
     * needs no new handler and reads like every other validation failure.
     *
     * <p>Callers inside a transaction must call this <em>before</em> any {@code @Transactional}
     * collaborator, not inside one. An exception escaping a nested transactional call marks the shared
     * transaction rollback-only, so a caller that catches it to report one bad row would then fail its
     * own commit with {@code UnexpectedRollbackException} — turning "one row rejected" into "the whole
     * import failed".
     *
     * @param name optional holding name, used only to fuzzy-match a mutual fund whose symbol is a
     *             scheme name rather than an ISIN
     * @throws IllegalArgumentException if the type is gated and the symbol is not in its reference list
     */
    public Resolution requireKnown(String symbol, AssetType assetType, String name) {
        if (assetType == null || !GATED.contains(assetType) || symbol == null || symbol.isBlank()) {
            return new Resolution(symbol, null);
        }

        String trimmed = symbol.trim();
        Optional<Symbol> found = lookup(trimmed, assetType, name);
        if (found.isEmpty()) {
            throw new IllegalArgumentException(rejection(trimmed, assetType));
        }

        Symbol row = found.get();
        return new Resolution(row.getSymbol(), isinOf(row));
    }

    /**
     * The ISIN for a symbol, or null if it cannot be resolved.
     *
     * <p>The non-throwing half of the same lookup, for the paths that repair existing rows rather than
     * accept new input: {@code backfillMissingIsins} and the pricing-symbol resolution behind the
     * Holdings page. Those run over data that is already stored, where refusing it achieves nothing.
     */
    public String resolveIsin(String symbol, AssetType assetType, String name) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return lookup(symbol.trim(), assetType, name).map(this::isinOf).orElse(null);
    }

    /** Whether this asset type's symbols are checked at all. */
    public boolean isGated(AssetType assetType) {
        return assetType != null && GATED.contains(assetType);
    }

    // ── lookup ────────────────────────────────────────────────────────────────

    /**
     * Find the reference row for a symbol, trying every form a user might reasonably type.
     *
     * <p>Ordered cheapest-first, and every step is an indexed hit: primary key, then the
     * {@code .NS}-suffixed key, then {@code idx_symbols_isin}, then {@code idx_symbols_scheme_code},
     * and only for a fund the name search that scans.
     */
    private Optional<Symbol> lookup(String symbol, AssetType assetType, String name) {
        Set<String> categories = acceptableCategories(assetType);

        // 1. The key itself — an equity typed with its suffix, a fund typed as an ISIN, an NPS scheme
        //    typed as its code.
        Optional<Symbol> exact = symbolRepository.findById(symbol).filter(s -> categories.contains(s.getCategory()));
        if (exact.isPresent()) {
            return exact;
        }

        // 2. The NSE suffix, so a user typing RELIANCE gets RELIANCE.NS. This is where canonicalisation
        //    earns its keep: without it the same company arrives under two keys and only one of them
        //    has price history.
        if (isExchangeListed(assetType) && !symbol.endsWith(".NS")) {
            Optional<Symbol> suffixed = symbolRepository.findById(symbol + ".NS")
                    .filter(s -> categories.contains(s.getCategory()));
            if (suffixed.isPresent()) {
                return suffixed;
            }
        }

        // 3. By ISIN — the form a broker statement or a CAS uses.
        Optional<Symbol> byIsin = first(symbolRepository.findByIsin(symbol), categories);
        if (byIsin.isPresent()) {
            return byIsin;
        }

        // 4. By scheme code: an AMFI code for a fund, an SM… code for a pension fund.
        Optional<Symbol> byScheme = first(symbolRepository.findBySchemeCode(symbol), categories);
        if (byScheme.isPresent()) {
            return byScheme;
        }

        // 5. Only for funds: the symbol may be a scheme name rather than any kind of code, because that
        //    is what a broker's CSV exports. Kept last — it is the one step that scans.
        if (assetType == AssetType.MUTUAL_FUND) {
            return fuzzyFundMatch(symbol, name);
        }
        return Optional.empty();
    }

    /**
     * Match a fund by scheme name, e.g. {@code Axis Bluechip Fund - Direct Growth}.
     *
     * <p>Kept because our own downloadable sample CSV ships {@code PARAGPARIKHFLEXICAP} in the symbol
     * column: mutual-fund reference rows are keyed by ISIN, so a strict code lookup would reject the
     * very file the Import page offers as an example. Prefers the direct-growth variant, which is what
     * a holding without an explicit plan almost always is.
     */
    private Optional<Symbol> fuzzyFundMatch(String symbol, String name) {
        String[] terms = (name != null && !name.equals(symbol))
                ? new String[]{symbol, name}
                : new String[]{symbol};

        for (String term : terms) {
            if (term == null || term.isBlank()) continue;
            // Cut the plan qualifier: the reference name spells it differently often enough that
            // leaving it in loses the match.
            String keyword = term.split("\\s*[-–]\\s*(Direct|Regular|Growth|IDCW|Plan|Dividend)")[0].trim();
            if (keyword.length() <= 5) continue;

            List<Symbol> matches = symbolRepository.searchByCategoryAndName("MUTUAL_FUND", keyword);
            Optional<Symbol> match = matches.stream()
                    .filter(s -> containsAll(s.getName(), "direct", "growth"))
                    .findFirst()
                    .or(() -> matches.stream().filter(s -> containsAll(s.getName(), "direct")).findFirst())
                    .or(() -> matches.stream().findFirst());
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /**
     * Which reference categories satisfy a declared asset type.
     *
     * <p>Equity and ETF are interchangeable on purpose: both are exchange-listed, keyed identically and
     * priced through the same chain, and a user who files {@code GOLDBEES} under Equity has made a
     * classification choice, not a typo. Funds and pension schemes are not interchangeable with
     * anything — a fund row carries the ISIN that NAV lookups need, and an NPS row the scheme code, so
     * accepting a near-miss from another category would produce a holding that can never be priced.
     */
    private Set<String> acceptableCategories(AssetType assetType) {
        if (assetType == AssetType.EQUITY || assetType == AssetType.ETF) {
            return Set.of("EQUITY", "ETF");
        }
        return Set.of(assetType == null ? "" : assetType.name());
    }

    private boolean isExchangeListed(AssetType assetType) {
        return assetType == AssetType.EQUITY || assetType == AssetType.ETF;
    }

    /** For a fund the primary key <em>is</em> the ISIN; for everything else the column holds it. */
    private String isinOf(Symbol row) {
        if ("MUTUAL_FUND".equals(row.getCategory())) {
            return row.getSymbol();
        }
        return (row.getIsin() != null && !row.getIsin().isBlank()) ? row.getIsin() : null;
    }

    private Optional<Symbol> first(List<Symbol> rows, Set<String> categories) {
        return rows.stream().filter(s -> categories.contains(s.getCategory())).findFirst();
    }

    private static boolean containsAll(String name, String... needles) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String needle : needles) {
            if (!lower.contains(needle)) return false;
        }
        return true;
    }

    // ── the message ───────────────────────────────────────────────────────────

    /**
     * Say what went wrong in terms the user can act on.
     *
     * <p>There is no override flag, so the message is the entire remedy. It distinguishes the two cases
     * that look identical from the outside: a symbol that is genuinely not listed, and a reference list
     * that never loaded because a provider was down. Reporting a provider outage as a spelling mistake
     * would send somebody hunting for a typo in a ticker that is perfectly correct.
     */
    private String rejection(String symbol, AssetType assetType) {
        Set<String> categories = acceptableCategories(assetType);
        long available = categories.stream().mapToLong(symbolRepository::countByCategory).sum();

        if (available == 0) {
            return String.format("Cannot validate %s symbols: the %s reference list is empty, which means the "
                            + "startup load did not complete. POST /api/v1/symbols/refresh to retry.",
                    assetType, listName(assetType));
        }

        Instant newest = categories.stream()
                .map(symbolRepository::findMaxUpdatedAtByCategory)
                .flatMap(Optional::stream)
                .max(Instant::compareTo)
                .orElse(null);
        String asOf = newest == null
                ? "date unknown"
                : "updated " + LocalDate.ofInstant(newest, MarketCalendar.ZONE);

        return String.format("Unknown %s symbol '%s'. It is not in the %s reference list (%,d symbols, %s). "
                        + "Check the spelling, or POST /api/v1/symbols/refresh if this is a newly listed instrument.",
                assetType, symbol, listName(assetType), available, asOf);
    }

    private String listName(AssetType assetType) {
        if (assetType == AssetType.MUTUAL_FUND) return "AMFI";
        if (assetType == AssetType.NPS) return "NPS scheme";
        return "NSE";
    }
}
