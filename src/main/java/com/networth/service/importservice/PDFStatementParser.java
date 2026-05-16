package com.networth.service.importservice;

import com.networth.model.dto.HoldingRequest;
import com.networth.model.dto.TransactionRequest;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PDFStatementParser {

    private final HoldingService holdingService;
    private final TransactionService transactionService;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MMM dd, yyyy")
    };

    private static final Pattern EQUITY_PATTERN = Pattern.compile(
            "([A-Z][A-Z0-9]{1,10})\\s+(\\d+\\.?\\d*)\\s+(\\d+\\.?\\d+)",
            Pattern.MULTILINE
    );

    private static final Pattern MF_PATTERN = Pattern.compile(
            "([A-Za-z\\s]+(?:Fund|Plan|Growth|Dividend))\\s+(\\d+\\.?\\d*)\\s+(\\d+\\.?\\d*)\\s+(\\d+\\.?\\d*)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    public List<String> parseCAS(UUID userId, MultipartFile file) {
        List<String> created = new ArrayList<>();
        try {
            String text = extractText(file);
            created.addAll(parseEquityHoldings(userId, text));
            created.addAll(parseMFHoldings(userId, text));
            created.addAll(parseTransactions(userId, text));
        } catch (Exception e) {
            log.error("Failed to parse CAS PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to parse CAS statement", e);
        }
        return created;
    }

    public List<String> parseBankStatement(UUID userId, MultipartFile file) {
        List<String> transactions = new ArrayList<>();
        try {
            String text = extractText(file);
            List<BankTransaction> bankTxns = parseBankTransactions(text);

            for (BankTransaction txn : bankTxns) {
                transactions.add(txn.description());
            }
        } catch (Exception e) {
            log.error("Failed to parse bank statement PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to parse bank statement", e);
        }
        return transactions;
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private List<String> parseEquityHoldings(UUID userId, String text) {
        List<String> created = new ArrayList<>();
        Matcher matcher = EQUITY_PATTERN.matcher(text);

        while (matcher.find()) {
            try {
                String symbol = matcher.group(1).trim();
                BigDecimal quantity = new BigDecimal(matcher.group(2));
                BigDecimal price = new BigDecimal(matcher.group(3));

                if (isValidSymbol(symbol) && quantity.compareTo(BigDecimal.ZERO) > 0) {
                    HoldingRequest request = HoldingRequest.builder()
                            .assetType(AssetType.EQUITY)
                            .symbol(symbol)
                            .quantity(quantity)
                            .averageBuyPrice(price)
                            .build();

                    holdingService.createHolding(userId.toString(), request);
                    created.add(symbol);
                }
            } catch (Exception e) {
                log.warn("Failed to parse equity line: {}", matcher.group());
            }
        }

        return created;
    }

    private List<String> parseMFHoldings(UUID userId, String text) {
        List<String> created = new ArrayList<>();
        Matcher matcher = MF_PATTERN.matcher(text);

        while (matcher.find()) {
            try {
                String schemeName = matcher.group(1).trim();
                BigDecimal units = new BigDecimal(matcher.group(2));
                BigDecimal nav = new BigDecimal(matcher.group(3));
                BigDecimal currentNav = new BigDecimal(matcher.group(4));

                HoldingRequest request = HoldingRequest.builder()
                        .assetType(AssetType.MUTUAL_FUND)
                        .symbol(schemeName)
                        .quantity(units)
                        .averageBuyPrice(nav)
                        .build();

                holdingService.createHolding(userId.toString(), request);
                created.add(schemeName);
            } catch (Exception e) {
                log.warn("Failed to parse MF line: {}", matcher.group());
            }
        }

        return created;
    }

    private List<String> parseTransactions(UUID userId, String text) {
        List<String> created = new ArrayList<>();
        String[] lines = text.split("\n");

        for (String line : lines) {
            if (line.toLowerCase().contains("purchase") || line.toLowerCase().contains("sale")) {
                try {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        LocalDate date = parseDate(parts[0]);
                        if (date != null) {
                            created.add("Transaction: " + line);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skipping transaction line: {}", line);
                }
            }
        }

        return created;
    }

    private List<BankTransaction> parseBankTransactions(String text) {
        List<BankTransaction> transactions = new ArrayList<>();
        String[] lines = text.split("\n");

        for (String line : lines) {
            try {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    LocalDate date = parseDate(parts[0]);
                    if (date != null) {
                        BigDecimal amount = parseAmount(parts[parts.length - 1]);
                        if (amount != null) {
                            String description = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length - 1));
                            transactions.add(new BankTransaction(date, description, amount));
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Skipping bank line: {}", line);
            }
        }

        return transactions;
    }

    private LocalDate parseDate(String text) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private BigDecimal parseAmount(String text) {
        try {
            String cleaned = text.replaceAll("[^\\d.\\-]", "");
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidSymbol(String symbol) {
        return symbol.matches("^[A-Z][A-Z0-9]{1,10}$");
    }

    private record BankTransaction(LocalDate date, String description, BigDecimal amount) {}
}
