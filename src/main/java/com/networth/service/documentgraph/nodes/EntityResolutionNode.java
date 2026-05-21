package com.networth.service.documentgraph.nodes;

import com.networth.model.entity.BankAccount;
import com.networth.model.entity.CreditCard;
import com.networth.model.entity.Holding;
import com.networth.repository.*;
import com.networth.service.documentgraph.GraphState;
import com.networth.service.documentgraph.MatchedEntity;
import com.networth.service.documentgraph.ProcessingNode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityResolutionNode implements ProcessingNode {

    private final HoldingRepository holdingRepository;
    private final CreditCardRepository creditCardRepository;
    private final BankAccountRepository bankAccountRepository;
    private final SalaryRepository salaryRepository;
    private final DematAccountRepository dematAccountRepository;

    @Override
    public String getName() {
        return "entityResolve";
    }

    @Override
    public void process(GraphState state) {
        Map<String, Object> data = state.getExtractedData();
        if (data == null || data.isEmpty()) {
            state.setMatchedEntities(List.of());
            return;
        }

        UUID userId = state.getUserId();
        List<MatchedEntity> matches = new ArrayList<>();

        // Try to match by ISIN
        String isin = stringField(data, "isin");
        if (isin != null) {
            findHoldingsByIsin(userId, isin, matches);
        }

        // Try to match by symbol
        String symbol = stringField(data, "symbol");
        if (symbol != null) {
            findHoldingsBySymbol(userId, symbol, matches);
        }

        // Try to match by card issuer + last 4
        String cardIssuer = stringField(data, "cardIssuer");
        String cardLastFour = stringField(data, "cardLastFourDigits");
        if (cardIssuer != null || cardLastFour != null) {
            findCreditCard(userId, cardIssuer, cardLastFour, matches);
        }

        // Try to match by bank name + account number
        String bankName = stringField(data, "bankName");
        String accountNumber = stringField(data, "accountNumber");
        if (bankName != null || accountNumber != null) {
            findBankAccount(userId, bankName, accountNumber, matches);
        }

        // Try to match employer name from extracted data
        String employerName = stringField(data, "employerName");
        if (employerName != null) {
            findEmployer(userId, employerName, matches);
        }

        // Try to match from entities array (generic extraction)
        Object entitiesObj = data.get("entities");
        if (entitiesObj instanceof List<?> entities) {
            for (Object e : entities) {
                if (e instanceof Map<?, ?> entity) {
                    String type = stringField((Map<String, Object>) entity, "type");
                    String name = stringField((Map<String, Object>) entity, "name");
                    String identifier = stringField((Map<String, Object>) entity, "identifier");
                    if (type != null && name != null) {
                        resolveGenericEntity(userId, type, name, identifier, matches);
                    }
                }
            }
        }

        state.setMatchedEntities(matches);
        log.debug("Resolved {} entities", matches.size());
    }

    private void findHoldingsByIsin(UUID userId, String isin, List<MatchedEntity> matches) {
        List<Holding> holdings = holdingRepository.findByUserId(userId).stream()
                .filter(h -> isin.equals(h.getIsin()))
                .toList();
        for (Holding h : holdings) {
            matches.add(MatchedEntity.builder()
                    .entityType("HOLDING")
                    .entityId(h.getId())
                    .entityLabel(h.getSymbol() + " (" + h.getName() + ")")
                    .matchConfidence(1.0)
                    .build());
        }
    }

    private void findHoldingsBySymbol(UUID userId, String symbol, List<MatchedEntity> matches) {
        List<Holding> holdings = holdingRepository.findByUserId(userId).stream()
                .filter(h -> symbol.equalsIgnoreCase(h.getSymbol())
                        || symbol.equalsIgnoreCase(h.getSymbol().replace(".NS", "")))
                .toList();
        for (Holding h : holdings) {
            matches.add(MatchedEntity.builder()
                    .entityType("HOLDING")
                    .entityId(h.getId())
                    .entityLabel(h.getSymbol() + " (" + h.getName() + ")")
                    .matchConfidence(0.95)
                    .build());
        }
    }

    private void findCreditCard(UUID userId, String issuer, String lastFour, List<MatchedEntity> matches) {
        List<CreditCard> cards = creditCardRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (CreditCard c : cards) {
            boolean issuerMatch = issuer == null || c.getCardIssuer().toLowerCase().contains(issuer.toLowerCase());
            boolean lastFourMatch = lastFour == null || lastFour.equals(c.getCardLastFour());
            if (issuerMatch && lastFourMatch) {
                double confidence = (issuer != null && lastFour != null) ? 1.0 : 0.7;
                matches.add(MatchedEntity.builder()
                        .entityType("CREDIT_CARD")
                        .entityId(c.getId())
                        .entityLabel(c.getCardIssuer() + " " + c.getCardName() + " (" + c.getCardLastFour() + ")")
                        .matchConfidence(confidence)
                        .build());
            }
        }
    }

    private void findBankAccount(UUID userId, String bankName, String accountNumber, List<MatchedEntity> matches) {
        List<BankAccount> accounts = bankAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (BankAccount a : accounts) {
            boolean nameMatch = bankName == null || a.getBankName().toLowerCase().contains(bankName.toLowerCase());
            boolean acctMatch = accountNumber == null || a.getAccountNumber().endsWith(accountNumber);
            if (nameMatch && acctMatch) {
                double confidence = (bankName != null && accountNumber != null) ? 1.0 : 0.7;
                matches.add(MatchedEntity.builder()
                        .entityType("BANK_ACCOUNT")
                        .entityId(a.getId())
                        .entityLabel(a.getBankName() + " " + a.getAccountName() + " (" + maskAccount(a.getAccountNumber()) + ")")
                        .matchConfidence(confidence)
                        .build());
            }
        }
    }

    private void findEmployer(UUID userId, String employerName, List<MatchedEntity> matches) {
        salaryRepository.findByUserIdOrderByPayDateDesc(userId).stream()
                .filter(s -> s.getEmployerName() != null
                        && s.getEmployerName().toLowerCase().contains(employerName.toLowerCase()))
                .findFirst()
                .ifPresent(s -> matches.add(MatchedEntity.builder()
                        .entityType("SALARY")
                        .entityId(s.getId())
                        .entityLabel(s.getEmployerName() + " salary")
                        .matchConfidence(0.85)
                        .build()));
    }

    private void resolveGenericEntity(UUID userId, String type, String name, String identifier, List<MatchedEntity> matches) {
        switch (type.toUpperCase()) {
            case "HOLDING" -> {
                findHoldingsBySymbol(userId, name, matches);
                if (identifier != null) findHoldingsByIsin(userId, identifier, matches);
            }
            case "CARD" -> findCreditCard(userId, name, identifier, matches);
            case "ACCOUNT" -> findBankAccount(userId, name, identifier, matches);
            case "EMPLOYER" -> findEmployer(userId, name, matches);
            default -> matches.add(MatchedEntity.builder()
                    .entityType(type)
                    .entityId(null)
                    .entityLabel(name + (identifier != null ? " (" + identifier + ")" : ""))
                    .matchConfidence(0.0)
                    .build());
        }
    }

    private String stringField(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s && !s.isBlank() ? s : null;
    }

    private String maskAccount(String number) {
        if (number == null || number.length() < 4) return number;
        return "XXXX" + number.substring(number.length() - 4);
    }
}
