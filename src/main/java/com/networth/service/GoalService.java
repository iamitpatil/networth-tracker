package com.networth.service;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.entity.Goal;
import com.networth.model.entity.GoalHolding;
import com.networth.model.entity.Holding;
import com.networth.repository.GoalHoldingRepository;
import com.networth.repository.GoalRepository;
import com.networth.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalService {

    private final GoalRepository goalRepository;
    private final HoldingRepository holdingRepository;
    private final GoalHoldingRepository goalHoldingRepository;

    // --- CRUD ---

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
        goal.setDeletedAt(LocalDateTime.now());
        goalRepository.save(goal);
        log.info("Soft-deleted goal {} for user {}", goalId, userId);
    }

    // --- Holding Linking ---

    @Transactional
    public GoalHolding linkHolding(UUID userId, UUID goalId, UUID holdingId, BigDecimal allocationPct) {
        findOwnedGoal(userId, goalId);
        Holding holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId.toString()));
        if (!holding.getUserId().equals(userId)) {
            throw new AccessDeniedException("Holding", holdingId.toString());
        }

        // Clamp allocation to 1-100
        if (allocationPct == null || allocationPct.compareTo(BigDecimal.ZERO) <= 0) {
            allocationPct = BigDecimal.valueOf(100);
        }
        if (allocationPct.compareTo(BigDecimal.valueOf(100)) > 0) {
            allocationPct = BigDecimal.valueOf(100);
        }

        // Upsert: update if already linked, create if not
        GoalHolding link = goalHoldingRepository.findByGoalIdAndHoldingId(goalId, holdingId)
                .orElse(GoalHolding.builder().goalId(goalId).holdingId(holdingId).build());
        link.setAllocationPct(allocationPct);
        link = goalHoldingRepository.save(link);

        // Refresh goal's currentAmount
        refreshGoalProgress(goalId);

        return link;
    }

    @Transactional
    public void unlinkHolding(UUID userId, UUID goalId, UUID holdingId) {
        findOwnedGoal(userId, goalId);
        goalHoldingRepository.deleteByGoalIdAndHoldingId(goalId, holdingId);
        refreshGoalProgress(goalId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLinkedHoldings(UUID userId, UUID goalId) {
        findOwnedGoal(userId, goalId);
        List<GoalHolding> links = goalHoldingRepository.findByGoalId(goalId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (GoalHolding link : links) {
            holdingRepository.findById(link.getHoldingId()).ifPresent(h -> {
                BigDecimal value = h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO;
                BigDecimal allocated = value.multiply(link.getAllocationPct())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", link.getId());
                map.put("holdingId", h.getId());
                map.put("symbol", h.getSymbol());
                map.put("name", h.getName());
                map.put("assetType", h.getAssetType());
                map.put("holdingValue", value);
                map.put("allocationPct", link.getAllocationPct());
                map.put("allocatedValue", allocated);
                result.add(map);
            });
        }
        return result;
    }

    // --- Progress ---

    @Transactional
    public void refreshGoalProgress(UUID goalId) {
        Goal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null || goal.getDeletedAt() != null) return;

        List<GoalHolding> links = goalHoldingRepository.findByGoalId(goalId);
        if (links.isEmpty()) return; // Don't reset to 0 if no links — keep manual value

        BigDecimal total = BigDecimal.ZERO;
        for (GoalHolding link : links) {
            holdingRepository.findById(link.getHoldingId()).ifPresent(h -> {
                // Non-final variable workaround: use array
            });
        }

        // Recompute
        for (GoalHolding link : links) {
            Optional<Holding> hOpt = holdingRepository.findById(link.getHoldingId());
            if (hOpt.isPresent()) {
                BigDecimal value = hOpt.get().getCurrentValue() != null
                        ? hOpt.get().getCurrentValue() : BigDecimal.ZERO;
                BigDecimal allocated = value.multiply(link.getAllocationPct())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                total = total.add(allocated);
            }
        }

        goal.setCurrentAmount(total);
        goalRepository.save(goal);
    }

    /** Refresh all goals for all users. Called daily by scheduler. */
    @Transactional
    public int refreshAllGoalProgress() {
        List<Goal> allGoals = goalRepository.findAll();
        int refreshed = 0;
        for (Goal goal : allGoals) {
            if (goal.getDeletedAt() != null) continue;
            List<GoalHolding> links = goalHoldingRepository.findByGoalId(goal.getId());
            if (links.isEmpty()) continue;

            BigDecimal total = BigDecimal.ZERO;
            for (GoalHolding link : links) {
                Optional<Holding> hOpt = holdingRepository.findById(link.getHoldingId());
                if (hOpt.isPresent()) {
                    BigDecimal value = hOpt.get().getCurrentValue() != null
                            ? hOpt.get().getCurrentValue() : BigDecimal.ZERO;
                    BigDecimal allocated = value.multiply(link.getAllocationPct())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    total = total.add(allocated);
                }
            }

            goal.setCurrentAmount(total);
            goalRepository.save(goal);
            refreshed++;
        }
        return refreshed;
    }

    /** Daily refresh at 2 AM */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledGoalRefresh() {
        log.info("Running daily goal progress refresh...");
        int count = refreshAllGoalProgress();
        log.info("Refreshed {} goals", count);
    }

    @Transactional(readOnly = true)
    public GoalProgress getGoalProgress(UUID userId, UUID goalId) {
        Goal goal = findOwnedGoal(userId, goalId);

        BigDecimal current = goal.getCurrentAmount() != null ? goal.getCurrentAmount() : BigDecimal.ZERO;
        BigDecimal progress = goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0
                ? current.divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal shortfall = goal.getTargetAmount().subtract(current).max(BigDecimal.ZERO);
        long daysRemaining = goal.getTargetDate() != null
                ? java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), goal.getTargetDate())
                : 0;

        BigDecimal monthlyTarget = BigDecimal.ZERO;
        if (daysRemaining > 0 && shortfall.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal monthsRemaining = BigDecimal.valueOf(daysRemaining)
                    .divide(BigDecimal.valueOf(30), 4, RoundingMode.HALF_UP);
            if (monthsRemaining.compareTo(BigDecimal.ZERO) > 0) {
                monthlyTarget = shortfall.divide(monthsRemaining, 2, RoundingMode.HALF_UP);
            }
        }

        int linkedHoldings = goalHoldingRepository.findByGoalId(goalId).size();

        return GoalProgress.builder()
                .goalId(goalId.toString())
                .goalName(goal.getName())
                .targetAmount(goal.getTargetAmount())
                .currentAmount(current)
                .progressPercentage(progress.setScale(2, RoundingMode.HALF_UP))
                .shortfall(shortfall)
                .daysRemaining(daysRemaining)
                .monthlyTarget(monthlyTarget)
                .isOnTrack(shortfall.compareTo(BigDecimal.ZERO) <= 0
                        || (daysRemaining > 0 && monthlyTarget.compareTo(BigDecimal.ZERO) > 0))
                .linkedHoldings(linkedHoldings)
                .build();
    }

    // --- Helpers ---

    /** Old method kept for backward compatibility. Now delegates to linkHolding. */
    @Transactional
    public void mapHoldingToGoal(UUID userId, UUID goalId, UUID holdingId, BigDecimal allocationPercentage) {
        linkHolding(userId, goalId, holdingId, allocationPercentage);
    }

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
        private int linkedHoldings;
    }
}
