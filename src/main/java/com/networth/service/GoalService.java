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
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
        goal.setDeletedAt(Instant.now());
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

        // Check if holding is already linked to another goal
        List<GoalHolding> existingLinks = goalHoldingRepository.findByHoldingId(holdingId);
        for (GoalHolding existing : existingLinks) {
            if (!existing.getGoalId().equals(goalId)) {
                String otherGoalName = goalRepository.findById(existing.getGoalId())
                        .map(Goal::getName).orElse("another goal");
                throw new IllegalArgumentException(
                        "This holding is already linked to \"" + otherGoalName + "\". "
                        + "Unlink it first before linking to a different goal.");
            }
        }

        // Clamp allocation to 1-100
        if (allocationPct == null || allocationPct.compareTo(BigDecimal.ZERO) <= 0) {
            allocationPct = BigDecimal.valueOf(100);
        }
        if (allocationPct.compareTo(BigDecimal.valueOf(100)) > 0) {
            allocationPct = BigDecimal.valueOf(100);
        }

        // Upsert: update allocation if already linked to this goal, create if not
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
        // One query for every linked holding, rather than one per link.
        Map<UUID, Holding> holdings = holdingsById(links);

        List<Map<String, Object>> result = new ArrayList<>();
        for (GoalHolding link : links) {
            Holding h = holdings.get(link.getHoldingId());
            if (h == null) {
                continue;   // holding deleted; the stale link contributes nothing
            }
            BigDecimal value = h.getCurrentValue() != null ? h.getCurrentValue() : BigDecimal.ZERO;
            BigDecimal pct = link.getAllocationPct() != null
                    ? link.getAllocationPct() : BigDecimal.valueOf(100);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", link.getId());
            map.put("holdingId", h.getId());
            map.put("symbol", h.getSymbol());
            map.put("name", h.getName());
            map.put("assetType", h.getAssetType());
            map.put("holdingValue", value);
            map.put("allocationPct", pct);
            map.put("allocatedValue", value.multiply(pct)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            result.add(map);
        }
        return result;
    }

    // --- Progress ---

    /**
     * Recomputes one goal's progress from the holdings linked to it.
     *
     * <p>A goal's {@code currentAmount} is the sum of each linked holding's current value times
     * its allocation percentage, so a holding split across two goals is not counted twice in full.
     */
    @Transactional
    public void refreshGoalProgress(UUID goalId) {
        Goal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null || goal.getDeletedAt() != null) return;

        List<GoalHolding> links = goalHoldingRepository.findByGoalId(goalId);
        goal.setCurrentAmount(sumAllocations(links, holdingsById(links)));
        goalRepository.save(goal);
    }

    /**
     * Refreshes every goal for every user. Runs nightly.
     *
     * <p>Three queries in total, regardless of how many goals and holdings exist: the goals, their
     * links, and the holdings those links point at. It previously issued one query per linked
     * holding per goal, so the cost grew with the whole user base every night.
     *
     * <p>Goals with no links are reset to zero rather than skipped. Skipping them left a stale
     * figure on screen forever once a linked holding was deleted -- the link row goes with it, so
     * the goal silently kept the progress those holdings used to provide.
     */
    @Transactional
    public int refreshAllGoalProgress() {
        List<Goal> active = goalRepository.findAll().stream()
                .filter(g -> g.getDeletedAt() == null)
                .toList();
        if (active.isEmpty()) {
            return 0;
        }

        List<GoalHolding> allLinks = goalHoldingRepository.findByGoalIdIn(
                active.stream().map(Goal::getId).toList());
        Map<UUID, Holding> holdings = holdingsById(allLinks);
        Map<UUID, List<GoalHolding>> linksByGoal = allLinks.stream()
                .collect(Collectors.groupingBy(GoalHolding::getGoalId));

        for (Goal goal : active) {
            goal.setCurrentAmount(sumAllocations(
                    linksByGoal.getOrDefault(goal.getId(), List.of()), holdings));
        }
        goalRepository.saveAll(active);
        return active.size();
    }

    /** Every holding referenced by these links, in one query. */
    private Map<UUID, Holding> holdingsById(List<GoalHolding> links) {
        if (links.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = links.stream().map(GoalHolding::getHoldingId).distinct().toList();
        return holdingRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Holding::getId, h -> h));
    }

    /**
     * The allocated value of these links.
     *
     * <p>A link whose holding no longer exists contributes nothing rather than failing the whole
     * refresh -- one deleted holding must not stop every other goal being updated.
     */
    private BigDecimal sumAllocations(List<GoalHolding> links, Map<UUID, Holding> holdings) {
        BigDecimal total = BigDecimal.ZERO;
        for (GoalHolding link : links) {
            Holding holding = holdings.get(link.getHoldingId());
            if (holding == null) {
                continue;
            }
            BigDecimal value = holding.getCurrentValue() != null
                    ? holding.getCurrentValue() : BigDecimal.ZERO;
            BigDecimal pct = link.getAllocationPct() != null
                    ? link.getAllocationPct() : BigDecimal.valueOf(100);
            total = total.add(value.multiply(pct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        return total;
    }

    /** Daily refresh at 2 AM */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Kolkata")
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
