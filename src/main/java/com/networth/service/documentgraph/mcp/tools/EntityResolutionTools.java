package com.networth.service.documentgraph.mcp.tools;

import com.networth.model.entity.BankAccount;
import com.networth.model.entity.CreditCard;
import com.networth.model.entity.Holding;
import com.networth.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntityResolutionTools {

    private final HoldingRepository holdingRepository;
    private final CreditCardRepository creditCardRepository;
    private final BankAccountRepository bankAccountRepository;
    private final SalaryRepository salaryRepository;

    @Tool(name = "resolve_entity", description = "Match extracted document entities against the user's existing portfolio, accounts, and cards")
    public List<Map<String, Object>> resolveEntity(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Extracted data map from the document") Map<String, Object> extractedData) {
        UUID uid = UUID.fromString(userId);
        List<Map<String, Object>> matches = new ArrayList<>();
        if (extractedData == null || extractedData.isEmpty()) return matches;

        String isin = stringField(extractedData, "isin");
        if (isin != null) matches.addAll(findHoldingsByIsin(uid, isin));

        String symbol = stringField(extractedData, "symbol");
        if (symbol != null) matches.addAll(findHoldingsBySymbol(uid, symbol));

        String cardIssuer = stringField(extractedData, "cardIssuer");
        String cardLastFour = stringField(extractedData, "cardLastFourDigits");
        if (cardIssuer != null || cardLastFour != null)
            matches.addAll(findCreditCards(uid, cardIssuer, cardLastFour));

        String bankName = stringField(extractedData, "bankName");
        String accountNumber = stringField(extractedData, "accountNumber");
        if (bankName != null || accountNumber != null)
            matches.addAll(findBankAccounts(uid, bankName, accountNumber));

        String employerName = stringField(extractedData, "employerName");
        if (employerName != null) matches.addAll(findEmployer(uid, employerName));

        Object entitiesObj = extractedData.get("entities");
        if (entitiesObj instanceof List<?> entities) {
            for (Object e : entities) {
                if (e instanceof Map<?, ?> entity) {
                    String type = stringField((Map<String, Object>) entity, "type");
                    String name = stringField((Map<String, Object>) entity, "name");
                    String identifier = stringField((Map<String, Object>) entity, "identifier");
                    if (type != null && name != null)
                        matches.addAll(resolveGeneric(uid, type, name, identifier));
                }
            }
        }
        return matches;
    }

    @Tool(name = "search_holdings", description = "Search user's investment holdings by symbol or name")
    public List<Map<String, Object>> searchHoldings(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Search query (symbol or name)") String query,
            @ToolParam(description = "Maximum results to return", required = false) Integer limit) {
        UUID uid = UUID.fromString(userId);
        int max = limit != null && limit > 0 ? limit : 10;
        String q = query.toLowerCase();
        return holdingRepository.findByUserId(uid).stream()
                .filter(h -> (h.getSymbol() != null && h.getSymbol().toLowerCase().contains(q))
                        || (h.getName() != null && h.getName().toLowerCase().contains(q)))
                .limit(max).map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", h.getId().toString());
                    m.put("symbol", h.getSymbol());
                    m.put("name", h.getName());
                    m.put("qty", h.getQuantity());
                    m.put("value", h.getCurrentValue());
                    m.put("avgPrice", h.getAverageBuyPrice());
                    return m;
                }).collect(Collectors.toList());
    }

    @Tool(name = "search_accounts", description = "Search user's bank accounts by name or number")
    public List<Map<String, Object>> searchAccounts(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Search query (bank name or account name)") String query,
            @ToolParam(description = "Maximum results to return", required = false) Integer limit) {
        UUID uid = UUID.fromString(userId);
        int max = limit != null && limit > 0 ? limit : 10;
        String q = query.toLowerCase();
        return bankAccountRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(uid).stream()
                .filter(a -> (a.getBankName() != null && a.getBankName().toLowerCase().contains(q))
                        || (a.getAccountName() != null && a.getAccountName().toLowerCase().contains(q)))
                .limit(max).map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId().toString()); m.put("bankName", a.getBankName());
                    m.put("accountName", a.getAccountName());
                    m.put("accountNumber", maskAccount(a.getAccountNumber()));
                    m.put("balance", a.getBalance());
                    return m;
                }).collect(Collectors.toList());
    }

    @Tool(name = "search_credit_cards", description = "Search user's credit cards by issuer or last four digits")
    public List<Map<String, Object>> searchCreditCards(
            @ToolParam(description = "User ID (UUID)") String userId,
            @ToolParam(description = "Search query (issuer name or last 4 digits)") String query,
            @ToolParam(description = "Maximum results to return", required = false) Integer limit) {
        UUID uid = UUID.fromString(userId);
        int max = limit != null && limit > 0 ? limit : 10;
        String q = query.toLowerCase();
        return creditCardRepository.findByUserIdOrderByCreatedAtDesc(uid).stream()
                .filter(c -> (c.getCardIssuer() != null && c.getCardIssuer().toLowerCase().contains(q))
                        || (c.getCardLastFour() != null && c.getCardLastFour().contains(q)))
                .limit(max).map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId().toString()); m.put("issuer", c.getCardIssuer());
                    m.put("cardName", c.getCardName()); m.put("lastFour", c.getCardLastFour());
                    return m;
                }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> findHoldingsByIsin(UUID userId, String isin) {
        return holdingRepository.findByUserId(userId).stream()
                .filter(h -> isin.equals(h.getIsin()))
                .map(h -> entityMatch("HOLDING", h.getId(), h.getSymbol() + " (" + h.getName() + ")", 1.0))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> findHoldingsBySymbol(UUID userId, String symbol) {
        return holdingRepository.findByUserId(userId).stream()
                .filter(h -> h.getSymbol() != null &&
                        (symbol.equalsIgnoreCase(h.getSymbol())
                        || symbol.equalsIgnoreCase(h.getSymbol().replace(".NS", ""))))
                .map(h -> entityMatch("HOLDING", h.getId(), h.getSymbol() + " (" + h.getName() + ")", 0.95))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> findCreditCards(UUID userId, String issuer, String lastFour) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (CreditCard c : creditCardRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            boolean issuerMatch = issuer == null
                    || (c.getCardIssuer() != null && c.getCardIssuer().toLowerCase().contains(issuer.toLowerCase()));
            boolean lastFourMatch = lastFour == null || lastFour.equals(c.getCardLastFour());
            if (issuerMatch && lastFourMatch) {
                double conf = (issuer != null && lastFour != null) ? 1.0 : 0.7;
                String label = (c.getCardIssuer() != null ? c.getCardIssuer() : "") + " "
                        + (c.getCardName() != null ? c.getCardName() : "") + " (" + c.getCardLastFour() + ")";
                results.add(entityMatch("CREDIT_CARD", c.getId(), label.trim(), conf));
            }
        }
        return results;
    }

    private List<Map<String, Object>> findBankAccounts(UUID userId, String bankName, String accountNumber) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (BankAccount a : bankAccountRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)) {
            boolean nameMatch = bankName == null
                    || (a.getBankName() != null && a.getBankName().toLowerCase().contains(bankName.toLowerCase()));
            boolean acctMatch = accountNumber == null
                    || (a.getAccountNumber() != null && a.getAccountNumber().endsWith(accountNumber));
            if (nameMatch && acctMatch) {
                double conf = (bankName != null && accountNumber != null) ? 1.0 : 0.7;
                String label = (a.getBankName() != null ? a.getBankName() : "") + " "
                        + (a.getAccountName() != null ? a.getAccountName() : "")
                        + " (" + maskAccount(a.getAccountNumber()) + ")";
                results.add(entityMatch("BANK_ACCOUNT", a.getId(), label.trim(), conf));
            }
        }
        return results;
    }

    private List<Map<String, Object>> findEmployer(UUID userId, String employerName) {
        return salaryRepository.findByUserIdOrderByPayDateDesc(userId).stream()
                .filter(s -> s.getEmployerName() != null
                        && s.getEmployerName().toLowerCase().contains(employerName.toLowerCase()))
                .findFirst().stream()
                .map(s -> entityMatch("SALARY", s.getId(), s.getEmployerName() + " salary", 0.85))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> resolveGeneric(UUID userId, String type, String name, String identifier) {
        return switch (type.toUpperCase()) {
            case "HOLDING" -> {
                List<Map<String, Object>> r = new ArrayList<>(findHoldingsBySymbol(userId, name));
                if (identifier != null) r.addAll(findHoldingsByIsin(userId, identifier));
                yield r;
            }
            case "CARD" -> findCreditCards(userId, name, identifier);
            case "ACCOUNT" -> findBankAccounts(userId, name, identifier);
            case "EMPLOYER" -> findEmployer(userId, name);
            default -> List.of(entityMatch(type, null, name + (identifier != null ? " (" + identifier + ")" : ""), 0.0));
        };
    }

    private Map<String, Object> entityMatch(String entityType, UUID entityId, String label, double confidence) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("entityType", entityType);
        m.put("entityId", entityId != null ? entityId.toString() : null);
        m.put("entityLabel", label);
        m.put("matchConfidence", confidence);
        return m;
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
