package com.networth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing token blacklist and refresh token rotation.
 * Uses Redis to store revoked token JTIs (token IDs) with TTL matching the token expiration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Add a token JTI to the blacklist.
     * @param jti Token ID
     * @param expirationEpochSeconds When the token would have naturally expired
     */
    public void revokeToken(String jti, long expirationEpochSeconds) {
        if (jti == null || jti.isBlank()) return;
        long ttl = expirationEpochSeconds - Instant.now().getEpochSecond();
        if (ttl <= 0) return; // Already expired - no need to track

        String key = BLACKLIST_KEY_PREFIX + jti;
        try {
            redisTemplate.opsForValue().set(key, "revoked", ttl, TimeUnit.SECONDS);
            log.debug("Revoked token JTI: {}", jti);
        } catch (Exception e) {
            log.error("Failed to revoke token {}: {}", jti, e.getMessage());
        }
    }

    /**
     * Check if a token JTI is blacklisted.
     */
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti));
        } catch (Exception e) {
            log.error("Failed to check token revocation for JTI {}: {}", jti, e.getMessage());
            // Fail open: if Redis is down, allow the request rather than locking everyone out
            return false;
        }
    }
}
