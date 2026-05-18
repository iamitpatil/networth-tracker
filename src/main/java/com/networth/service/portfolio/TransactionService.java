package com.networth.service.portfolio;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.dto.TransactionRequest;
import com.networth.model.dto.TransactionResponse;
import com.networth.model.entity.Holding;
import com.networth.model.entity.Transaction;
import com.networth.model.enums.TransactionType;
import com.networth.repository.HoldingRepository;
import com.networth.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final HoldingRepository holdingRepository;
    private final CostBasisService costBasisService;

    @Transactional(readOnly = true)
    public List<TransactionResponse> getHoldingTransactions(String userId, String holdingId) {
        // Verify holding ownership before returning transactions
        verifyHoldingOwnership(userId, holdingId);
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
        // Verify holding belongs to user before adding transaction
        Holding holding = verifyHoldingOwnership(userId, request.getHoldingId());

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
    public void deleteTransaction(String userId, String transactionId) {
        UUID txnId;
        UUID uid;
        try {
            txnId = UUID.fromString(transactionId);
            uid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Transaction", transactionId);
        }
        Transaction transaction = transactionRepository.findById(txnId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
        if (!transaction.getUserId().equals(uid)) {
            log.warn("User {} attempted to delete transaction {} owned by {}", userId, transactionId, transaction.getUserId());
            throw new AccessDeniedException("Transaction", transactionId);
        }
        transactionRepository.delete(transaction);
    }

    /**
     * Verifies that the given holding belongs to the user.
     * @return The Holding entity if ownership is valid.
     * @throws ResourceNotFoundException if holding doesn't exist
     * @throws AccessDeniedException if user doesn't own the holding
     */
    private Holding verifyHoldingOwnership(String userId, String holdingId) {
        UUID hid;
        UUID uid;
        try {
            hid = UUID.fromString(holdingId);
            uid = UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Holding", holdingId);
        }
        Holding holding = holdingRepository.findById(hid)
                .orElseThrow(() -> new ResourceNotFoundException("Holding", holdingId));
        if (!holding.getUserId().equals(uid)) {
            log.warn("User {} attempted to access holding {} owned by {}", userId, holdingId, holding.getUserId());
            throw new AccessDeniedException("Holding", holdingId);
        }
        return holding;
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
