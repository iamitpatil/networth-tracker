package com.networth.service;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SIPCalendarService {

    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getSIPCalendar(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        List<SIPInfo> sipInfos = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDate nextMonth = today.plusMonths(1);

        for (Holding holding : holdings) {
            List<Transaction> sipTxns = transactionRepository
                    .findByHoldingId(holding.getId())
                    .stream()
                    .filter(t -> t.getTransactionType() == TransactionType.SIP)
                    .sorted(Comparator.comparing(Transaction::getTransactionDate))
                    .toList();

            if (sipTxns.isEmpty()) continue;

            Transaction lastSIP = sipTxns.getLast();
            LocalDate lastSIPDate = lastSIP.getTransactionDate().toLocalDate();

            int sipDayOfMonth = lastSIPDate.getDayOfMonth();
            LocalDate nextSIPDate = calculateNextSIPDate(today, sipDayOfMonth);

            boolean isDue = !nextSIPDate.isAfter(today.plusDays(7));
            boolean isMissed = nextSIPDate.isBefore(today) && !wasSIPMade(today, holding.getId(), sipTxns);

            sipInfos.add(SIPInfo.builder()
                    .holdingId(holding.getId().toString())
                    .symbol(holding.getSymbol())
                    .name(holding.getName())
                    .amount(lastSIP.getAmount())
                    .sipDayOfMonth(sipDayOfMonth)
                    .nextSIPDate(nextSIPDate)
                    .lastSIPDate(lastSIPDate)
                    .totalSIPs(sipTxns.size())
                    .isDue(isDue)
                    .isMissed(isMissed)
                    .build());
        }

        BigDecimal totalMonthlySIP = sipInfos.stream()
                .map(SIPInfo::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long upcomingCount = sipInfos.stream().filter(s -> s.isDue()).count();
        long missedCount = sipInfos.stream().filter(s -> s.isMissed()).count();

        return Map.of(
                "totalMonthlySIP", totalMonthlySIP,
                "upcomingSIPs", upcomingCount,
                "missedSIPs", missedCount,
                "sipList", sipInfos
        );
    }

    private LocalDate calculateNextSIPDate(LocalDate today, int dayOfMonth) {
        try {
            LocalDate next = today.withDayOfMonth(Math.min(dayOfMonth, today.lengthOfMonth()));
            if (!next.isBefore(today)) {
                return next;
            }
            return today.plusMonths(1).withDayOfMonth(Math.min(dayOfMonth, today.plusMonths(1).lengthOfMonth()));
        } catch (Exception e) {
            return today.plusMonths(1).withDayOfMonth(1);
        }
    }

    private boolean wasSIPMade(LocalDate today, UUID holdingId, List<Transaction> sipTxns) {
        LocalDate monthStart = today.withDayOfMonth(1);
        return sipTxns.stream()
                .anyMatch(t -> !t.getTransactionDate().toLocalDate().isBefore(monthStart));
    }

    @lombok.Builder
    @lombok.Getter
    public static class SIPInfo {
        private String holdingId;
        private String symbol;
        private String name;
        private BigDecimal amount;
        private int sipDayOfMonth;
        private LocalDate nextSIPDate;
        private LocalDate lastSIPDate;
        private int totalSIPs;
        private boolean isDue;
        private boolean isMissed;
    }
}
