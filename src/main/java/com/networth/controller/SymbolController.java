package com.networth.controller;

import com.networth.service.SymbolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/symbols")
@RequiredArgsConstructor
public class SymbolController {

    private final SymbolService symbolService;

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> getSymbols(
            @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            return ResponseEntity.ok(symbolService.getSymbolsByCategory(category));
        }
        return ResponseEntity.ok(symbolService.getAllSymbols());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh() {
        symbolService.refreshAll();
        return ResponseEntity.ok(Map.of("message", "Symbols refreshed"));
    }

    @PostMapping("/refresh/bonds")
    public ResponseEntity<Map<String, String>> refreshBonds() {
        symbolService.refreshBonds();
        return ResponseEntity.ok(Map.of("message", "Bond symbols refreshed"));
    }
}
