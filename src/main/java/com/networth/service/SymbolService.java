package com.networth.service;

import com.networth.model.entity.Symbol;
import com.networth.model.entity.SymbolAlias;
import com.networth.repository.SymbolAliasRepository;
import com.networth.repository.SymbolRepository;
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

    @Transactional
    public void refreshAll() {
        refreshEquities();
        refreshMutualFunds();
        refreshBonds();
        int fixed = holdingService.backfillMissingIsins();
        log.info("Symbol refresh complete (fixed {} holding ISINs)", fixed);
    }

    @Transactional
    public void refreshEquities() {
        try {
            HttpURLConnection conn = openConnection(NSE_CSV_URL, "text/csv,application/csv", 30000);
            if (conn.getResponseCode() != 200) {
                log.warn("NSE CSV returned status {}", conn.getResponseCode());
                return;
            }

            int upserted = 0;
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

                    String key = symbol + ".NS";
                    Symbol existing = symbolRepository.findById(key).orElse(null);
                    if (existing != null) {
                        existing.setName(name);
                        existing.setSector("Equity");
                        if (isin != null) existing.setIsin(isin);
                        symbolRepository.save(existing);
                    } else {
                        symbolRepository.save(Symbol.builder()
                                .symbol(key).name(name).category("EQUITY").sector("Equity").isin(isin)
                                .build());
                    }
                    upserted++;

                    // Upsert alias
                    upsertAlias(key, "ALPHA_VANTAGE", symbol + ".BSE");
                }
            }
            log.info("Refreshed {} NSE equity symbols (upsert)", upserted);
        } catch (Exception e) {
            log.error("Failed to refresh NSE equities: {}", e.getMessage());
        }
    }

    @Transactional
    public void refreshMutualFunds() {
        try {
            HttpURLConnection conn = openConnection(MF_NAV_URL, "text/plain", 60000);
            if (conn.getResponseCode() != 200) {
                log.warn("AMFI NAV returned status {}", conn.getResponseCode());
                return;
            }

            int upserted = 0;
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

                    Symbol existing = symbolRepository.findById(isin).orElse(null);
                    if (existing != null) {
                        existing.setName(schemeName);
                        existing.setSchemeCode(schemeCode);
                        symbolRepository.save(existing);
                    } else {
                        symbolRepository.save(Symbol.builder()
                                .symbol(isin).name(schemeName).category("MUTUAL_FUND")
                                .sector("Mutual Fund").schemeCode(schemeCode)
                                .build());
                    }
                    upserted++;
                }
            }

            if (upserted == 0) {
                log.warn("No mutual fund symbols parsed from AMFI data");
                return;
            }
            log.info("Refreshed {} mutual fund symbols (upsert)", upserted);
        } catch (Exception e) {
            log.error("Failed to refresh mutual funds: {}", e.getMessage());
        }
    }

    @Transactional
    public void refreshBonds() {
        try {
            HttpURLConnection conn = openConnection(NSE_DEBT_CSV_URL, "text/csv,application/csv", 30000);
            if (conn.getResponseCode() != 200) {
                log.warn("NSE DEBT CSV returned status {}", conn.getResponseCode());
                return;
            }

            int upserted = 0;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String header = br.readLine();
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

                    String sector = "Bond";
                    if (!ipRate.isEmpty()) sector += " | Coupon: " + ipRate + "%";
                    if (!redemptionDate.isEmpty()) sector += " | Maturity: " + redemptionDate;
                    if (!faceValue.isEmpty()) sector += " | FV: ₹" + faceValue;

                    String fullName = name + (series.isEmpty() ? "" : " [" + series + "]");
                    String key = symbol + ".NS";

                    Symbol existing = symbolRepository.findById(key).orElse(null);
                    if (existing != null) {
                        existing.setName(fullName);
                        existing.setSector(sector);
                        if (isin != null) existing.setIsin(isin);
                        symbolRepository.save(existing);
                    } else {
                        symbolRepository.save(Symbol.builder()
                                .symbol(key).name(fullName).category("BOND").sector(sector).isin(isin)
                                .build());
                    }
                    upserted++;
                }
            }
            log.info("Refreshed {} NSE bond/debenture symbols (upsert)", upserted);
        } catch (Exception e) {
            log.error("Failed to refresh bonds: {}", e.getMessage());
        }
    }

    /**
     * Upsert a symbol alias — find by (symbol, source), create or update.
     */
    private void upsertAlias(String symbol, String source, String alias) {
        SymbolAlias existing = symbolAliasRepository.findBySymbolAndSource(symbol, source).orElse(null);
        if (existing != null) {
            if (!alias.equals(existing.getAlias())) {
                existing.setAlias(alias);
                symbolAliasRepository.save(existing);
            }
        } else {
            symbolAliasRepository.save(SymbolAlias.builder()
                    .symbol(symbol).source(source).alias(alias).build());
        }
    }

    private HttpURLConnection openConnection(String url, String accept, int readTimeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Accept", accept);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(readTimeout);
        return conn;
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
