package com.networth.repository;

import com.networth.model.entity.Symbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SymbolRepository extends JpaRepository<Symbol, String> {
    List<Symbol> findByCategory(String category);

    @Query("SELECT s FROM Symbol s WHERE s.category = :category AND LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY s.name")
    List<Symbol> searchByCategoryAndName(@Param("category") String category, @Param("keyword") String keyword);

    /**
     * How many rows one reference list has.
     *
     * <p>The pre-start loader's whole decision rests on this: zero means the category has never been
     * loaded and must block boot, because strict validation would otherwise reject every symbol in it.
     * Covered by {@code idx_symbols_category}, so a warm boot's five calls are index-only.
     */
    long countByCategory(String category);

    /**
     * When a reference list was last written, or empty if it has no rows.
     *
     * <p>Distinguishes "loaded recently" from "loaded months ago" so a stale list can be refreshed in
     * the background instead of blocking, and so the rejection message can tell the user how old the
     * list it was checked against is.
     */
    @Query("SELECT MAX(s.updatedAt) FROM Symbol s WHERE s.category = :category")
    Optional<Instant> findMaxUpdatedAtByCategory(@Param("category") String category);

    /**
     * Rows carrying this ISIN.
     *
     * <p>A list rather than an optional because the same instrument legitimately appears twice: an ETF
     * is keyed {@code NIFTYBEES.NS} while AMFI's fund-of-funds entry for it is keyed by ISIN, and both
     * may carry an ISIN. The caller picks by category.
     */
    List<Symbol> findByIsin(String isin);

    /** Rows carrying this scheme code — an AMFI code for a fund, an {@code SM…} code for an NPS scheme. */
    List<Symbol> findBySchemeCode(String schemeCode);

    /**
     * Symbols somebody holds whose corporate-action events still need fetching.
     *
     * <p>The narrow work list, for {@code app.events.scope=held}. Finishes in minutes rather than hours,
     * which is the right trade when only the symbols on somebody's screen matter — the same reasoning
     * {@code UpstoxHistoricalService.heldEquities} applies to price history.
     *
     * <p>{@code events_synced_at IS NULL} is the fetch-once condition: one NSE call returns a symbol's
     * entire dividend history, so once stored there is nothing to re-ask for until a new event is
     * announced — which is what {@code before} is for.
     *
     * <p>Native because {@code events_synced_at} is deliberately not mapped on {@link Symbol}: it is
     * written by the sync's JDBC batch, and mapping it would let any unrelated JPA save of a Symbol
     * flush a null over it.
     */
    @Query(value = """
            SELECT s.symbol FROM symbols s
             WHERE s.category IN ('EQUITY', 'ETF')
               AND (s.events_synced_at IS NULL OR s.events_synced_at < :before)
               AND EXISTS (SELECT 1 FROM holdings h
                            WHERE h.symbol = s.symbol AND h.deleted_at IS NULL)
             ORDER BY s.symbol
            """, nativeQuery = true)
    List<String> findHeldSymbolsNeedingEventSync(@Param("before") Instant before);

    /**
     * Every listed equity and ETF whose corporate-action events still need fetching.
     *
     * <p>The default work list. About 2,428 symbols, and at NSE's ten requests a minute that is roughly
     * four hours — but only once: {@code events_synced_at} means a completed symbol is never re-fetched
     * until {@code app.events.max-age} passes, so a second run has almost nothing to do. Covering
     * everything listed rather than only what is held means a dividend history is already there the day
     * a holding is created, instead of the new holding showing zero until the next sync.
     *
     * <p>Ordered so that held symbols come first. The run is long and cancellable, so if it is
     * interrupted the symbols somebody is actually looking at are the ones already done.
     */
    @Query(value = """
            SELECT s.symbol FROM symbols s
             WHERE s.category IN ('EQUITY', 'ETF')
               AND (s.events_synced_at IS NULL OR s.events_synced_at < :before)
             ORDER BY EXISTS (SELECT 1 FROM holdings h
                               WHERE h.symbol = s.symbol AND h.deleted_at IS NULL) DESC,
                      s.symbol
            """, nativeQuery = true)
    List<String> findAllSymbolsNeedingEventSync(@Param("before") Instant before);
}
