package com.networth.service.market.provider;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class NewsItem {
    String title;
    String link;
    String source;
    /** When the article was published. An instant: feeds report it with an offset. */
    Instant pubDate;
    String description;
    String symbol;
}
