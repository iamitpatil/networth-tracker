package com.networth.service;

import com.networth.model.entity.Symbol;
import com.networth.model.entity.SymbolAlias;
import com.networth.repository.SymbolAliasRepository;
import com.networth.repository.SymbolRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolService {

    private final SymbolRepository symbolRepository;
    private final SymbolAliasRepository symbolAliasRepository;
    private final com.networth.service.portfolio.HoldingService holdingService;

    private static final String NSE_CSV_URL = "https://archives.nseindia.com/content/equities/EQUITY_L.csv";
    private static final String NSE_DEBT_CSV_URL = "https://archives.nseindia.com/content/equities/DEBT.csv";
    private static final String MF_NAV_URL = "https://portal.amfiindia.com/spages/NAVAll.txt";

    // Remove @PostConstruct — symbols refreshed manually via POST /api/v1/symbols/refresh
    // @PostConstruct
    public void init() {
        refreshAll();
    }

    @Transactional
    public void refreshAll() {
        refreshEquities();
        refreshMutualFunds();
        refreshBonds();
        // Backfill ISINs on holdings using updated symbols
        int fixed = holdingService.backfillMissingIsins();
        log.info("Symbol refresh complete (fixed {} holding ISINs)", fixed);
    }

    @Transactional
    public void refreshEquities() {
        try {
            URI uri = URI.create(NSE_CSV_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "text/csv,application/csv");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            int status = conn.getResponseCode();
            if (status != 200) {
                log.warn("NSE CSV returned status {}", status);
                return;
            }

            List<Symbol> symbols = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String header = br.readLine();
                if (header == null) return;

                String line;
                while ((line = br.readLine()) != null) {
                    String[] cols = parseCsvLine(line);
                    if (cols.length < 3) continue;
                    String symbol = cols[0].trim();
                    String name = cols[1].trim().replaceAll("^\"|\"$", "");
                    String series = cols[2].trim();
                    if (!"EQ".equals(series)) continue;

                    String isin = cols.length > 6 ? cols[6].trim().replaceAll("^\"|\"$", "") : null;
                    if (isin != null && (isin.isEmpty() || isin.length() != 12)) isin = null;

                    symbols.add(Symbol.builder()
                            .symbol(symbol + ".NS")
                            .name(name)
                            .category("EQUITY")
                            .sector("Equity")
                            .isin(isin)
                            .build());
                }
            }

            // Only delete existing equities, not MFs
            List<Symbol> existingEquities = symbolRepository.findByCategory("EQUITY");
            symbolRepository.deleteAll(existingEquities);
            symbolRepository.saveAll(symbols);
            log.info("Refreshed {} NSE equity symbols", symbols.size());

            populateAliases(symbols);
        } catch (Exception e) {
            log.error("Failed to refresh NSE equities: {}", e.getMessage());
        }
    }

    private void populateAliases(List<Symbol> symbols) {
        // Clear existing aliases before re-populating to avoid unique constraint violations
        symbolAliasRepository.deleteAll();

        List<SymbolAlias> aliases = symbols.stream()
                .filter(s -> s.getCategory().equals("EQUITY"))
                .flatMap(s -> {
                    String base = s.getSymbol().replace(".NS", "");
                    return java.util.stream.Stream.of(
                            SymbolAlias.builder().symbol(s.getSymbol()).source("ALPHA_VANTAGE").alias(base + ".BSE").build()
                    );
                })
                .toList();
        symbolAliasRepository.saveAll(aliases);
        log.info("Populated {} symbol aliases", aliases.size());
    }

    @Transactional
    public void refreshMutualFunds() {
        try {
            URI uri = URI.create(MF_NAV_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "text/plain");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            int status = conn.getResponseCode();
            if (status != 200) {
                log.warn("AMFI NAV returned status {}", status);
                return;
            }

            List<Symbol> symbols = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (!line.contains(";") || line.contains("Scheme Code") || line.endsWith("Fund)")) continue;

                    String[] cols = line.split(";");
                    if (cols.length < 4) continue;

                    String isin = null;
                    if (cols.length > 1 && cols[1].trim().length() == 12 && !cols[1].trim().equals("-")) {
                        isin = cols[1].trim();
                    }
                    if (isin == null && cols.length > 2 && cols[2].trim().length() == 12 && !cols[2].trim().equals("-")) {
                        isin = cols[2].trim();
                    }
                    if (isin == null) continue;

                    String schemeName = cols[3].trim();
                    if (schemeName.isEmpty()) continue;

                    String schemeCode = cols[0].trim();

                    symbols.add(Symbol.builder()
                            .symbol(isin)
                            .name(schemeName)
                            .category("MUTUAL_FUND")
                            .sector("Mutual Fund")
                            .schemeCode(schemeCode)
                            .build());
                }
            }

            if (symbols.isEmpty()) {
                log.warn("No mutual fund symbols parsed from AMFI data");
                return;
            }

            symbolRepository.saveAll(symbols);
            log.info("Refreshed {} mutual fund symbols", symbols.size());
        } catch (Exception e) {
            log.error("Failed to refresh mutual funds: {}", e.getMessage());
        }
    }

    @Transactional
    public void refreshBonds() {
        try {
            URI uri = URI.create(NSE_DEBT_CSV_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Accept", "text/csv,application/csv");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            int status = conn.getResponseCode();
            if (status != 200) {
                log.warn("NSE DEBT CSV returned status {}", status);
                return;
            }

            List<Symbol> symbols = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String header = br.readLine(); // skip header
                if (header == null) return;

                String line;
                while ((line = br.readLine()) != null) {
                    String[] cols = parseCsvLine(line);
                    if (cols.length < 14) continue;

                    String symbol = cols[0].trim();
                    String name = cols[1].trim().replaceAll("^\"|\"$", "");
                    String series = cols[2].trim();
                    String faceValue = cols[3].trim();
                    String ipRate = cols.length > 6 ? cols[6].trim() : "";
                    String redemptionDate = cols.length > 9 ? cols[9].trim() : "";
                    String isin = cols.length > 14 ? cols[14].trim().replaceAll("^\"|\"$", "") : null;

                    if (symbol.isEmpty() || name.isEmpty()) continue;
                    if (isin != null && (isin.isEmpty() || isin.length() != 12)) isin = null;

                    // Build sector string with bond metadata for frontend display
                    String sector = "Bond";
                    if (!ipRate.isEmpty()) sector += " | Coupon: " + ipRate + "%";
                    if (!redemptionDate.isEmpty()) sector += " | Maturity: " + redemptionDate;
                    if (!faceValue.isEmpty()) sector += " | FV: ₹" + faceValue;

                    symbols.add(Symbol.builder()
                            .symbol(symbol + ".NS")
                            .name(name + (series.isEmpty() ? "" : " [" + series + "]"))
                            .category("BOND")
                            .sector(sector)
                            .isin(isin)
                            .build());
                }
            }

            // Only delete existing bonds, not equities or MFs
            List<Symbol> existingBonds = symbolRepository.findByCategory("BOND");
            symbolRepository.deleteAll(existingBonds);
            symbolRepository.saveAll(symbols);
            log.info("Refreshed {} NSE bond/debenture symbols", symbols.size());
        } catch (Exception e) {
            log.error("Failed to refresh bonds: {}", e.getMessage());
        }
    }

    public List<Map<String, String>> getAllSymbols() {
        return symbolRepository.findAll().stream()
                .map(this::symbolToMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, String>> getSymbolsByCategory(String category) {
        return symbolRepository.findByCategory(category.toUpperCase()).stream()
                .map(this::symbolToMap)
                .collect(Collectors.toList());
    }

    private Map<String, String> symbolToMap(Symbol s) {
        var map = new java.util.HashMap<String, String>();
        map.put("symbol", s.getSymbol());
        map.put("name", s.getName());
        map.put("category", s.getCategory());
        map.put("sector", s.getSector() != null ? s.getSector() : "");
        map.put("isin", s.getIsin() != null ? s.getIsin() : "");
        return map;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) { fields.add(sb.toString()); sb = new StringBuilder(); continue; }
            sb.append(c);
        }
        fields.add(sb.toString());
        return fields.toArray(String[]::new);
    }

}
