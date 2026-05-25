package com.networth.repository;

import com.networth.model.entity.Symbol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SymbolRepository extends JpaRepository<Symbol, String> {
    List<Symbol> findByCategory(String category);

    @Query("SELECT s FROM Symbol s WHERE s.category = :category AND LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY s.name")
    List<Symbol> searchByCategoryAndName(@Param("category") String category, @Param("keyword") String keyword);
}
