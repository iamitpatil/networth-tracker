package com.networth.service;

import com.networth.model.entity.AuditLog;
import com.networth.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Audit logging service with dual storage:
 * - PostgreSQL for durable, queryable, regulator-compliant audit trail (long-term)
 * - Redis as hot cache for fast recent-activity queries (90 days)
 *
 * Writes are async to avoid impacting request latency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String AUDIT_KEY_PREFIX = "audit:";
    private static final long AUDIT_RETENTION_DAYS = 90;

    /**
     * Log an audit event. Writes to both PostgreSQL (durable) and Redis (cache).
     * Async to avoid blocking the request thread.
     */
    @Async
    public void log(UUID userId, String action, String entityType, String entityId, Map<String, Object> details) {
        // 1. Persist to PostgreSQL (durable, regulator-compliant)
        try {
            UUID entityUuid = null;
            if (entityId != null && !entityId.isBlank()) {
                try {
                    entityUuid = UUID.fromString(entityId);
                } catch (IllegalArgumentException e) {
                    // entityId is not a UUID - that's OK, we'll just skip it
                }
            }

            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityUuid)
                    .newValue(details)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to persist audit log to DB: {}", e.getMessage(), e);
            // Don't fail - we'll still write to Redis
        }

        // 2. Cache in Redis for fast recent queries
        try {
            String key = AUDIT_KEY_PREFIX + userId;
            Map<String, Object> entry = new LinkedHashMap<>();
            // An instant: an audit trail read from another zone must still order correctly.
            entry.put("timestamp", Instant.now().toString());
            entry.put("action", action);
            entry.put("entityType", entityType);
            entry.put("entityId", entityId);
            entry.put("details", details != null ? details : Map.of());

            redisTemplate.opsForList().rightPush(key, entry);
            redisTemplate.opsForList().trim(key, -1000, -1);
            redisTemplate.expire(key, AUDIT_RETENTION_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to cache audit log in Redis: {}", e.getMessage());
            // Not fatal - DB write is what matters
        }

        log.info("Audit: userId={}, action={}, entityType={}, entityId={}", userId, action, entityType, entityId);
    }

    /**
     * Get recent audit logs from Redis cache (fast path).
     */
    public List<Map<String, Object>> getRecentAuditLogs(UUID userId, int limit) {
        try {
            String key = AUDIT_KEY_PREFIX + userId;
            Long size = redisTemplate.opsForList().size(key);

            if (size == null || size == 0) {
                return List.of();
            }

            int start = Math.max(0, size.intValue() - limit);
            List<Object> entries = redisTemplate.opsForList().range(key, start, -1);

            if (entries == null) return List.of();

            List<Map<String, Object>> logs = new ArrayList<>();
            for (Object entry : entries) {
                @SuppressWarnings("unchecked")
                Map<String, Object> logEntry = (Map<String, Object>) entry;
                logs.add(logEntry);
            }
            return logs;
        } catch (Exception e) {
            log.warn("Failed to get audit logs from Redis: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get historical audit logs from PostgreSQL (durable, supports pagination).
     */
    public Page<AuditLog> getAuditLogsPaginated(UUID userId, int page, int size) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    /**
     * Get audit logs for a specific entity type.
     */
    public Page<AuditLog> getAuditLogsByType(UUID userId, String entityType, int page, int size) {
        return auditLogRepository.findByUserIdAndEntityTypeOrderByCreatedAtDesc(userId, entityType, PageRequest.of(page, size));
    }

    /**
     * @deprecated Use {@link #getRecentAuditLogs(UUID, int)} instead.
     */
    @Deprecated
    public List<Map<String, Object>> getAuditLogs(UUID userId, int limit) {
        return getRecentAuditLogs(userId, limit);
    }
}
