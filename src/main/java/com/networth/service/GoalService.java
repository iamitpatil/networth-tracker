package com.networth.service;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.entity.Goal;
import com.networth.model.entity.Holding;
import com.networth.repository.GoalRepository;
import com.networth.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalService {

    private final GoalRepository goalRepository;
    private final HoldingRepository holdingRepository;

    @Transactional(readOnly = true)
    public List<Goal> getUserGoals(UUID userId) {
        return goalRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Goal getGoal(UUID userId, UUID goalId) {
        return findOwnedGoal(userId, goalId);
    }

    @Transactional
    public Goal createGoal(UUID userId, GoalRequest request) {
        Goal goal = Goal.builder()
                .userId(userId)
                .name(request.name())
                .targetAmount(request.targetAmount())
                .currentAmount(BigDecimal.ZERO)
                .targetDate(request.targetDate())
                .goalType(request.goalType())
                .riskProfile(request.riskProfile())
                .build();

        return goalRepository.save(goal);
    }

    @Transactional
    public Goal updateGoal(UUID userId, UUID goalId, GoalRequest request) {
        Goal goal = findOwnedGoal(userId, goalId);

        if (request.name() != null) goal.setName(request.name());
        if (request.targetAmount() != null) goal.setTargetAmount(request.targetAmount());
        if (request.targetDate() != null) goal.setTargetDate(request.targetDate());
        if (request.goalType() != null) goal.setGoalType(request.goalType());
        if (request.riskProfile() != null) goal.setRiskProfile(request.riskProfile());

        return goalRepository.save(goal);
    }

    @Transactional
    public void deleteGoal(UUID userId, UUID goalId) {
        Goal goal = findOwnedGoal(userId, goalId);
        goalRepository.delete(goal);
    }

    @Transactional
    public void mapHoldingToGoal(UUID userId, UUID goalId, UUID holdingId, BigDecimal allocationPercentage) {
        // Verify both goal and holding belong to user
        Goal goal = findOwnedGoal(userId, goalId);

        Holding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId.toString()));
        if (!holding.getUserId().equals(userId)) {
            log.warn("User {} attempted to map holding {} owned by {} to goal", userId, holdingId, holding.getUserId());
            throw new AccessDeniedException("Holding", holdingId.toString());
        }

        if (holding.getCurrentValue() != null) {
            BigDecimal contribution = holding.getCurrentValue()
                    .multiply(allocationPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

            goal.setCurrentAmount(goal.getCurrentAmount().add(contribution));
            goalRepository.save(goal);
        }
    }

    @Transactional(readOnly = true)
    public GoalProgress getGoalProgress(UUID userId, UUID goalId) {
        Goal goal = findOwnedGoal(userId, goalId);

        BigDecimal progress = goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0
                ? goal.getCurrentAmount()
                        .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal shortfall = goal.getTargetAmount().subtract(goal.getCurrentAmount());
        long daysRemaining = goal.getTargetDate() != null
                ? java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), goal.getTargetDate())
                : 0;

        // Fix integer division bug: handle daysRemaining < 30 case
        BigDecimal monthlyTarget = BigDecimal.ZERO;
        if (daysRemaining > 0) {
            BigDecimal monthsRemaining = BigDecimal.valueOf(daysRemaining)
                    .divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);
            if (monthsRemaining.compareTo(BigDecimal.ZERO) > 0) {
                monthlyTarget = shortfall.divide(monthsRemaining, 2, RoundingMode.HALF_UP);
            }
        }

        return GoalProgress.builder()
                .goalId(goalId.toString())
                .goalName(goal.getName())
                .targetAmount(goal.getTargetAmount())
                .currentAmount(goal.getCurrentAmount())
                .progressPercentage(progress.setScale(2, RoundingMode.HALF_UP))
                .shortfall(shortfall.max(BigDecimal.ZERO))
                .daysRemaining(daysRemaining)
                .monthlyTarget(monthlyTarget)
                .isOnTrack(monthlyTarget.compareTo(BigDecimal.ZERO) <= 0 ||
                        shortfall.compareTo(BigDecimal.ZERO) <= 0)
                .build();
    }

    /**
     * Find a goal by ID ensuring it belongs to the given user.
     */
    private Goal findOwnedGoal(UUID userId, UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId.toString()));
        if (!goal.getUserId().equals(userId)) {
            log.warn("User {} attempted to access goal {} owned by {}", userId, goalId, goal.getUserId());
            throw new AccessDeniedException("Goal", goalId.toString());
        }
        return goal;
    }

    public record GoalRequest(
            String name,
            BigDecimal targetAmount,
            LocalDate targetDate,
            String goalType,
            String riskProfile
    ) {}

    @lombok.Builder
    @lombok.Getter
    public static class GoalProgress {
        private String goalId;
        private String goalName;
        private BigDecimal targetAmount;
        private BigDecimal currentAmount;
        private BigDecimal progressPercentage;
        private BigDecimal shortfall;
        private long daysRemaining;
        private BigDecimal monthlyTarget;
        private boolean isOnTrack;
    }
}
