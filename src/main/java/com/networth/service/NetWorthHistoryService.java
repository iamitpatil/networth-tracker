package com.networth.service;

import com.networth.model.dto.NetWorthResponse;
import com.networth.service.networth.NetWorthService;
import com.networth.service.market.MarketCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NetWorthHistoryService {

    private final NetWorthService netWorthService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HISTORY_KEY_PREFIX = "networth:history:";
    private static final long HISTORY_RETENTION_DAYS = 365;

    /**
     * Insert a historical snapshot for a specific date (used for demo data seeding).
     */
    public void insertSnapshot(UUID userId, String date, Map<String, Object> values) {
        String key = HISTORY_KEY_PREFIX + userId;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("date", date);
        for (Map.Entry<String, Object> e : values.entrySet()) {
            entry.put(e.getKey(), e.getValue().toString());
        }
        redisTemplate.opsForHash().put(key, date, entry);
        redisTemplate.expire(key, HISTORY_RETENTION_DAYS, java.util.concurrent.TimeUnit.DAYS);
    }

    public void snapshotNetWorth(UUID userId) {
        NetWorthResponse snapshot = netWorthService.calculateNetWorth(userId);
        String key = HISTORY_KEY_PREFIX + userId;
        String date = LocalDate.now(MarketCalendar.ZONE).format(DateTimeFormatter.ISO_DATE);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("date", date);
        entry.put("totalAssets", snapshot.getTotalAssets().toString());
        entry.put("totalLiabilities", snapshot.getTotalLiabilities().toString());
        entry.put("netWorth", snapshot.getNetWorth().toString());
        entry.put("liquidAssets", snapshot.getLiquidAssets().toString());
        entry.put("equityValue", snapshot.getEquityValue().toString());
        entry.put("debtValue", snapshot.getDebtValue().toString());
        entry.put("goldValue", snapshot.getGoldValue().toString());
        entry.put("realEstateValue", snapshot.getRealEstateValue().toString());
        entry.put("cashValue", snapshot.getCashValue().toString());
        entry.put("cryptoValue", snapshot.getCryptoValue().toString());

        redisTemplate.opsForHash().put(key, date, entry);
        redisTemplate.expire(key, HISTORY_RETENTION_DAYS, java.util.concurrent.TimeUnit.DAYS);
    }

    public List<Map<String, Object>> getNetWorthHistory(UUID userId, int days) {
        String key = HISTORY_KEY_PREFIX + userId;
        Map<Object, Object> history = redisTemplate.opsForHash().entries(key);

        List<Map<String, Object>> sortedHistory = new ArrayList<>();
        LocalDate cutoff = LocalDate.now(MarketCalendar.ZONE).minusDays(days);

        for (Map.Entry<Object, Object> entry : history.entrySet()) {
            String date = entry.getKey().toString();
            LocalDate entryDate = LocalDate.parse(date);
            if (!entryDate.isBefore(cutoff)) {
                @SuppressWarnings("unchecked")
                Map<String, String> data = (Map<String, String>) entry.getValue();
                sortedHistory.add(Map.of(
                        "date", date,
                        "totalAssets", new BigDecimal(data.get("totalAssets")),
                        "totalLiabilities", new BigDecimal(data.get("totalLiabilities")),
                        "netWorth", new BigDecimal(data.get("netWorth")),
                        "liquidAssets", new BigDecimal(data.get("liquidAssets")),
                        "equityValue", new BigDecimal(data.get("equityValue")),
                        "debtValue", new BigDecimal(data.get("debtValue")),
                        "goldValue", new BigDecimal(data.getOrDefault("goldValue", "0")),
                        "cashValue", new BigDecimal(data.getOrDefault("cashValue", "0"))
                ));
            }
        }

        sortedHistory.sort(Comparator.comparing(m -> m.get("date").toString()));
        return sortedHistory;
    }

    public Map<String, Object> getNetWorthChange(UUID userId, int days) {
        List<Map<String, Object>> history = getNetWorthHistory(userId, days);
        if (history.size() < 2) {
            return Map.of("change", BigDecimal.ZERO, "changePercentage", BigDecimal.ZERO);
        }

        BigDecimal firstNetWorth = (BigDecimal) history.get(0).get("netWorth");
        BigDecimal lastNetWorth = (BigDecimal) history.getLast().get("netWorth");

        BigDecimal change = lastNetWorth.subtract(firstNetWorth);
        BigDecimal changePercentage = firstNetWorth.compareTo(BigDecimal.ZERO) != 0
                ? change.divide(firstNetWorth.abs(), 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return Map.of(
                "change", change,
                "changePercentage", changePercentage.setScale(2, BigDecimal.ROUND_HALF_UP),
                "startValue", firstNetWorth,
                "endValue", lastNetWorth
        );
    }
}
