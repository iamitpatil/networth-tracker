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
    /**
     * Every corporate action for a symbol, not only its dividends.
     *
     * <p>The {@code &subject=Dividend} filter that used to be here threw away bonuses, splits and
     * demergers from a response that already contained them. That is why the live database held twelve
     * transactions typed {@code BUY} at a price of zero where a bonus or a demerger belonged, and why
     * nothing could tell a 1:1 bonus from a 1:2 one. Fetching everything also keeps the sync to one
     * request per symbol: at NSE's ten requests a minute, a second pass for bonuses would have turned a
     * four-hour job into eight.
     */
    private static final String CORP_ACTIONS_URL =
            NSE_BASE + "/api/corporates-corporateActions?index=equities&symbol=%s";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("Rs?\\.?\\s*([\\d.]+)\\s*(?:/-)?(\\s*Per\\s*(?:Share|Equity\\s*Share))?", Pattern.CASE_INSENSITIVE);

    /**
     * {@code Bonus 1:1}, {@code Bonus Issue 1:2}, {@code Bonus 4:1}.
     *
     * <p>NSE writes the ratio as new:held, so 1:1 means one new share for each held and the count
     * doubles, while 4:1 means four new per one held and it becomes five times.
     */
    private static final Pattern BONUS_PATTERN =
            Pattern.compile("bonus\\D{0,20}?(\\d+)\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    /**
     * {@code Face Value Split From Rs 10 To Rs 2}, {@code Face Value Split (Sub-Division) - From Rs 2 Per
     * Share To Re 1 Per Share}.
     *
     * <p>The multiplier is old face value over new, so Rs 2 to Re 1 doubles the share count. Read from the
     * face values rather than any ratio in the text, because the text does not always carry one.
     */
    private static final Pattern SPLIT_PATTERN = Pattern.compile(
            "split.*?from\\s*(?:rs\\.?|re\\.?)\\s*([\\d.]+).*?to\\s*(?:rs\\.?|re\\.?)\\s*([\\d.]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

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

    /**
     * Dividends only, filtered out of the full corporate-action list.
     *
     * <p>Kept for the {@code DIVIDEND} provider chain. Everything now comes from one request, so this and
     * {@link #fetchCorporateActions} cost the same and never disagree.
     */
    @Override
    public List<DividendEvent> fetchDividends(String symbol) {
        List<CorporateActionEvent> all = fetchCorporateActions(symbol);
        if (all == null) {
            return List.of();
        }
        return all.stream()
                .filter(e -> "DIVIDEND".equals(e.getEventType()))
                .map(e -> DividendEvent.builder()
                        .symbol(e.getSymbol())
                        .isin(e.getIsin())
                        .amountPerShare(e.getAmountPerShare())
                        .dividendType(e.getEventSubtype().isEmpty() ? "Final" : e.getEventSubtype())
                        .exDate(e.getExDate())
                        .recordDate(e.getRecordDate())
                        .description(e.getDescription())
                        .source(e.getSource())
                        .build())
                .toList();
    }

    /**
     * Every corporate action NSE publishes for a symbol, in one request.
     *
     * <p>An empty list and null mean different things, and the event sync depends on the difference: an
     * empty list is NSE answering that this company has announced nothing, which lets the symbol be marked
     * done forever, while null means the request failed and it must be retried. Collapsing the two would
     * let an outage masquerade as "this company pays nothing".
     *
     * @return the events, empty if NSE reported none, or null if the request failed
     */
    @SuppressWarnings("unchecked")
    public List<CorporateActionEvent> fetchCorporateActions(String symbol) {
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
                log.warn("NSE corporate actions for {} returned {}", symbol, response.getStatusCode());
                return null;
            }

            List<Map<String, Object>> data = response.getBody();
            List<CorporateActionEvent> events = new ArrayList<>();

            for (Map<String, Object> item : data) {
                String subject = (String) item.getOrDefault("subject", "");
                LocalDate exDate = parseNseDate((String) item.getOrDefault("exDate", ""));
                if (exDate == null) continue;

                CorporateActionEvent event = classify(symbol, subject,
                        (String) item.getOrDefault("isin", ""),
                        exDate, parseNseDate((String) item.getOrDefault("recDate", "")));
                if (event != null) {
                    events.add(event);
                }
            }
            return events;
        } catch (Exception e) {
            log.warn("NSE corporate action fetch failed for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Turn one NSE subject line into a typed event, or null if it is not one we can act on.
     *
     * <p>Order matters. A subject can mention both a bonus and a split, and can mention rupees while being
     * neither, so bonus and split are tested before falling back to a rupee amount. Anything unrecognised
     * returns null rather than a guess: a wrong {@code ratio} silently corrupts every later capital-gains
     * figure for the symbol, which is worse than not storing the event at all.
     */
    private CorporateActionEvent classify(String symbol, String subject, String isin,
                                          LocalDate exDate, LocalDate recDate) {
        String text = subject == null ? "" : subject.trim();
        String lower = text.toLowerCase(Locale.ENGLISH);

        CorporateActionEvent.CorporateActionEventBuilder base = CorporateActionEvent.builder()
                .symbol(symbol).isin(isin).exDate(exDate).recordDate(recDate)
                .description(text).source("NSE").eventSubtype("");

        Matcher bonus = BONUS_PATTERN.matcher(text);
        if (bonus.find()) {
            BigDecimal newShares = new BigDecimal(bonus.group(1));
            BigDecimal held = new BigDecimal(bonus.group(2));
            if (held.signum() > 0) {
                // new:held, so the holding is multiplied by (held + new) / held: 1:1 doubles, 4:1 quintuples.
                return base.eventType("BONUS")
                        .ratio(held.add(newShares).divide(held, 8, java.math.RoundingMode.HALF_UP))
                        .build();
            }
        }

        Matcher split = SPLIT_PATTERN.matcher(text);
        if (split.find()) {
            BigDecimal from = new BigDecimal(split.group(1));
            BigDecimal to = new BigDecimal(split.group(2));
            if (to.signum() > 0 && from.compareTo(to) > 0) {
                // Old face value over new: Rs 2 to Re 1 doubles the count.
                return base.eventType("SPLIT")
                        .ratio(from.divide(to, 8, java.math.RoundingMode.HALF_UP))
                        .build();
            }
        }

        if (lower.contains("demerger") || lower.contains("scheme of arrangement")) {
            // No ratio: a demerger's entitlement relates two different companies, so the multiplier
            // belongs to the pairing rather than to this row. CorporateActionReclassifier supplies it.
            return base.eventType("DEMERGER").build();
        }

        BigDecimal amount = extractAmount(text);
        if (amount != null && amount.signum() > 0 && lower.contains("dividend")) {
            String subtype = lower.contains("interim") ? "Interim"
                    : lower.contains("special") ? "Special"
                    : "Final";
            return base.eventType("DIVIDEND").eventSubtype(subtype).amountPerShare(amount).build();
        }

        return null;
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
