package com.networth.repository;

import com.networth.model.entity.SymbolEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Reads over the cached corporate-action events.
 *
 * <p>Read-only by intent. Writes go through {@code SymbolEventService}'s batched
 * {@code JdbcTemplate} upsert, because no {@code hibernate.jdbc.batch_size} is configured and a
 * per-row {@code save} would cost a round trip each.
 */
public interface SymbolEventRepository extends JpaRepository<SymbolEvent, Long> {

    /**
     * Every event of one type for a set of symbols, in one query.
     *
     * <p>Batched over symbols deliberately: the dividend calculation walks a user's whole portfolio,
     * and asking per holding is how the previous implementation ended up issuing one call per symbol.
     * {@code idx_symbol_events_symbol_date} covers this.
     */
    @Query("""
            SELECT e FROM SymbolEvent e
            WHERE e.symbol IN :symbols AND e.eventType = :eventType
            ORDER BY e.symbol, e.exDate DESC
            """)
    List<SymbolEvent> findBySymbolsAndType(@Param("symbols") Collection<String> symbols,
                                           @Param("eventType") String eventType);

    /**
     * One symbol's events, newest first.
     *
     * <p>Not used by the calculation, which batches over the whole portfolio instead — asking per symbol
     * is the shape that caused the original bug. Kept for single-symbol inspection.
     */
    List<SymbolEvent> findBySymbolOrderByExDateDesc(String symbol);
}
