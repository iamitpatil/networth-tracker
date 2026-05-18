package com.networth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Simple Redis-based rate limiter using sliding window counter approach.
 * Protects /auth endpoints from brute force attacks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Check if an action is allowed for the given key (e.g. IP or email).
     *
     * @param key Unique identifier for the rate limit (e.g. "login:192.168.1.1")
     * @param maxAttempts Maximum allowed attempts in the window
     * @param windowSeconds Time window in seconds
     * @return true if allowed, false if rate-limited
     */
    public boolean isAllowed(String key, int maxAttempts, int windowSeconds) {
        String redisKey = KEY_PREFIX + key;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                // First request - set expiration
                redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
            }
            if (count != null && count > maxAttempts) {
                log.warn("Rate limit exceeded for {}: {} attempts (max {})", key, count, maxAttempts);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Rate limit check failed for {}: {}", key, e.getMessage());
            // Fail open: if Redis is down, allow the request
            return true;
        }
    }

    /**
     * Reset the counter for a key (e.g. on successful login).
     */
    public void reset(String key) {
        try {
            redisTemplate.delete(KEY_PREFIX + key);
        } catch (Exception e) {
            log.error("Failed to reset rate limit for {}: {}", key, e.getMessage());
        }
    }

    /**
     * Get remaining attempts for a key.
     */
    public int getRemainingAttempts(String key, int maxAttempts) {
        try {
            String count = redisTemplate.opsForValue().get(KEY_PREFIX + key);
            if (count == null) return maxAttempts;
            return Math.max(0, maxAttempts - Integer.parseInt(count));
        } catch (Exception e) {
            return maxAttempts;
        }
    }
}
