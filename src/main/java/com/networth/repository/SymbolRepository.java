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
}
