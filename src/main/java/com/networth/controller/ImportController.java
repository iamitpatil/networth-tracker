package com.networth.controller;

import com.networth.service.importservice.ImportService;
import com.networth.service.importservice.PDFStatementParser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final PDFStatementParser pdfStatementParser;

    @PostMapping
    public ResponseEntity<Map<String, Object>> importFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam("source") String source) {

        UUID uid = UUID.fromString(userDetails.getUsername());
        String filename = file.getOriginalFilename();
        boolean isPDF = filename != null && filename.toLowerCase().endsWith(".pdf");

        List<String> created = switch (source.toLowerCase()) {
            case "zerodha" -> importService.processZerodhaCsv(uid, file);
            case "groww" -> importService.processGrowwCsv(uid, file);
            case "cas" -> isPDF ? pdfStatementParser.parseCAS(uid, file) : importService.processZerodhaCsv(uid, file);
            case "bank" -> isPDF ? pdfStatementParser.parseBankStatement(uid, file) : List.of();
            default -> throw new IllegalArgumentException("Unsupported source: " + source);
        };

        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "imported", created.size(),
                "holdings", created
        ));
    }
}
