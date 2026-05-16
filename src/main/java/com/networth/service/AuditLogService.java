package com.networth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String AUDIT_KEY_PREFIX = "audit:";
    private static final long AUDIT_RETENTION_DAYS = 90;

    public void log(UUID userId, String action, String entityType, String entityId, Map<String, Object> details) {
        String key = AUDIT_KEY_PREFIX + userId;

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        entry.put("action", action);
        entry.put("entityType", entityType);
        entry.put("entityId", entityId);
        entry.put("details", details != null ? details : Map.of());

        redisTemplate.opsForList().rightPush(key, entry);
        redisTemplate.opsForList().trim(key, -1000, -1);
        redisTemplate.expire(key, AUDIT_RETENTION_DAYS, TimeUnit.DAYS);

        log.info("Audit: userId={}, action={}, entityType={}, entityId={}", userId, action, entityType, entityId);
    }

    public List<Map<String, Object>> getAuditLogs(UUID userId, int limit) {
        String key = AUDIT_KEY_PREFIX + userId;
        Long size = redisTemplate.opsForList().size(key);

        if (size == null || size == 0) {
            return List.of();
        }

        int start = Math.max(0, size.intValue() - limit);
        List<Object> entries = redisTemplate.opsForList().range(key, start, -1);

        List<Map<String, Object>> logs = new ArrayList<>();
        for (Object entry : entries) {
            @SuppressWarnings("unchecked")
            Map<String, Object> logEntry = (Map<String, Object>) entry;
            logs.add(logEntry);
        }

        return logs;
    }
}
