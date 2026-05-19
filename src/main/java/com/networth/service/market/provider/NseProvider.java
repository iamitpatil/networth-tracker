package com.networth.service.market.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.HttpCookie;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component("nse")
@Slf4j
public class NseProvider implements MarketDataProvider {

    private final RestTemplate restTemplate;

    private static final String NSE_BASE = "https://www.nseindia.com";
    private static final String CORP_ACTIONS_URL =
            NSE_BASE + "/api/corporates-corporateActions?index=equities&symbol=%s&subject=Dividend";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("Rs?\\.?\\s*([\\d.]+)\\s*(?:/-)?(\\s*Per\\s*(?:Share|Equity\\s*Share))?", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter NSE_DATE_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private String sessionCookie;
    private long sessionExpiry;

    public NseProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getName() {
        return "nse";
    }

    @Override
    public Set<MarketDataType> supportedTypes() {
        return Set.of(MarketDataType.DIVIDEND);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DividendEvent> fetchDividends(String symbol) {
        try {
            ensureSession();
            String clean = symbol.replaceAll("\\.(NS|BO)$", "");
            String url = String.format(CORP_ACTIONS_URL, clean);

            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "application/json");
            headers.set("Referer", NSE_BASE + "/");
            if (sessionCookie != null) {
                headers.set("Cookie", sessionCookie);
            }

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), List.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return List.of();
            }

            List<Map<String, Object>> data = response.getBody();
            List<DividendEvent> events = new ArrayList<>();

            for (Map<String, Object> item : data) {
                String subject = (String) item.getOrDefault("subject", "");
                String exDateStr = (String) item.getOrDefault("exDate", "");
                String recDateStr = (String) item.getOrDefault("recDate", "");
                String isin = (String) item.getOrDefault("isin", "");

                BigDecimal amount = extractAmount(subject);
                if (amount == null) continue;

                LocalDate exDate = parseNseDate(exDateStr);
                LocalDate recDate = parseNseDate(recDateStr);
                if (exDate == null) continue;

                String divType = subject.toLowerCase().contains("interim") ? "Interim"
                        : subject.toLowerCase().contains("special") ? "Special"
                        : "Final";

                events.add(DividendEvent.builder()
                        .symbol(symbol)
                        .isin(isin)
                        .amountPerShare(amount)
                        .dividendType(divType)
                        .exDate(exDate)
                        .recordDate(recDate)
                        .description(subject.trim())
                        .source("NSE")
                        .build());
            }

            return events;
        } catch (Exception e) {
            log.warn("NSE dividend fetch failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    private void ensureSession() {
        if (sessionCookie != null && System.currentTimeMillis() < sessionExpiry) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", USER_AGENT);
            headers.set("Accept", "text/html");

            ResponseEntity<String> response = restTemplate.exchange(NSE_BASE, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);

            List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (setCookies != null) {
                StringBuilder sb = new StringBuilder();
                for (String c : setCookies) {
                    String name = c.split(";")[0];
                    if (!sb.isEmpty()) sb.append("; ");
                    sb.append(name);
                }
                sessionCookie = sb.toString();
                sessionExpiry = System.currentTimeMillis() + 4 * 60 * 1000; // 4 min TTL
                log.debug("NSE session refreshed");
            }
        } catch (Exception e) {
            log.warn("Failed to get NSE session: {}", e.getMessage());
        }
    }

    private BigDecimal extractAmount(String subject) {
        if (subject == null || subject.isBlank()) return null;
        Matcher matcher = AMOUNT_PATTERN.matcher(subject);
        BigDecimal lastAmount = null;
        while (matcher.find()) {
            try {
                lastAmount = new BigDecimal(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return lastAmount;
    }

    private LocalDate parseNseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.trim(), NSE_DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}
