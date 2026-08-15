package com.networth.repository;

import com.networth.model.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, UUID> {
    List<Dividend> findByHoldingId(UUID holdingId);

    /** Dividends for many holdings in one query, so callers iterating holdings avoid an N+1. */
    List<Dividend> findByHoldingIdIn(Collection<UUID> holdingIds);
    List<Dividend> findBySymbol(String symbol);

    /**
     * How many dividends belong to a holding.
     *
     * <p>Read before a delete so the caller can report what the cascade took with it. Dividends are
     * removed by {@code fk_dividends_holding}'s {@code ON DELETE CASCADE} (V44), not by application code,
     * which is why nothing here deletes them.
     */
    long countByHoldingId(UUID holdingId);
}
