package com.networth.scheduler;

import com.networth.model.entity.User;
import com.networth.repository.UserRepository;
import com.networth.service.NetWorthHistoryService;
import com.networth.service.portfolio.HoldingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceUpdateScheduler {

    private final HoldingService holdingService;
    private final NetWorthHistoryService historyService;
    private final UserRepository userRepository;

    /**
     * How often to look for a stale price. Must be shorter than the shortest
     * {@code market.price.max-age} — a threshold equal to the poll period never fires, because at
     * every tick the age is the period minus however long the previous pass took. Five minutes
     * against a fifteen-minute equity age means a symbol refreshes on the first tick past fifteen.
     */
    private static final long SWEEP_INTERVAL_MS = 300_000;

    /**
     * Refreshes whatever has gone stale, and only that.
     *
     * <p>This replaces two jobs that between them got the timing wrong in both directions. An equity
     * job ran every 15 minutes and re-fetched every holding unconditionally — the same quote once per
     * holding rather than once per instrument, all night and all through the closed market, because
     * nothing recorded when a price had last been confirmed. A separate NAV job ran at 23:30 for
     * mutual funds. Between them they covered two asset types out of eight priceable ones: ETFs,
     * crypto, gold, sovereign gold bonds and NPS were never refreshed by any schedule at all.
     *
     * <p>There is no trading-day or market-hours check here on purpose. Whether a re-fetch can produce
     * a new number is exactly what {@code PriceFreshnessPolicy} decides, per asset type, and it knows
     * both that Friday's close stands until Monday's open and that crypto does not care. A calendar
     * gate at this level could only suppress the types the exchange calendar does not govern — which is
     * what happened before: the old NAV job at 23:30 and the old equity job's weekday check were two
     * different answers to the same question.
     *
     * <p>Nor is there a sleep between symbols. {@code ProviderRateLimiter} paces the outbound calls at
     * whatever each provider actually permits, which the previous fixed 300ms could only approximate.
     */
    @Scheduled(fixedRate = SWEEP_INTERVAL_MS)
    public void refreshStalePrices() {
        try {
            holdingService.refreshStalePrices();
        } catch (Exception e) {
            // A sweep that throws is never rescheduled by Spring's default error handling on a
            // fixedRate task, so a single bad symbol could silently end all price updates.
            log.error("Price sweep failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Kolkata")
    public void snapshotNetWorth() {
        log.info("Starting daily net worth snapshots...");

        List<User> users = userRepository.findAll();
        for (User user : users) {
            try {
                historyService.snapshotNetWorth(user.getId());
            } catch (Exception e) {
                log.error("Failed to snapshot net worth for user {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("Net worth snapshots completed.");
    }
}
