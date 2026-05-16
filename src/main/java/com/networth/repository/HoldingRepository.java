package com.networth.repository;

import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, UUID> {
    List<Holding> findByUserId(UUID userId);
    List<Holding> findByUserIdAndAssetType(UUID userId, AssetType assetType);
    List<Holding> findByUserIdAndSymbol(UUID userId, String symbol);

    @Query("SELECT DISTINCT h.isin FROM Holding h WHERE h.symbol = :symbol AND h.isin IS NOT NULL")
    Optional<String> findIsinBySymbol(@Param("symbol") String symbol);
}
