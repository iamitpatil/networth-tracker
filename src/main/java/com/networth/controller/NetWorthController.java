package com.networth.controller;

import com.networth.model.dto.NetWorthResponse;
import com.networth.service.FamilyDataService;
import com.networth.service.HealthScoreService;
import com.networth.service.NetWorthHistoryService;
import com.networth.service.networth.NetWorthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/net-worth")
@RequiredArgsConstructor
public class NetWorthController {

    private final NetWorthService netWorthService;
    private final NetWorthHistoryService historyService;
    private final HealthScoreService healthScoreService;
    private final FamilyDataService familyDataService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNetWorth(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam Map<String, String> params) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        boolean fam = "f".equals(params.getOrDefault("view", ""));
        if (!fam) {
            NetWorthResponse response = netWorthService.calculateNetWorth(uid);
            historyService.snapshotNetWorth(uid);
            return ResponseEntity.ok(Map.of(
                    "totalAssets", response.getTotalAssets(),
                    "totalLiabilities", response.getTotalLiabilities(),
                    "netWorth", response.getNetWorth(),
                    "liquidAssets", response.getLiquidAssets(),
                    "equityValue", response.getEquityValue(),
                    "debtValue", response.getDebtValue(),
                    "goldValue", response.getGoldValue(),
                    "realEstateValue", response.getRealEstateValue(),
                    "cashValue", response.getCashValue(),
                    "cryptoValue", response.getCryptoValue()
            ));
        }
        return ResponseEntity.ok(familyDataService.getNetWorth(uid, true));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getNetWorthHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(historyService.getNetWorthHistory(UUID.fromString(userDetails.getUsername()), days));
    }

    @GetMapping("/change")
    public ResponseEntity<Map<String, Object>> getNetWorthChange(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(historyService.getNetWorthChange(UUID.fromString(userDetails.getUsername()), days));
    }

    @GetMapping("/breakdown")
    public ResponseEntity<Map<String, Object>> getBreakdown(@AuthenticationPrincipal UserDetails userDetails) {
        UUID uid = UUID.fromString(userDetails.getUsername());
        NetWorthResponse response = netWorthService.calculateNetWorth(uid);
        return ResponseEntity.ok(Map.of(
                "totalAssets", response.getTotalAssets(),
                "totalLiabilities", response.getTotalLiabilities(),
                "netWorth", response.getNetWorth(),
                "liquidAssets", response.getLiquidAssets(),
                "equityValue", response.getEquityValue(),
                "debtValue", response.getDebtValue(),
                "goldValue", response.getGoldValue(),
                "realEstateValue", response.getRealEstateValue(),
                "cashValue", response.getCashValue(),
                "cryptoValue", response.getCryptoValue()
        ));
    }

    @GetMapping("/health-score")
    public ResponseEntity<Map<String, Object>> getHealthScore(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(healthScoreService.calculateHealthScore(UUID.fromString(userDetails.getUsername())));
    }
}
