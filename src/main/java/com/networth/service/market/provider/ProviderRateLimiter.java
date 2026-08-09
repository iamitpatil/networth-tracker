package com.networth.service.market.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces each provider's request budget with a rolling-window counter.
 *
 * <p>Rolling rather than fixed windows, because a fixed window allows double the intended rate
 * across its boundary: 25 requests at 23:59:59 and 25 more at 00:00:01 is 50 in two seconds
 * against a daily limit of 25. Timestamps are retained and counted back over each window instead.
 *
 * <p>Two ways to ask, deliberately:
 *
 * <ul>
 *   <li>{@link #tryAcquire(String)} — never blocks. Used on the live read path, where a throttled
 *       provider should be skipped so the chain falls through to the next one. Waiting there would
 *       hold an HTTP thread hostage to a provider we have an alternative for.</li>
 *   <li>{@link #acquire(String)} — waits briefly, then gives up. Used by the backfill, which has no
 *       alternative provider and is not serving a user, so pausing beats dropping a symbol's
 *       history.</li>
 * </ul>
 *
 * <p>Memory is bounded: a provider only ever retains timestamps inside its longest window, and
 * anything older is discarded on the next call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProviderRateLimiter {

    private final ProviderRateLimits config;

    /** Per-provider request timestamps in epoch millis, oldest first. */
    private final Map<String, Deque<Long>> history = new ConcurrentHashMap<>();

    /** How many requests each provider has turned away, for the status endpoint. */
    private final Map<String, Long> throttled = new ConcurrentHashMap<>();

    /**
     * Takes a slot if one is free, without waiting.
     *
     * @return false when the provider is at its limit, meaning the caller should try another
     */
    public boolean tryAcquire(String provider) {
        return acquire(provider, Duration.ZERO);
    }

    /** Takes a slot, waiting up to the configured {@code maxWait}. */
    public boolean acquire(String provider) {
        return acquire(provider, config.getMaxWait());
    }

    /**
     * Takes a slot, waiting at most {@code maxWait}.
     *
     * <p>An unknown provider is allowed through rather than blocked. A provider added to a chain
     * without a matching limit entry is a configuration gap, and failing every one of its requests
     * would present as the provider itself being broken.
     */
    public boolean acquire(String provider, Duration maxWait) {
        if (!config.isEnabled()) {
            return true;
        }
        ProviderRateLimits.Limit limit = config.forProvider(provider);
        if (limit == null || limit.windows().isEmpty()) {
            log.debug("No rate limit configured for provider '{}'; allowing", provider);
            return true;
        }

        long deadline = System.currentTimeMillis() + Math.max(0, maxWait.toMillis());
        while (true) {
            long waitMs = tryTake(provider, limit);
            if (waitMs == 0) {
                return true;
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throttled.merge(provider, 1L, Long::sum);
                log.debug("Rate limit reached for {} ({}); a slot frees in {} ms",
                        provider, describe(limit), waitMs);
                return false;
            }
            try {
                Thread.sleep(Math.min(waitMs, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /**
     * @return 0 when a slot was taken, otherwise how many millis until the tightest breached
     *         window frees one
     */
    private long tryTake(String provider, ProviderRateLimits.Limit limit) {
        Deque<Long> timestamps = history.computeIfAbsent(provider, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();

        // One lock per provider: providers never contend with each other, and the critical section
        // is a handful of arithmetic operations.
        synchronized (timestamps) {
            prune(timestamps, now, limit.longestWindow());

            long longestWait = 0;
            for (ProviderRateLimits.Window window : limit.windows()) {
                long cutoff = now - window.window().toMillis();
                long used = timestamps.stream().filter(t -> t > cutoff).count();
                if (used >= window.limit()) {
                    // The oldest request still inside this window is the one whose expiry frees a
                    // slot, so that is how long the caller would have to wait.
                    long oldestInWindow = timestamps.stream().filter(t -> t > cutoff)
                            .min(Long::compare).orElse(now);
                    longestWait = Math.max(longestWait,
                            oldestInWindow + window.window().toMillis() - now + 1);
                }
            }
            if (longestWait > 0) {
                return longestWait;
            }
            timestamps.addLast(now);
            return 0;
        }
    }

    private void prune(Deque<Long> timestamps, long now, Duration longest) {
        long cutoff = now - longest.toMillis();
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
            timestamps.pollFirst();
        }
    }

    private String describe(ProviderRateLimits.Limit limit) {
        return limit.windows().stream().map(ProviderRateLimits.Window::label)
                .reduce((a, b) -> a + ", " + b).orElse("none");
    }

    /**
     * Current usage per provider, for {@code GET /api/v1/market/providers}.
     *
     * <p>Reports the published limit alongside the enforced budget and what is used right now, so
     * "why did that not refresh" has an answer that does not require reading logs.
     */
    public List<Map<String, Object>> snapshot() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> out = new ArrayList<>();

        config.getProviders().forEach((name, limit) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("provider", name);
            row.put("documented", limit.getDocumented());
            row.put("source", limit.getSource());

            Deque<Long> timestamps = history.get(name);
            List<Map<String, Object>> windows = new ArrayList<>();
            boolean atLimit = false;
            for (ProviderRateLimits.Window window : limit.windows()) {
                long cutoff = now - window.window().toMillis();
                long used;
                if (timestamps == null) {
                    used = 0;
                } else {
                    synchronized (timestamps) {
                        used = timestamps.stream().filter(t -> t > cutoff).count();
                    }
                }
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("window", window.label());
                w.put("budget", window.limit());
                w.put("used", used);
                w.put("remaining", Math.max(0, window.limit() - used));
                windows.add(w);
                atLimit |= used >= window.limit();
            }
            row.put("enforced", windows);
            row.put("atLimit", atLimit);
            row.put("throttledRequests", throttled.getOrDefault(name, 0L));
            out.add(row);
        });
        return out;
    }

    /** Test seam: forget all recorded usage. */
    void reset() {
        history.clear();
        throttled.clear();
    }
}
