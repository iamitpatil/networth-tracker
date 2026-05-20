package com.networth.controller;

import com.networth.model.entity.ReferenceData;
import com.networth.repository.ReferenceDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public endpoint for reference/lookup data used in dropdowns.
 * No authentication required — this is shared configuration data.
 */
@RestController
@RequestMapping("/api/v1/reference-data")
@RequiredArgsConstructor
public class ReferenceDataController {

    private final ReferenceDataRepository repo;

    /**
     * Get all items for a category (e.g., BANK, BROKER, CARD_ISSUER).
     */
    @GetMapping("/{category}")
    public ResponseEntity<List<Map<String, Object>>> getByCategory(@PathVariable String category) {
        List<ReferenceData> items = repo.findByCategoryAndIsActiveTrueOrderBySortOrder(category.toUpperCase());
        return ResponseEntity.ok(items.stream().map(this::toMap).collect(Collectors.toList()));
    }

    /**
     * Get all reference data grouped by category (for bulk loading).
     */
    @GetMapping
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getAll() {
        List<ReferenceData> all = repo.findAll();
        Map<String, List<Map<String, Object>>> grouped = all.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .collect(Collectors.groupingBy(
                        ReferenceData::getCategory,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toMap, Collectors.toList())
                ));
        return ResponseEntity.ok(grouped);
    }

    /**
     * List all available categories.
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(repo.findDistinctCategories());
    }

    private Map<String, Object> toMap(ReferenceData r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", r.getValue());
        m.put("label", r.getLabel() != null ? r.getLabel() : r.getValue());
        if (r.getMetadata() != null && !r.getMetadata().isEmpty()) {
            m.put("metadata", r.getMetadata());
        }
        return m;
    }
}
