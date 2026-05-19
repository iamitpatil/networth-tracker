package com.networth.service.market;

import com.networth.model.entity.Holding;
import com.networth.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private final RestTemplate restTemplate;
    private final HoldingRepository holdingRepository;

    private static final String GOOGLE_NEWS_RSS = "https://news.google.com/rss/search?q=%s&hl=en-IN&gl=IN&ceid=IN:en";

    public List<Map<String, Object>> searchNews(String query, int limit) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format(GOOGLE_NEWS_RSS, encoded);
            String xml = restTemplate.getForObject(url, String.class);
            if (xml == null || xml.isBlank()) return List.of();

            return parseRss(xml, limit);
        } catch (Exception e) {
            log.error("Failed to fetch news for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getNewsForHoldings(String userId, int limitPerHolding) {
        List<Holding> holdings = holdingRepository.findByUserId(UUID.fromString(userId));
        if (holdings.isEmpty()) return List.of();

        List<String> symbols = holdings.stream()
                .map(Holding::getSymbol)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();

        List<Map<String, Object>> allNews = new ArrayList<>();
        for (String symbol : symbols) {
            String cleanSymbol = symbol.replaceAll("\\.(NS|BO)$", "");
            String query = cleanSymbol + " stock";
            List<Map<String, Object>> news = searchNews(query, limitPerHolding);
            for (Map<String, Object> item : news) {
                item.put("symbol", symbol);
            }
            allNews.addAll(news);
        }

        allNews.sort(Comparator.comparing(
                n -> (LocalDateTime) n.getOrDefault("pubDate", LocalDateTime.MIN),
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        return allNews;
    }

    private List<Map<String, Object>> parseRss(String xml, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
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

                LocalDateTime pubDate = parseRssDate(pubDateStr);

                items.add(Map.of(
                        "title", title != null ? title : "",
                        "link", link != null ? link : "",
                        "source", source != null ? source : "Google News",
                        "pubDate", pubDate,
                        "description", description != null ? stripHtml(description) : ""
                ));
            }
        } catch (Exception e) {
            log.error("Failed to parse RSS XML: {}", e.getMessage());
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

    private LocalDateTime parseRssDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(dateStr,
                        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH));
            } catch (DateTimeParseException e2) {
                return LocalDateTime.now();
            }
        }
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").strip();
    }
}
