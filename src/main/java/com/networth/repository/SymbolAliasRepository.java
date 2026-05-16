package com.networth.repository;

import com.networth.model.entity.SymbolAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SymbolAliasRepository extends JpaRepository<SymbolAlias, UUID> {
    Optional<SymbolAlias> findBySymbolAndSource(String symbol, String source);
    List<SymbolAlias> findBySymbol(String symbol);
    List<SymbolAlias> findBySource(String source);
}
