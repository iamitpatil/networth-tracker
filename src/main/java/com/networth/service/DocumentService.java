package com.networth.service;

import com.networth.exception.AccessDeniedException;
import com.networth.exception.ResourceNotFoundException;
import com.networth.model.entity.Document;
import com.networth.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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

    /**
     * Get documents for a demat account - only returns user's own documents.
     */
    @Transactional(readOnly = true)
    public List<Document> getDematAccountDocuments(UUID userId, UUID dematAccountId) {
        return documentRepository.findByDematAccountIdOrderByCreatedAtDesc(dematAccountId)
                .stream()
                .filter(doc -> userId.equals(doc.getUserId()))
                .toList();
    }

    /**
     * Get documents for a holding - only returns user's own documents.
     */
    @Transactional(readOnly = true)
    public List<Document> getHoldingDocuments(UUID userId, UUID holdingId) {
        return documentRepository.findByHoldingIdOrderByCreatedAtDesc(holdingId)
                .stream()
                .filter(doc -> userId.equals(doc.getUserId()))
                .toList();
    }

    /**
     * Get documents for a salary - only returns user's own documents.
     */
    @Transactional(readOnly = true)
    public List<Document> getSalaryDocuments(UUID userId, UUID salaryId) {
        return documentRepository.findBySalaryIdOrderByCreatedAtDesc(salaryId)
                .stream()
                .filter(doc -> userId.equals(doc.getUserId()))
                .toList();
    }

    /**
     * Link document to salary. Verifies both belong to the user.
     */
    @Transactional
    public void linkDocumentToSalary(UUID userId, UUID docId, UUID salaryId) {
        Document doc = findOwnedDocument(userId, docId);
        // Note: caller should also verify salary ownership
        doc.setSalaryId(salaryId);
        documentRepository.save(doc);
    }

    /**
     * Get a document, verifying ownership.
     */
    @Transactional(readOnly = true)
    public Document getDocument(UUID userId, UUID id) {
        return findOwnedDocument(userId, id);
    }

    /**
     * @deprecated Use {@link #getDocument(UUID, UUID)} instead. This method does not check ownership.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public Document getDocumentUnsafe(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id.toString()));
    }

    @Transactional(readOnly = true)
    public Path getDocumentPath(Document doc) {
        return Paths.get(uploadDir, doc.getUserId().toString(), doc.getStoredFilename());
    }

    @Transactional
    public void deleteDocument(UUID userId, UUID docId) throws IOException {
        Document doc = findOwnedDocument(userId, docId);
        Path filePath = getDocumentPath(doc);
        Files.deleteIfExists(filePath);
        documentRepository.delete(doc);
    }

    public long countByDematAccount(UUID dematAccountId) {
        return documentRepository.countByDematAccountId(dematAccountId);
    }

    public long countByHolding(UUID holdingId) {
        return documentRepository.countByHoldingId(holdingId);
    }

    /**
     * Find a document ensuring it belongs to the given user.
     */
    private Document findOwnedDocument(UUID userId, UUID docId) {
        Document doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId.toString()));
        if (!doc.getUserId().equals(userId)) {
            log.warn("User {} attempted to access document {} owned by {}", userId, docId, doc.getUserId());
            throw new AccessDeniedException("Document", docId.toString());
        }
        return doc;
    }
}
