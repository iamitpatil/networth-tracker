package com.networth.service;

import com.networth.model.dto.HoldingResponse;
import com.networth.model.entity.Goal;
import com.networth.model.entity.Holding;
import com.networth.repository.GoalRepository;
import com.networth.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final HoldingRepository holdingRepository;

    @Transactional(readOnly = true)
    public List<Goal> getUserGoals(UUID userId) {
        return goalRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Goal getGoal(UUID goalId) {
        return goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));
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
    public Goal updateGoal(UUID goalId, GoalRequest request) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        if (request.name() != null) goal.setName(request.name());
        if (request.targetAmount() != null) goal.setTargetAmount(request.targetAmount());
        if (request.targetDate() != null) goal.setTargetDate(request.targetDate());
        if (request.goalType() != null) goal.setGoalType(request.goalType());
        if (request.riskProfile() != null) goal.setRiskProfile(request.riskProfile());

        return goalRepository.save(goal);
    }

    @Transactional
    public void deleteGoal(UUID goalId) {
        goalRepository.deleteById(goalId);
    }

    @Transactional
    public void mapHoldingToGoal(UUID goalId, UUID holdingId, BigDecimal allocationPercentage) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        Holding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new IllegalArgumentException("Holding not found"));

        if (holding.getCurrentValue() != null) {
            BigDecimal contribution = holding.getCurrentValue()
                    .multiply(allocationPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

            goal.setCurrentAmount(goal.getCurrentAmount().add(contribution));
            goalRepository.save(goal);
        }
    }

    @Transactional(readOnly = true)
    public GoalProgress getGoalProgress(UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        BigDecimal progress = goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0
                ? goal.getCurrentAmount()
                        .divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal shortfall = goal.getTargetAmount().subtract(goal.getCurrentAmount());
        long daysRemaining = goal.getTargetDate() != null
                ? java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), goal.getTargetDate())
                : 0;

        BigDecimal monthlyTarget = daysRemaining > 0
                ? shortfall.divide(BigDecimal.valueOf(daysRemaining / 30), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

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
