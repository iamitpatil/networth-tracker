package com.networth.service.market.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;

@Component("google")
@Slf4j
public class GoogleNewsProvider implements MarketDataProvider {

    private final RestTemplate restTemplate;

    private static final String GOOGLE_NEWS_RSS =
            "https://news.google.com/rss/search?q=%s&hl=en-IN&gl=IN&ceid=IN:en";

    /**
     * Tried in order against the header with its day name removed. Both keep the offset and differ
     * only in how the zone is written: {@code Z} is numeric ("+0530"), {@code z} named ("GMT").
     */
    private static final List<DateTimeFormatter> RSS_DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss z", Locale.ENGLISH));

    public GoogleNewsProvider(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getName() {
        return "google";
    }

    @Override
    public Set<MarketDataType> supportedTypes() {
        return Set.of(MarketDataType.NEWS);
    }

    @Override
    public List<NewsItem> fetchNews(String query, int limit) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format(GOOGLE_NEWS_RSS, encoded);
            String xml = restTemplate.getForObject(url, String.class);
            if (xml == null || xml.isBlank()) return List.of();

            return parseRss(xml, limit);
        } catch (Exception e) {
            log.warn("Google News fetch failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    private List<NewsItem> parseRss(String xml, int limit) {
        List<NewsItem> items = new ArrayList<>();
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList itemNodes = doc.getElementsByTagName("item");
            int max = Math.min(itemNodes.getLength(), limit);
            for (int i = 0; i < max; i++) {
                Element item = (Element) itemNodes.item(i);
                String title = getElementText(item, "title");
                String link = getElementText(item, "link");
                String source = getElementText(item, "source");
                String pubDateStr = getElementText(item, "pubDate");
                String description = getElementText(item, "description");

                Instant pubDate = parseRssDate(pubDateStr);

                items.add(NewsItem.builder()
                        .title(title != null ? title : "")
                        .link(link != null ? link : "")
                        .source(source != null ? source : "Google News")
                        .pubDate(pubDate)
                        .description(description != null ? stripHtml(description) : "")
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to parse RSS XML: {}", e.getMessage());
        }
        return items;
    }

    private String getElementText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            String text = list.item(0).getTextContent();
            return text != null ? text.strip() : null;
        }
        return null;
    }

    /**
     * An article's publication time as an instant.
     *
     * <p>RFC-1123 carries an offset ({@code GMT}, {@code +0530}), and it matters: parsing into a
     * {@code LocalDateTime} dropped it, so "10:30 +0530" (05:00 UTC) compared as newer than
     * "10:00 GMT" (10:00 UTC). Feeds mix zones, and {@code NewsService} sorts on this field, so
     * the offset has to survive parsing.
     *
     * <p>The fallback accepts a numeric offset as well as a named zone. Strict RFC-1123 rejects a
     * header whose day name disagrees with its date, and a feed that gets that wrong would
     * otherwise have every article stamped with the time we happened to fetch it — a worse answer
     * than trusting the numeric date it did give us.
     */
    private Instant parseRssDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return Instant.now();
        try {
            return ZonedDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException strict) {
            // Drop the day name and retry. It is redundant with the date, and it is the part feeds
            // most often get wrong; keeping it would mean rejecting a header that is otherwise
            // perfectly clear about when the article was published.
            String withoutDayName = dateStr.substring(dateStr.indexOf(',') + 1).strip();
            for (DateTimeFormatter format : RSS_DATE_FORMATS) {
                try {
                    return ZonedDateTime.parse(withoutDayName, format).toInstant();
                } catch (DateTimeParseException ignored) {
                    // try the next shape
                }
            }
        }
        log.debug("Unparseable RSS pubDate '{}'; using now", dateStr);
        return Instant.now();
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").strip();
    }
}
