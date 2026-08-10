package com.networth.service.importservice;

import com.networth.model.dto.CorporateActionRequest;
import com.networth.model.dto.HoldingRequest;
import com.networth.model.dto.TransactionRequest;
import com.networth.model.entity.DematAccount;
import com.networth.model.entity.Holding;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.CorporateActionType;
import com.networth.model.enums.TransactionType;
import com.networth.repository.DematAccountRepository;
import com.networth.repository.HoldingRepository;
import com.networth.service.portfolio.CorporateActionService;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.TransactionService;
import com.networth.service.market.MarketCalendar;
import com.opencsv.CSVReader;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Bulk import of transactions from a CSV.
 *
 * <p>Distinct from {@link ImportService}, which ingests broker-specific holding statements and
 * creates positions without any transaction history. This takes a plain transaction ledger and
 * records each row as a transaction, so cost basis, holding period and capital gains all have
 * something to work from.
 *
 * <p>Rows are independent: a valid row is imported even when its neighbour fails, and every
 * failure is reported with its row number and reason. Rejecting a 200-row file over one
 * mistyped date would waste the other 199.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionImportService {

    /** Column order of the template offered by {@link #sampleCsv()}. */
    static final String[] COLUMNS = {
            "symbol", "assetType", "transactionType", "quantity", "price", "transactionDate",
            "ratio", "broker", "notes"
    };

    /**
     * Corporate actions a CSV row can express.
     *
     * <p>Demergers are deliberately absent: one writes to two holdings and needs a cost
     * apportionment percentage from the company's scheme document. Squeezing that into a flat
     * row invites a silently wrong cost basis, so it is directed to the Holdings form instead.
     */
    private static final Set<TransactionType> IMPORTABLE_ACTIONS =
            Set.of(TransactionType.BONUS, TransactionType.SPLIT);

    /** Accepted date forms, tried in order. Time is optional. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,                       // 2025-04-01
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));

    private final HoldingRepository holdingRepository;
    private final DematAccountRepository dematAccountRepository;
    private final HoldingService holdingService;
    private final TransactionService transactionService;
    private final CorporateActionService corporateActionService;

    /**
     * Checks each row's ticker before anything is written.
     *
     * <p>Called from {@code importRow}, deliberately not from inside {@code findOrCreateHolding} —
     * see the note there about rollback-only transactions.
     */
    private final com.networth.service.SymbolValidator symbolValidator;

    @Getter
    @Builder
    public static class ImportResult {
        private int totalRows;
        private int imported;
        private int failed;
        private List<String> createdHoldings;
        private List<RowError> errors;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("totalRows", totalRows);
            m.put("imported", imported);
            m.put("failed", failed);
            m.put("createdHoldings", createdHoldings);
            m.put("errors", errors.stream().map(RowError::toMap).toList());
            return m;
        }
    }

    @Getter
    @Builder
    public static class RowError {
        private int row;
        private String symbol;
        private String reason;

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("row", row);
            m.put("symbol", symbol);
            m.put("reason", reason);
            return m;
        }
    }

    /**
     * The template a user downloads, so the expected columns are never guesswork.
     *
     * <p>{@code ratio} is only read for SPLIT rows, where it is "shares before:shares after".
     * BONUS rows put the number of free shares allotted in {@code quantity} and leave
     * {@code price} at 0, matching how a demat statement reports them.
     */
    public String sampleCsv() {
        String today = LocalDate.now(MarketCalendar.ZONE).toString();
        return String.join(",", COLUMNS) + "\n"
                + "RELIANCE,EQUITY,BUY,10,2850.50," + today + ",,Zerodha,Optional note\n"
                + "RELIANCE,EQUITY,SELL,4,2990.00," + today + ",,Zerodha,\n"
                + "GOLDBEES,ETF,BUY,25,62.40," + today + ",,,\n"
                + "PARAGPARIKHFLEXICAP,MUTUAL_FUND,SIP,12.5,78.20," + today + ",,,Monthly SIP\n"
                + "INFY,EQUITY,BUY,20,1580.00," + today + ",,,\n"
                + "INFY,EQUITY,BONUS,5,0," + today + ",,,Free shares - price stays 0\n"
                + "INFY,EQUITY,SPLIT,0,0," + today + ",1:2,,Each share becomes two\n";
    }

    /**
     * Imports each row as a transaction, creating the holding when the symbol is new.
     *
     * <p>A newly created holding starts at zero quantity and is built up by the transactions
     * themselves. Creating it with the row's quantity would record an opening lot on top of
     * the very transaction being imported, leaving the ledger at twice the position.
     */
    @Transactional
    public ImportResult importTransactions(UUID userId, MultipartFile file) {
        List<RowError> errors = new ArrayList<>();
        Set<String> createdHoldings = new LinkedHashSet<>();
        int imported = 0;
        int rowNumber = 1;   // row 1 is the header

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVReader csv = new CSVReader(reader)) {

            String[] header = csv.readNext();
            if (header == null) {
                throw new IllegalArgumentException("The file is empty");
            }
            Map<String, Integer> index = mapColumns(header);

            String[] line;
            while ((line = csv.readNext()) != null) {
                rowNumber++;
                if (isBlank(line)) {
                    continue;
                }
                String symbol = value(line, index, "symbol");
                try {
                    importRow(userId, line, index, createdHoldings);
                    imported++;
                } catch (Exception e) {
                    // Row-level failures are data problems, not bugs; report and carry on.
                    errors.add(RowError.builder().row(rowNumber).symbol(symbol)
                            .reason(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                            .build());
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Transaction import failed for {}: {}", userId, e.getMessage());
            throw new IllegalArgumentException("Could not read the file: " + e.getMessage());
        }

        return ImportResult.builder()
                .totalRows(rowNumber - 1)
                .imported(imported)
                .failed(errors.size())
                .createdHoldings(new ArrayList<>(createdHoldings))
                .errors(errors)
                .build();
    }

    private void importRow(UUID userId, String[] line, Map<String, Integer> index, Set<String> createdHoldings) {
        String symbol = require(value(line, index, "symbol"), "symbol");
        AssetType assetType = parseEnum(AssetType.class, require(value(line, index, "assetType"), "assetType"), "assetType");
        TransactionType type = parseEnum(TransactionType.class,
                require(value(line, index, "transactionType"), "transactionType"), "transactionType");
        LocalDateTime when = parseDate(require(value(line, index, "transactionDate"), "transactionDate"));

        // Validated here, before anything transactional is touched, and that placement is load-bearing.
        // HoldingService.createHolding is @Transactional and joins this method's transaction, so a
        // rejection thrown from inside it would mark the shared transaction rollback-only — the caller
        // would still catch it and record a row error, then fail its own commit with
        // UnexpectedRollbackException. One bad row would take the whole file down, which is the opposite
        // of the per-row reporting this importer is built around.
        symbol = symbolValidator.requireKnown(symbol, assetType, null).symbol();

        if (type.isCorporateAction()) {
            importCorporateAction(userId, line, index, symbol, assetType, type, when, createdHoldings);
            return;
        }

        BigDecimal quantity = parsePositive(require(value(line, index, "quantity"), "quantity"), "quantity");
        BigDecimal price = parseAmount(require(value(line, index, "price"), "price"), "price");

        Holding holding = findOrCreateHolding(userId, symbol, assetType, createdHoldings);

        transactionService.addTransaction(userId.toString(), TransactionRequest.builder()
                .holdingId(holding.getId().toString())
                .transactionType(type)
                .quantity(quantity)
                .price(price)
                .transactionDate(when)
                .broker(emptyToNull(value(line, index, "broker")))
                .notes(emptyToNull(value(line, index, "notes")))
                .build());
    }

    /**
     * A BONUS or SPLIT row, routed through {@link CorporateActionService} so the position and the
     * ledger are adjusted the same way as they are from the UI.
     *
     * <p>An existing holding is required: a bonus or split has to act on shares you already
     * hold. Creating one here would produce free shares from nothing.
     */
    private void importCorporateAction(UUID userId, String[] line, Map<String, Integer> index,
                                       String symbol, AssetType assetType, TransactionType type,
                                       LocalDateTime when, Set<String> createdHoldings) {
        if (!IMPORTABLE_ACTIONS.contains(type)) {
            throw new IllegalArgumentException(type + " cannot be imported from a CSV because it "
                    + "affects two holdings and needs the cost apportionment published by the "
                    + "company. Apply it from the holding's Corporate action form instead.");
        }
        Holding holding = holdingRepository.findByUserId(userId).stream()
                .filter(h -> symbol.equalsIgnoreCase(h.getSymbol()) && h.getAssetType() == assetType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("You hold no " + symbol
                        + ", so there is nothing for a " + type + " to apply to. Import the "
                        + "purchase first — order matters, and rows are applied top to bottom."));

        String notes = emptyToNull(value(line, index, "notes"));
        CorporateActionRequest.CorporateActionRequestBuilder request = CorporateActionRequest.builder()
                .actionDate(when.toLocalDate())
                .notes(notes);

        if (type == TransactionType.BONUS) {
            request.type(CorporateActionType.BONUS)
                    // Absolute allotment, as a demat statement reports it.
                    .sharesReceived(parsePositive(require(value(line, index, "quantity"), "quantity"), "quantity"));
        } else {
            BigDecimal[] ratio = parseRatio(require(value(line, index, "ratio"), "ratio"));
            request.type(CorporateActionType.SPLIT).fromQuantity(ratio[0]).toQuantity(ratio[1]);
        }

        corporateActionService.apply(userId.toString(), holding.getId().toString(), request.build());
    }

    private Holding findOrCreateHolding(UUID userId, String symbol, AssetType assetType, Set<String> created) {
        Optional<Holding> existing = holdingRepository.findByUserId(userId).stream()
                .filter(h -> symbol.equalsIgnoreCase(h.getSymbol()) && h.getAssetType() == assetType)
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        HoldingRequest.HoldingRequestBuilder request = HoldingRequest.builder()
                .assetType(assetType)
                .symbol(symbol)
                .name(symbol)
                // Zero, deliberately: the imported transactions build the position up.
                .quantity(BigDecimal.ZERO)
                .averageBuyPrice(BigDecimal.ZERO);

        if (requiresDemat(assetType)) {
            request.dematAccountId(defaultDematAccount(userId, assetType).getId().toString());
        }

        String holdingId = holdingService.createHolding(userId.toString(), request.build()).getId();
        created.add(symbol);
        return holdingRepository.findById(UUID.fromString(holdingId))
                .orElseThrow(() -> new IllegalStateException("Holding " + symbol + " could not be read back"));
    }

    /** Mirrors HoldingService's rule; equity, ETF and MF holdings need a demat account. */
    private boolean requiresDemat(AssetType assetType) {
        return assetType == AssetType.EQUITY || assetType == AssetType.ETF || assetType == AssetType.MUTUAL_FUND;
    }

    private DematAccount defaultDematAccount(UUID userId, AssetType assetType) {
        List<DematAccount> accounts = dematAccountRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (accounts.isEmpty()) {
            throw new IllegalArgumentException(assetType + " needs a demat account, and none is set up. "
                    + "Add one under Accounts first, or import only asset types that do not require one.");
        }
        return accounts.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsDefault()))
                .findFirst()
                .orElse(accounts.get(0));
    }

    // ── parsing ───────────────────────────────────────────────────────

    private Map<String, Integer> mapColumns(String[] header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            index.put(normalise(header[i]), i);
        }
        List<String> missing = new ArrayList<>();
        for (String required : List.of("symbol", "assetType", "transactionType", "quantity", "price", "transactionDate")) {
            if (!index.containsKey(normalise(required))) {
                missing.add(required);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("The file is missing required column(s): " + String.join(", ", missing)
                    + ". Download the sample to see the expected format.");
        }
        return index;
    }

    /** Tolerant of case, spaces, underscores and a UTF-8 byte-order mark from Excel. */
    private String normalise(String header) {
        return header == null ? "" : header.replace("﻿", "").trim().toLowerCase().replaceAll("[ _-]", "");
    }

    private String value(String[] line, Map<String, Integer> index, String column) {
        Integer i = index.get(normalise(column));
        if (i == null || i >= line.length || line[i] == null) {
            return "";
        }
        return line[i].trim();
    }

    private boolean isBlank(String[] line) {
        return Arrays.stream(line).allMatch(c -> c == null || c.isBlank());
    }

    private String require(String value, String column) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(column + " is required");
        }
        return value;
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String column) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(column + " '" + value + "' is not valid. Expected one of: "
                    + String.join(", ", Arrays.stream(type.getEnumConstants()).map(Enum::name).toList()));
        }
    }

    private BigDecimal parseAmount(String value, String column) {
        try {
            // Tolerate thousands separators and a currency prefix pasted from a statement.
            BigDecimal parsed = new BigDecimal(value.replace(",", "").replace("₹", "").replace("Rs.", "").trim());
            if (parsed.signum() < 0) {
                throw new IllegalArgumentException(column + " must not be negative");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(column + " '" + value + "' is not a number");
        }
    }

    private BigDecimal parsePositive(String value, String column) {
        BigDecimal parsed = parseAmount(value, column);
        if (parsed.signum() == 0) {
            throw new IllegalArgumentException(column + " must be greater than zero");
        }
        return parsed;
    }

    /**
     * A "before:after" split ratio. Accepts "1:2", "1-2" and "1/2", because all three turn up in
     * statements and the separator is not the interesting part.
     */
    private BigDecimal[] parseRatio(String value) {
        String[] parts = value.trim().split("\\s*[:/\\-]\\s*");
        if (parts.length != 2) {
            throw new IllegalArgumentException("ratio '" + value + "' should be shares before to "
                    + "shares after, e.g. 1:2 for a share that splits into two");
        }
        BigDecimal from = parsePositive(parts[0], "ratio (shares before)");
        BigDecimal to = parsePositive(parts[1], "ratio (shares after)");
        if (from.compareTo(to) == 0) {
            throw new IllegalArgumentException("ratio '" + value + "' changes nothing");
        }
        return new BigDecimal[]{from, to};
    }

    private LocalDateTime parseDate(String value) {
        String cleaned = value.trim();
        // An ISO timestamp passes straight through.
        try {
            return LocalDateTime.parse(cleaned);
        } catch (DateTimeParseException ignored) {
            // fall through to date-only forms
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleaned, format).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // try the next
            }
        }
        throw new IllegalArgumentException("transactionDate '" + value
                + "' is not a recognised date. Use YYYY-MM-DD.");
    }
}
