package com.networth.repository;

import com.networth.model.entity.ReferenceData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferenceDataRepository extends JpaRepository<ReferenceData, Integer> {
    List<ReferenceData> findByCategoryAndIsActiveTrueOrderBySortOrder(String category);

    @Query("SELECT DISTINCT r.category FROM ReferenceData r ORDER BY r.category")
    List<String> findDistinctCategories();
}
