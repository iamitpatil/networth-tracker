package com.networth.service;

import com.networth.model.entity.Document;
import com.networth.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Transactional
    public Document uploadDocument(UUID userId, MultipartFile file, String category, String description,
                                    String dematAccountId, String holdingId, String salaryId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) originalFilename = "unnamed";

        String storedFilename = UUID.randomUUID() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path userDir = Paths.get(uploadDir, userId.toString());
        Files.createDirectories(userDir);
        Files.copy(file.getInputStream(), userDir.resolve(storedFilename));

        Document doc = Document.builder()
                .userId(userId)
                .dematAccountId(dematAccountId != null && !dematAccountId.isBlank() ? UUID.fromString(dematAccountId) : null)
                .holdingId(holdingId != null && !holdingId.isBlank() ? UUID.fromString(holdingId) : null)
                .salaryId(salaryId != null && !salaryId.isBlank() ? UUID.fromString(salaryId) : null)
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .category(category != null ? category : "OTHER")
                .description(description)
                .build();

        return documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public List<Document> getUserDocuments(UUID userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Document> getDematAccountDocuments(UUID dematAccountId) {
        return documentRepository.findByDematAccountIdOrderByCreatedAtDesc(dematAccountId);
    }

    @Transactional(readOnly = true)
    public List<Document> getHoldingDocuments(UUID holdingId) {
        return documentRepository.findByHoldingIdOrderByCreatedAtDesc(holdingId);
    }

    @Transactional(readOnly = true)
    public List<Document> getSalaryDocuments(UUID salaryId) {
        return documentRepository.findBySalaryIdOrderByCreatedAtDesc(salaryId);
    }

    @Transactional
    public void linkDocumentToSalary(UUID docId, UUID salaryId) {
        Document doc = getDocument(docId);
        doc.setSalaryId(salaryId);
        documentRepository.save(doc);
    }

    @Transactional(readOnly = true)
    public Document getDocument(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    @Transactional(readOnly = true)
    public Path getDocumentPath(Document doc) {
        return Paths.get(uploadDir, doc.getUserId().toString(), doc.getStoredFilename());
    }

    @Transactional
    public void deleteDocument(UUID userId, UUID docId) throws IOException {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!doc.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to delete this document");
        }
        Path filePath = getDocumentPath(doc);
        Files.deleteIfExists(filePath);
        documentRepository.deleteById(docId);
    }

    public long countByDematAccount(UUID dematAccountId) {
        return documentRepository.countByDematAccountId(dematAccountId);
    }

    public long countByHolding(UUID holdingId) {
        return documentRepository.countByHoldingId(holdingId);
    }
}
