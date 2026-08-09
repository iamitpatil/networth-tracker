package com.networth.service.market;

import com.networth.model.entity.Holding;
import com.networth.repository.HoldingRepository;
import com.networth.service.market.provider.MarketDataResolver;
import com.networth.service.market.provider.NewsItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {

    private final MarketDataResolver resolver;
    private final HoldingRepository holdingRepository;

    public List<Map<String, Object>> searchNews(String query, int limit) {
        List<NewsItem> items = resolver.getNews(query, limit);
        return items.stream().map(this::toMap).collect(Collectors.toList());
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
            List<NewsItem> news = resolver.getNews(query, limitPerHolding);
            for (NewsItem item : news) {
                Map<String, Object> map = toMap(item);
                map.put("symbol", symbol);
                allNews.add(map);
            }
        }

        allNews.sort(Comparator.comparing(
                n -> (Instant) n.getOrDefault("pubDate", Instant.EPOCH),
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        return allNews;
    }

    private Map<String, Object> toMap(NewsItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", item.getTitle());
        map.put("link", item.getLink());
        map.put("source", item.getSource());
        map.put("pubDate", item.getPubDate());
        map.put("description", item.getDescription());
        return map;
    }
}
