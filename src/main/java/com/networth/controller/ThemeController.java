package com.networth.controller;

import com.networth.model.entity.Theme;
import com.networth.service.ThemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/themes")
@RequiredArgsConstructor
public class ThemeController {

    private final ThemeService themeService;

    @GetMapping
    public ResponseEntity<List<Theme>> getThemes(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                themeService.getAvailableThemes(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/sample")
    public ResponseEntity<String> getSampleFormat() {
        String json = """
                {
                  "name": "My Theme",
                  "colorsJson": "{\\"bg\\":\\"#0f172a\\",\\"bg-card\\":\\"#1e293b\\",\\"bg-card-hover\\":\\"#1e293b\\",\\"border\\":\\"#334155\\",\\"text\\":\\"#f1f5f9\\",\\"text-muted\\":\\"#94a3b8\\",\\"text-secondary\\":\\"#64748b\\",\\"primary\\":\\"#3b82f6\\",\\"primary-hover\\":\\"#2563eb\\",\\"green\\":\\"#22c55e\\",\\"red\\":\\"#ef4444\\",\\"amber\\":\\"#f59e0b\\",\\"sidebar-bg\\":\\"#1e293b\\",\\"sidebar-border\\":\\"#334155\\",\\"hover-bg\\":\\"rgba(255,255,255,0.05)\\",\\"input-bg\\":\\"#334155\\",\\"input-border\\":\\"#475569\\"}"
                }
                """;
        return ResponseEntity.ok()
                .header("Content-Type", "application/json")
                .header("Content-Disposition", "attachment; filename=\"theme-sample.json\"")
                .body(json);
    }

    @PostMapping
    public ResponseEntity<Theme> createTheme(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        String name = body.get("name");
        String colorsJson = body.get("colorsJson");
        if (name == null || colorsJson == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(themeService.createTheme(userId, name, colorsJson));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Theme> updateTheme(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        UUID themeId = UUID.fromString(id);
        String name = body.get("name");
        String colorsJson = body.get("colorsJson");
        return ResponseEntity.ok(themeService.updateTheme(userId, themeId, name, colorsJson));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTheme(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        themeService.deleteTheme(UUID.fromString(userDetails.getUsername()), UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }
}
