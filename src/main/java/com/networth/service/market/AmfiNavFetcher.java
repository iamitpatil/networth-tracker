package com.networth.service.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Service
@Slf4j
public class AmfiNavFetcher {

    private final RestTemplate restTemplate;

    @Value("${market.data.amfi.nav-url}")
    private String navUrl;

    public AmfiNavFetcher(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public BigDecimal fetchNav(String isin) {
        try {
            String response = restTemplate.getForObject(navUrl, String.class);
            if (response == null) return null;

            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.contains(isin)) {
                    String[] parts = line.split(";");
                    if (parts.length >= 5) {
                        String navStr = parts[4].trim();
                        if (!navStr.isEmpty()) {
                            return new BigDecimal(navStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch NAV for ISIN {}: {}", isin, e.getMessage());
        }
        return null;
    }

    public BigDecimal fetchNavBySchemeCode(String schemeCode) {
        try {
            String response = restTemplate.getForObject(navUrl, String.class);
            if (response == null) return null;

            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.startsWith(schemeCode + ";")) {
                    String[] parts = line.split(";");
                    if (parts.length >= 5) {
                        String navStr = parts[4].trim();
                        if (!navStr.isEmpty()) {
                            return new BigDecimal(navStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch NAV for scheme code {}: {}", schemeCode, e.getMessage());
        }
        return null;
    }
}
