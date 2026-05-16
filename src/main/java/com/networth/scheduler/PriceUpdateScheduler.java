package com.networth.scheduler;

import com.networth.model.entity.Holding;
import com.networth.model.entity.User;
import com.networth.model.enums.AssetType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.UserRepository;
import com.networth.service.NetWorthHistoryService;
import com.networth.service.market.PriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceUpdateScheduler {

    private final HoldingRepository holdingRepository;
    private final PriceService priceService;
    private final NetWorthHistoryService historyService;
    private final UserRepository userRepository;

    @Scheduled(fixedRate = 900000)
    public void updateEquityPrices() {
        LocalDate today = LocalDate.now();
        if (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return;
        }

        log.info("Starting equity price update...");
        List<User> users = userRepository.findAll();
        int totalUpdated = 0;
        for (User user : users) {
            List<Holding> equityHoldings = holdingRepository.findByUserIdAndAssetType(user.getId(), AssetType.EQUITY);
            for (Holding holding : equityHoldings) {
                try {
                    priceService.refreshPrice(holding.getSymbol(), holding.getAssetType());
                    totalUpdated++;
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Failed to update price for {}: {}", holding.getSymbol(), e.getMessage());
                }
            }
        }

        log.info("Equity price update completed. Updated {} prices.", totalUpdated);
    }

    @Scheduled(cron = "0 30 23 * * *")
    public void updateNavPrices() {
        log.info("Starting NAV update from AMFI...");

        List<User> users = userRepository.findAll();
        int totalUpdated = 0;
        for (User user : users) {
            List<Holding> mfHoldings = holdingRepository.findByUserIdAndAssetType(user.getId(), AssetType.MUTUAL_FUND);
            for (Holding holding : mfHoldings) {
                try {
                    priceService.refreshPrice(holding.getSymbol(), holding.getAssetType());
                    // Recalculate holding currentValue from the fetched NAV
                    BigDecimal currentPrice = priceService.getCurrentPrice(holding.getSymbol(), holding.getAssetType());
                    if (currentPrice != null) {
                        holding.setCurrentPrice(currentPrice);
                        holding.setCurrentValue(holding.getQuantity().multiply(currentPrice));
                        BigDecimal costBasis = holding.getQuantity().multiply(holding.getAverageBuyPrice());
                        holding.setUnrealizedPnl(holding.getCurrentValue().subtract(costBasis));
                        holdingRepository.save(holding);
                    }
                    totalUpdated++;
                } catch (Exception e) {
                    log.error("Failed to update NAV for {}: {}", holding.getSymbol(), e.getMessage());
                }
            }
        }

        log.info("NAV update completed. Updated {} NAVs.", totalUpdated);
    }

    @Scheduled(cron = "0 0 1 * * *")
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
