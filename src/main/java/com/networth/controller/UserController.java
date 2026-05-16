package com.networth.controller;

import com.networth.model.entity.User;
import com.networth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @PutMapping("/theme")
    public ResponseEntity<Void> setTheme(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String themeId = body.get("themeId");
        user.setActiveThemeId(themeId != null && !themeId.isBlank() ? UUID.fromString(themeId) : null);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}
