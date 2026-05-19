package com.networth.repository;

import com.networth.model.entity.GoalHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalHoldingRepository extends JpaRepository<GoalHolding, UUID> {
    List<GoalHolding> findByGoalId(UUID goalId);
    List<GoalHolding> findByHoldingId(UUID holdingId);
    Optional<GoalHolding> findByGoalIdAndHoldingId(UUID goalId, UUID holdingId);
    void deleteByGoalIdAndHoldingId(UUID goalId, UUID holdingId);
}
