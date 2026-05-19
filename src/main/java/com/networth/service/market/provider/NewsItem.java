package com.networth.service.market.provider;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class NewsItem {
    String title;
    String link;
    String source;
    LocalDateTime pubDate;
    String description;
    String symbol;
}
