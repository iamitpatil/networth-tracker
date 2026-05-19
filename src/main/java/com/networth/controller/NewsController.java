package com.networth.controller;

import com.networth.service.market.NewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchNews(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(newsService.searchNews(q, limit));
    }

    @GetMapping("/holdings")
    public ResponseEntity<List<Map<String, Object>>> newsForHoldings(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(newsService.getNewsForHoldings(userDetails.getUsername(), limit));
    }
}
