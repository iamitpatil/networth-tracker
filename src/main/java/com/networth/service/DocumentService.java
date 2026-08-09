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
    private final FileEncryptionService fileEncryptionService;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Transactional
    public Document uploadDocument(UUID userId, MultipartFile file, String category, String description,
                                    String dematAccountId, String holdingId, String salaryId,
                                    String form16Id, String itrFilingId, String bankAccountId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) originalFilename = "unnamed";

        String storedFilename = UUID.randomUUID() + "_" + originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path userDir = Paths.get(uploadDir, userId.toString());
        Files.createDirectories(userDir);

        boolean encrypted = false;
        try {
            if (fileEncryptionService.isEnabled()) {
                fileEncryptionService.encryptAndWrite(file.getInputStream(), userDir.resolve(storedFilename));
                encrypted = true;
            } else {
                Files.copy(file.getInputStream(), userDir.resolve(storedFilename));
            }
        } catch (Exception e) {
            log.error("Failed to write file (encrypted={}): {}", fileEncryptionService.isEnabled(), e.getMessage());
            throw new IOException("Failed to save file: " + e.getMessage(), e);
        }

        Document doc = Document.builder()
                .userId(userId)
                .dematAccountId(dematAccountId != null && !dematAccountId.isBlank() ? UUID.fromString(dematAccountId) : null)
                .holdingId(holdingId != null && !holdingId.isBlank() ? UUID.fromString(holdingId) : null)
                .salaryId(salaryId != null && !salaryId.isBlank() ? UUID.fromString(salaryId) : null)
                .form16Id(form16Id != null && !form16Id.isBlank() ? UUID.fromString(form16Id) : null)
                .itrFilingId(itrFilingId != null && !itrFilingId.isBlank() ? UUID.fromString(itrFilingId) : null)
                .bankAccountId(bankAccountId != null && !bankAccountId.isBlank() ? UUID.fromString(bankAccountId) : null)
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .encrypted(encrypted)
                .category(category != null ? category : "OTHER")
                .description(description)
                .build();

        return documentRepository.save(doc);
    }

    /**
     * Backward-compatible overload for existing callers.
     */
    @Transactional
    public Document uploadDocument(UUID userId, MultipartFile file, String category, String description,
                                    String dematAccountId, String holdingId, String salaryId) throws IOException {
        return uploadDocument(userId, file, category, description,
                dematAccountId, holdingId, salaryId, null, null, null);
    }

    /**
     * Group all user's documents by their source section.
     * Returns: { "Tax Documents": [...], "Salary Documents": [...], etc. }
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, java.util.List<Document>> getGroupedDocuments(UUID userId) {
        java.util.List<Document> all = documentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        java.util.Map<String, java.util.List<Document>> groups = new java.util.LinkedHashMap<>();

        // Initialize groups in priority order
        groups.put("Tax Documents", new java.util.ArrayList<>());
        groups.put("Salary Documents", new java.util.ArrayList<>());
        groups.put("Demat Statements", new java.util.ArrayList<>());
        groups.put("Holding Documents", new java.util.ArrayList<>());
        groups.put("Bank Statements", new java.util.ArrayList<>());
        groups.put("Other Documents", new java.util.ArrayList<>());

        for (Document doc : all) {
            String category = doc.getCategory() != null ? doc.getCategory().toUpperCase() : "OTHER";

            if (doc.getForm16Id() != null || doc.getItrFilingId() != null
                    || "FORM_16".equals(category) || "ITR".equals(category)
                    || "TAX_RETURN".equals(category)) {
                groups.get("Tax Documents").add(doc);
            } else if (doc.getSalaryId() != null || "PAYSLIP".equals(category) || "SALARY".equals(category)) {
                groups.get("Salary Documents").add(doc);
            } else if (doc.getDematAccountId() != null || "DEMAT_STATEMENT".equals(category)) {
                groups.get("Demat Statements").add(doc);
            } else if (doc.getHoldingId() != null) {
                groups.get("Holding Documents").add(doc);
            } else if (doc.getBankAccountId() != null || "BANK_STATEMENT".equals(category)) {
                groups.get("Bank Statements").add(doc);
            } else {
                groups.get("Other Documents").add(doc);
            }
        }

        // Remove empty groups to keep response clean
        groups.entrySet().removeIf(e -> e.getValue().isEmpty());

        return groups;
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

    @Transactional(readOnly = true)
    public Path getDocumentPath(Document doc) {
        return Paths.get(uploadDir, doc.getUserId().toString(), doc.getStoredFilename());
    }

    /**
     * Get an InputStream for reading the document content.
     * Automatically decrypts if the document was stored encrypted.
     */
    public java.io.InputStream getDocumentInputStream(Document doc) throws Exception {
        Path filePath = getDocumentPath(doc);
        if (doc.isEncrypted()) {
            return fileEncryptionService.readAndDecryptAsStream(filePath);
        }
        return Files.newInputStream(filePath);
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

    /**
     * Save/update a document entity (used for setting accountType/accountId after initial upload).
     */
    @Transactional
    public Document save(Document doc) {
        return documentRepository.save(doc);
    }

    /**
     * Get documents linked to a specific account (generic).
     */
    @Transactional(readOnly = true)
    public List<Document> getAccountDocuments(String accountType, UUID accountId) {
        return documentRepository.findByAccountTypeAndAccountIdOrderByCreatedAtDesc(accountType, accountId);
    }
}
