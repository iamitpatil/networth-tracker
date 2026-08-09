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

    // Default queries filter out soft-deleted records
    @Query("SELECT h FROM Holding h WHERE h.userId = :userId AND h.deletedAt IS NULL")
    List<Holding> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT h FROM Holding h WHERE h.userId = :userId AND h.assetType = :assetType AND h.deletedAt IS NULL")
    List<Holding> findByUserIdAndAssetType(@Param("userId") UUID userId, @Param("assetType") AssetType assetType);

    @Query("SELECT h FROM Holding h WHERE h.userId = :userId AND h.symbol = :symbol AND h.deletedAt IS NULL")
    List<Holding> findByUserIdAndSymbol(@Param("userId") UUID userId, @Param("symbol") String symbol);

    @Query("SELECT h FROM Holding h WHERE h.userId = :userId AND h.symbol = :symbol AND h.dematAccountId = :dematAccountId AND h.deletedAt IS NULL")
    Optional<Holding> findByUserIdAndSymbolAndDematAccountId(@Param("userId") UUID userId, @Param("symbol") String symbol, @Param("dematAccountId") UUID dematAccountId);

    @Query("SELECT DISTINCT h.isin FROM Holding h WHERE h.symbol = :symbol AND h.isin IS NOT NULL AND h.deletedAt IS NULL")
    Optional<String> findIsinBySymbol(@Param("symbol") String symbol);

    @Query("SELECT h FROM Holding h WHERE h.userId = :userId AND h.dematAccountId = :dematAccountId AND h.deletedAt IS NULL")
    List<Holding> findByUserIdAndDematAccountId(@Param("userId") UUID userId, @Param("dematAccountId") UUID dematAccountId);

    // For finding ALL holdings (including deleted) - use only when needed for audit/reports
    @Query("SELECT h FROM Holding h WHERE h.userId = :userId")
    List<Holding> findAllByUserIdIncludingDeleted(@Param("userId") UUID userId);
}
