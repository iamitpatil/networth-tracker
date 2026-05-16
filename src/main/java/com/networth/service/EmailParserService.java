package com.networth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailParserService {

    private final AIChatService aiChatService;
    private final ObjectMapper objectMapper;

    private static final Map<String, List<Pattern>> BANK_PATTERNS = new LinkedHashMap<>();
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:Rs\\.?|INR|\\u20B9)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BALANCE_PATTERN = Pattern.compile("(?:bal(?:ance)?|available|avl)\\s*(?:\\.?\\s*Rs\\.?|:)?\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCOUNT_LAST4 = Pattern.compile("(?:a/c|account|ac|acct|a\\./?c\\.?)\\s*(?:no|number|#|:)?\\s*.?([\\dXx*]{4,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})", Pattern.CASE_INSENSITIVE);

    public static final Set<String> BANK_SENDERS = Set.of(
            "alerts@hdfcbank.net", "alerts@icicibank.com", "alerts@sbi.co.in",
            "alerts@axisbank.com", "alerts@kotak.com", "alerts@yesbank.co.in",
            "alert@hdfcbank.net", " debit@icicibank.com", "credit@icicibank.com",
            "noreply@axisbank.com", " alerts@rblbank.com", " alerts@idfcbank.com",
            "alerts@indusind.com", " alerts@aurobindo.com", "alerts@federalbank.co.in",
            "alerts@unionbankofindia.com", "alerts@bankofbaroda.com",
            "alerts@pnb.co.in", "alerts@canarabank.com"
    );

    public ParsedEmail parse(String sender, String subject, String body, String messageId) {
        ParsedEmail result = new ParsedEmail();
        result.sender = sender;
        result.subject = subject;
        result.bodyPreview = body.length() > 500 ? body.substring(0, 500) : body;
        result.gmailMessageId = messageId;

        String text = (subject != null ? subject + " " : "") + (body != null ? body : "");

        extractAmount(text, result);
        extractBalance(text, result);
        extractAccountLast4(text, result);
        extractDate(text, result);
        inferTransactionType(subject, text, result);

        if (result.amount == null && result.balance == null) {
            tryAiFallback(sender, subject, body, result);
        }

        return result;
    }

    private void extractAmount(String text, ParsedEmail result) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        if (m.find()) {
            try {
                result.amount = new BigDecimal(m.group(1).replace(",", ""));
            } catch (Exception ignored) {}
        }
    }

    private void extractBalance(String text, ParsedEmail result) {
        Matcher m = BALANCE_PATTERN.matcher(text);
        if (m.find()) {
            try {
                result.balance = new BigDecimal(m.group(1).replace(",", ""));
            } catch (Exception ignored) {}
        }
    }

    private void extractAccountLast4(String text, ParsedEmail result) {
        Matcher m = ACCOUNT_LAST4.matcher(text);
        if (m.find()) {
            String raw = m.group(1).replaceAll("[*Xx]", "");
            if (raw.length() >= 4) {
                result.accountLast4 = raw.substring(Math.max(0, raw.length() - 4));
            }
        }
    }

    private void extractDate(String text, ParsedEmail result) {
        Matcher m = DATE_PATTERN.matcher(text);
        if (m.find()) {
            try {
                result.transactionDate = LocalDateTime.parse(m.group(1),
                        DateTimeFormatter.ofPattern("[d/M/yyyy][d-M-yyyy][d/M/yy][d-M-yy]").withLocale(Locale.ENGLISH));
            } catch (Exception ignored) {}
        }
    }

    private void inferTransactionType(String subject, String text, ParsedEmail result) {
        String upper = (subject != null ? subject.toUpperCase() : "") + " " + text.toUpperCase();
        if (upper.contains("DEBIT") || upper.contains("WITHDRAW") || upper.contains("SPENT")
                || upper.contains("PAYMENT") || upper.contains("TRANSFER OUT")
                || upper.contains("PURCHASE") || upper.contains("ATM")) {
            result.transactionType = "DEBIT";
        } else if (upper.contains("CREDIT") || upper.contains("DEPOSIT") || upper.contains("RECEIVED")
                || upper.contains("TRANSFER IN") || upper.contains("REFUND")
                || upper.contains("SALARY") || upper.contains("INTEREST")) {
            result.transactionType = "CREDIT";
        }
    }

    private void tryAiFallback(String sender, String subject, String body, ParsedEmail result) {
        try {
            String prompt = "Extract transaction info from this bank email. Return ONLY JSON with keys: amount (number), balance (number), transactionType (CREDIT/DEBIT/null), transactionDate (YYYY-MM-DD), accountLast4 (string). Use null for missing values. No explanation.\n\nFrom: "
                    + sender + "\nSubject: " + subject + "\nBody: " + (body != null ? body.substring(0, Math.min(body.length(), 2000)) : "");
            String aiResponse = aiChatService.chat("system", prompt, "import", List.of());
            String cleaned = aiResponse.replaceAll("```json\\s*|```\\s*", "").trim();
            Map<String, Object> map = objectMapper.readValue(cleaned, Map.class);
            if (map.get("amount") != null && result.amount == null) {
                result.amount = map.get("amount") instanceof Number
                        ? BigDecimal.valueOf(((Number) map.get("amount")).doubleValue()) : null;
            }
            if (map.get("balance") != null && result.balance == null) {
                result.balance = map.get("balance") instanceof Number
                        ? BigDecimal.valueOf(((Number) map.get("balance")).doubleValue()) : null;
            }
            if (map.get("transactionType") != null && result.transactionType == null) {
                result.transactionType = map.get("transactionType").toString();
            }
            if (map.get("accountLast4") != null && result.accountLast4 == null) {
                result.accountLast4 = map.get("accountLast4").toString();
            }
        } catch (Exception e) {
            log.debug("AI fallback parse failed: {}", e.getMessage());
        }
    }

    public static class ParsedEmail {
        public String gmailMessageId;
        public String sender;
        public String subject;
        public String bodyPreview;
        public BigDecimal amount;
        public BigDecimal balance;
        public String accountLast4;
        public String transactionType;
        public LocalDateTime transactionDate;
    }
}
