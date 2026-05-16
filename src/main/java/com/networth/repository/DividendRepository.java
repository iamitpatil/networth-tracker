package com.networth.repository;

import com.networth.model.entity.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, UUID> {
    List<Dividend> findByHoldingId(UUID holdingId);
    List<Dividend> findBySymbol(String symbol);
}
