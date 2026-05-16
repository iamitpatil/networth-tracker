package com.networth.service;

import com.networth.model.entity.Theme;
import com.networth.repository.ThemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ThemeService {

    private final ThemeRepository themeRepository;

    @Transactional(readOnly = true)
    public List<Theme> getAvailableThemes(UUID userId) {
        return themeRepository.findByIsSystemTrueOrUserIdOrderByName(userId);
    }

    @Transactional
    public Theme createTheme(UUID userId, String name, String colorsJson) {
        Theme theme = Theme.builder()
                .name(name)
                .colorsJson(colorsJson)
                .isSystem(false)
                .userId(userId)
                .build();
        return themeRepository.save(theme);
    }

    @Transactional(readOnly = true)
    public Theme getTheme(UUID id) {
        return themeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Theme not found"));
    }

    @Transactional
    public void deleteTheme(UUID userId, UUID themeId) {
        Theme theme = getTheme(themeId);
        if (theme.getIsSystem() || !userId.equals(theme.getUserId())) {
            throw new IllegalArgumentException("Cannot delete this theme");
        }
        themeRepository.deleteById(themeId);
    }

    @Transactional
    public Theme updateTheme(UUID userId, UUID themeId, String name, String colorsJson) {
        Theme theme = getTheme(themeId);
        if (theme.getIsSystem() || !userId.equals(theme.getUserId())) {
            throw new IllegalArgumentException("Cannot edit this theme");
        }
        if (name != null) theme.setName(name);
        if (colorsJson != null) theme.setColorsJson(colorsJson);
        return themeRepository.save(theme);
    }
}
