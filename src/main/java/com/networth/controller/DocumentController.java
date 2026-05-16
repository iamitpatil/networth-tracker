package com.networth.controller;

import com.networth.model.entity.Document;
import com.networth.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<List<Document>> getDocuments(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                documentService.getUserDocuments(UUID.fromString(userDetails.getUsername())));
    }

    @GetMapping("/demat/{dematAccountId}")
    public ResponseEntity<List<Document>> getDematDocuments(@PathVariable String dematAccountId) {
        return ResponseEntity.ok(
                documentService.getDematAccountDocuments(UUID.fromString(dematAccountId)));
    }

    @GetMapping("/holding/{holdingId}")
    public ResponseEntity<List<Document>> getHoldingDocuments(@PathVariable String holdingId) {
        return ResponseEntity.ok(
                documentService.getHoldingDocuments(UUID.fromString(holdingId)));
    }

    @PostMapping("/upload")
    public ResponseEntity<Document> uploadDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String dematAccountId,
            @RequestParam(required = false) String holdingId,
            @RequestParam(required = false) String salaryId) {
        try {
            Document doc = documentService.uploadDocument(
                    UUID.fromString(userDetails.getUsername()),
                    file, category, description, dematAccountId, holdingId, salaryId);
            return ResponseEntity.status(HttpStatus.CREATED).body(doc);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) throws IOException {
        UUID userId = UUID.fromString(userDetails.getUsername());
        Document doc = documentService.getDocument(UUID.fromString(id));

        if (!doc.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Path filePath = documentService.getDocumentPath(doc);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        InputStream inputStream = Files.newInputStream(filePath);
        InputStreamResource resource = new InputStreamResource(inputStream);

        String contentType = doc.getContentType() != null ? doc.getContentType() : "application/octet-stream";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(doc.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getOriginalFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        try {
            documentService.deleteDocument(
                    UUID.fromString(userDetails.getUsername()), UUID.fromString(id));
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
