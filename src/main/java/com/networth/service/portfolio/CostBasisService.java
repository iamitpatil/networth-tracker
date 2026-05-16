package com.networth.service.portfolio;

import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CostBasisService {

    private final TransactionRepository transactionRepository;

    public void updateBuy(Holding holding, BigDecimal quantity, BigDecimal price) {
        BigDecimal totalCost = holding.getAverageBuyPrice()
                .multiply(holding.getQuantity())
                .add(price.multiply(quantity));

        BigDecimal totalQuantity = holding.getQuantity().add(quantity);

        holding.setQuantity(totalQuantity);
        holding.setAverageBuyPrice(totalCost.divide(totalQuantity, 4, BigDecimal.ROUND_HALF_UP));
    }

    public void updateSell(Holding holding, BigDecimal quantity, BigDecimal sellPrice) {
        BigDecimal costBasis = holding.getAverageBuyPrice().multiply(quantity);
        BigDecimal saleValue = sellPrice.multiply(quantity);
        BigDecimal realizedGain = saleValue.subtract(costBasis);

        holding.setRealizedPnl(holding.getRealizedPnl().add(realizedGain));
        holding.setQuantity(holding.getQuantity().subtract(quantity));
    }

    public BigDecimal calculateRealizedPnL(UUID holdingId) {
        List<Transaction> transactions = transactionRepository.findByHoldingId(holdingId);
        BigDecimal totalPnL = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            if (tx.getTransactionType() == com.networth.model.enums.TransactionType.SELL) {
                BigDecimal costBasis = tx.getPrice().multiply(tx.getQuantity().abs());
                BigDecimal saleValue = tx.getAmount();
                totalPnL = totalPnL.add(saleValue.subtract(costBasis));
            }
        }

        return totalPnL;
    }
}
