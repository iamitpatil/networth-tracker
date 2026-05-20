package com.networth.controller;

import com.networth.model.entity.Document;
import com.networth.model.entity.Salary;
import com.networth.service.DocumentService;
import com.networth.service.SalaryService;
import com.networth.service.SalaryService.SalaryRequest;
import com.networth.service.SalarySlipParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/salaries")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;
    private final SalarySlipParser salarySlipParser;
    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<List<Salary>> getSalaries(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(salaryService.getUserSalaries(UUID.fromString(userDetails.getUsername())));
    }

    @PostMapping("/parse-slip")
    public ResponseEntity<Map<String, Object>> parseSlip(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password) {
        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            Document doc = documentService.uploadDocument(
                    userId, file, "PAYSLIP", "Salary slip upload", null, null, null);
            Map<String, Object> parsed = salarySlipParser.parseSlip(file, userDetails.getUsername(), password);
            Map<String, Object> result = new HashMap<>(parsed);
            result.put("documentId", doc.getId().toString());
            return ResponseEntity.ok(result);
        } catch (com.networth.service.CreditCardBillParser.PasswordRequiredException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("error", "PASSWORD_REQUIRED", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<Salary> createSalary(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SalaryRequest request) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Salary salary = salaryService.createSalary(userId, request);
        if (request.documentId() != null) {
            // Now verifies document ownership before linking
            documentService.linkDocumentToSalary(userId, UUID.fromString(request.documentId()), salary.getId());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(salary);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Salary> updateSalary(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody SalaryRequest request) {
        return ResponseEntity.ok(salaryService.updateSalary(UUID.fromString(userDetails.getUsername()), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSalary(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        salaryService.deleteSalary(UUID.fromString(userDetails.getUsername()), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<?> getSalaryDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        // Both methods now verify ownership internally - throws AccessDeniedException if not owner
        salaryService.getSalary(userId, id);
        List<Document> docs = documentService.getSalaryDocuments(userId, id);
        if (docs.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(docs.get(0));
    }
}
