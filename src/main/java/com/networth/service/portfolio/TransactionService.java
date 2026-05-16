package com.networth.service.portfolio;

import com.networth.model.dto.TransactionRequest;
import com.networth.model.dto.TransactionResponse;
import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final HoldingRepository holdingRepository;
    private final CostBasisService costBasisService;

    @Transactional(readOnly = true)
    public List<TransactionResponse> getHoldingTransactions(String holdingId) {
        return transactionRepository.findByHoldingIdOrderByTransactionDateDesc(UUID.fromString(holdingId))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getUserTransactions(String userId) {
        return transactionRepository.findByUserId(UUID.fromString(userId))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public TransactionResponse addTransaction(String userId, TransactionRequest request) {
        Holding holding = holdingRepository.findById(UUID.fromString(request.getHoldingId()))
                .orElseThrow(() -> new IllegalArgumentException("Holding not found"));

        BigDecimal amount = request.getAmount() != null
                ? request.getAmount()
                : request.getQuantity().multiply(request.getPrice());

        Transaction transaction = Transaction.builder()
                .holdingId(UUID.fromString(request.getHoldingId()))
                .userId(UUID.fromString(userId))
                .transactionType(request.getTransactionType())
                .quantity(request.getTransactionType() == TransactionType.SELL
                        ? request.getQuantity().negate()
                        : request.getQuantity())
                .price(request.getPrice())
                .amount(amount)
                .fees(request.getFees() != null ? request.getFees() : BigDecimal.ZERO)
                .taxes(request.getTaxes() != null ? request.getTaxes() : BigDecimal.ZERO)
                .transactionDate(request.getTransactionDate())
                .notes(request.getNotes())
                .broker(request.getBroker())
                .build();

        transaction = transactionRepository.save(transaction);

        if (request.getTransactionType() == TransactionType.BUY
                || request.getTransactionType() == TransactionType.SIP
                || request.getTransactionType() == TransactionType.LUMPSUM) {
            costBasisService.updateBuy(holding, request.getQuantity(), request.getPrice());
        } else if (request.getTransactionType() == TransactionType.SELL) {
            costBasisService.updateSell(holding, request.getQuantity(), request.getPrice());
        }

        return toResponse(transaction);
    }

    @Transactional
    public void deleteTransaction(String transactionId) {
        transactionRepository.deleteById(UUID.fromString(transactionId));
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId().toString())
                .holdingId(transaction.getHoldingId().toString())
                .transactionType(transaction.getTransactionType())
                .quantity(transaction.getQuantity())
                .price(transaction.getPrice())
                .amount(transaction.getAmount())
                .fees(transaction.getFees())
                .taxes(transaction.getTaxes())
                .transactionDate(transaction.getTransactionDate())
                .notes(transaction.getNotes())
                .broker(transaction.getBroker())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
