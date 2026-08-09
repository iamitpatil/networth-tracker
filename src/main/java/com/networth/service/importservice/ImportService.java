package com.networth.service.importservice;

import com.networth.model.dto.HoldingRequest;
import com.networth.model.enums.AssetType;
import com.networth.model.enums.TransactionType;
import com.networth.service.portfolio.HoldingService;
import com.networth.service.portfolio.TransactionService;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final HoldingService holdingService;
    private final TransactionService transactionService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    public List<String> processZerodhaCsv(UUID userId, MultipartFile file) {
        List<String> errors = new ArrayList<>();
        List<String> created = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVReader csvReader = new CSVReader(reader)) {

            String[] headers = csvReader.readNext();
            String[] line;

            while ((line = csvReader.readNext()) != null) {
                try {
                    processZerodhaLine(userId, line, created);
                } catch (Exception e) {
                    errors.add("Row error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Zerodha CSV: {}", e.getMessage());
            errors.add("File processing error: " + e.getMessage());
        }

        return created;
    }

    public List<String> processGrowwCsv(UUID userId, MultipartFile file) {
        List<String> errors = new ArrayList<>();
        List<String> created = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVReader csvReader = new CSVReader(reader)) {

            String[] headers = csvReader.readNext();
            String[] line;

            while ((line = csvReader.readNext()) != null) {
                try {
                    processGrowwLine(userId, line, created);
                } catch (Exception e) {
                    errors.add("Row error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process Groww CSV: {}", e.getMessage());
            errors.add("File processing error: " + e.getMessage());
        }

        return created;
    }

    private void processZerodhaLine(UUID userId, String[] line, List<String> created) {
        if (line.length < 6) return;

        String symbol = line[0].trim();
        String quantity = line[1].trim();
        String avgPrice = line[2].trim();
        String currentPrice = line[3].trim();

        HoldingRequest request = HoldingRequest.builder()
                .assetType(AssetType.EQUITY)
                .symbol(symbol)
                .quantity(new BigDecimal(quantity))
                .averageBuyPrice(new BigDecimal(avgPrice))
                .build();

        holdingService.createHolding(userId.toString(), request);
        created.add(symbol);
    }

    private void processGrowwLine(UUID userId, String[] line, List<String> created) {
        if (line.length < 6) return;

        String schemeName = line[0].trim();
        String units = line[1].trim();
        String nav = line[2].trim();
        String currentNav = line[3].trim();

        HoldingRequest request = HoldingRequest.builder()
                .assetType(AssetType.MUTUAL_FUND)
                .symbol(schemeName)
                .quantity(new BigDecimal(units))
                .averageBuyPrice(new BigDecimal(nav))
                .build();

        holdingService.createHolding(userId.toString(), request);
        created.add(schemeName);
    }
}
