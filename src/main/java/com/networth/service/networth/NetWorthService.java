package com.networth.service.networth;

import com.networth.model.dto.NetWorthResponse;
import com.networth.model.entity.BankAccount;
import com.networth.model.entity.Holding;
import com.networth.model.entity.Liability;
import com.networth.model.enums.AssetType;
import com.networth.repository.BankAccountRepository;
import com.networth.repository.HoldingRepository;
import com.networth.repository.LiabilityRepository;
import com.networth.service.market.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NetWorthService {

    private final HoldingRepository holdingRepository;
    private final LiabilityRepository liabilityRepository;
    private final BankAccountRepository bankAccountRepository;
    private final PriceService priceService;

    @Transactional(readOnly = true)
    public NetWorthResponse calculateNetWorth(UUID userId) {
        List<Holding> holdings = holdingRepository.findByUserId(userId);
        List<Liability> liabilities = liabilityRepository.findByUserId(userId);
        List<BankAccount> bankAccounts = bankAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal liquidAssets = BigDecimal.ZERO;
        BigDecimal equityValue = BigDecimal.ZERO;
        BigDecimal debtValue = BigDecimal.ZERO;
        BigDecimal goldValue = BigDecimal.ZERO;
        BigDecimal realEstateValue = BigDecimal.ZERO;
        BigDecimal cashValue = BigDecimal.ZERO;
        BigDecimal cryptoValue = BigDecimal.ZERO;

        for (Holding holding : holdings) {
            updateHoldingCurrentPrice(holding);
            BigDecimal value = holding.getCurrentValue() != null ? holding.getCurrentValue() : BigDecimal.ZERO;

            totalAssets = totalAssets.add(value);

            switch (holding.getAssetType()) {
                case EQUITY, ETF -> equityValue = equityValue.add(value);
                case MUTUAL_FUND, FD, BOND, EPF, PPF, NPS -> debtValue = debtValue.add(value);
                case GOLD, SGB -> goldValue = goldValue.add(value);
                case REAL_ESTATE -> realEstateValue = realEstateValue.add(value);
                case CASH -> {
                    cashValue = cashValue.add(value);
                    liquidAssets = liquidAssets.add(value);
                }
                case CRYPTO -> cryptoValue = cryptoValue.add(value);
                default -> {}
            }

            if (isLiquid(holding.getAssetType())) {
                liquidAssets = liquidAssets.add(value);
            }
        }

        for (BankAccount ba : bankAccounts) {
            BigDecimal bal = ba.getBalance() != null ? ba.getBalance() : BigDecimal.ZERO;
            cashValue = cashValue.add(bal);
            totalAssets = totalAssets.add(bal);
            liquidAssets = liquidAssets.add(bal);
        }

        BigDecimal totalLiabilities = liabilities.stream()
                .map(Liability::getOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return NetWorthResponse.builder()
                .totalAssets(totalAssets)
                .totalLiabilities(totalLiabilities)
                .netWorth(totalAssets.subtract(totalLiabilities))
                .liquidAssets(liquidAssets)
                .equityValue(equityValue)
                .debtValue(debtValue)
                .goldValue(goldValue)
                .realEstateValue(realEstateValue)
                .cashValue(cashValue)
                .cryptoValue(cryptoValue)
                .build();
    }

    private void updateHoldingCurrentPrice(Holding holding) {
        if (holding.getCurrentValue() == null || holding.getCurrentValue().compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal currentPrice = priceService.getCurrentPrice(holding.getSymbol(), holding.getAssetType());
            if (currentPrice != null) {
                holding.setCurrentPrice(currentPrice);
                holding.setCurrentValue(holding.getQuantity().multiply(currentPrice));
            }
        }
    }

    private boolean isLiquid(AssetType assetType) {
        return switch (assetType) {
            case EQUITY, ETF, MUTUAL_FUND, CASH, FD, BOND, CRYPTO -> true;
            default -> false;
        };
    }
}
