package com.networth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditCardBillParser {

    private final AIChatService aiChatService;
    private final ObjectMapper objectMapper;

    private static final String PARSE_PROMPT = """
            You are a credit card bill/statement parser. Extract structured data from the text below.
            Return ONLY a valid JSON object with these keys:
            - cardIssuer (string, e.g. "HDFC Bank", "ICICI Bank", "SBI Card")
            - cardLastFourDigits (string, last 4 digits of card number if found, else null)
            - cardType (string, e.g. "VISA", "Mastercard", "RuPay", or null)
            - statementDate (string, YYYY-MM-DD format, the billing/statement date)
            - dueDate (string, YYYY-MM-DD format, payment due date)
            - totalAmountDue (number, total amount due)
            - minimumAmountDue (number, minimum payment required, or null)
            - previousBalance (number, opening/previous balance, or null)
            - paymentsReceived (number, payments/credits received, or null)
            - newCharges (number, total new charges, or null)
            - transactions (array of objects, each with:
                - date (string, YYYY-MM-DD)
                - description (string, merchant/transaction description)
                - amount (number, positive for charges, negative for credits/refunds)
                - category (string, one of: FOOD, SHOPPING, TRAVEL, FUEL, ENTERTAINMENT, UTILITIES, GROCERIES, HEALTH, EDUCATION, EMI, SUBSCRIPTION, INSURANCE, TRANSFER, OTHER)
            )
            - spendSummary (object mapping category to total amount, e.g. {"FOOD": 5200, "SHOPPING": 12000})

            Categorize each transaction intelligently based on the merchant name/description.
            Use null for any field you cannot determine. No explanation, no markdown fences, just the JSON object.

            Credit card statement text:
            """;

    public Map<String, Object> parseBill(MultipartFile file, String userId, String password) {
        try {
            byte[] bytes = file.getBytes();
            String text = extractText(bytes, file.getOriginalFilename(), password);

            if (text.isBlank()) {
                throw new IllegalArgumentException("Could not extract text from the uploaded file");
            }

            log.info("Parsing credit card bill for user {}, extracted {} chars", userId, text.length());

            String aiResponse = aiChatService.chat(
                    userId,
                    PARSE_PROMPT + text,
                    "import",
                    List.of()
            );

            // Strip markdown fences if present
            String cleaned = aiResponse.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            Map<String, Object> result = objectMapper.readValue(
                    cleaned, new TypeReference<Map<String, Object>>() {});

            log.info("Successfully parsed credit card bill: issuer={}, transactions={}",
                    result.get("cardIssuer"),
                    result.get("transactions") instanceof List<?> l ? l.size() : 0);

            return result;
        } catch (Exception e) {
            log.error("Failed to parse credit card bill: {}", e.getMessage());
            throw new RuntimeException("Failed to parse credit card bill: " + e.getMessage(), e);
        }
    }

    private String extractText(byte[] bytes, String filename, String password) {
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            try {
                byte[] pdfBytes = new ByteArrayInputStream(bytes).readAllBytes();
                PDDocument doc;
                if (password != null && !password.isBlank()) {
                    doc = Loader.loadPDF(pdfBytes, password);
                } else {
                    doc = Loader.loadPDF(pdfBytes);
                }
                try (doc) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String text = stripper.getText(doc);
                    if (!text.isBlank()) return text;
                }
            } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
                throw new PasswordRequiredException("This PDF is password protected. Please provide the password.");
            } catch (Exception e) {
                log.warn("PDFBox extraction failed, falling back to raw text: {}", e.getMessage());
            }
        }
        // Fallback: treat as plain text (CSV, text statement)
        return new String(bytes);
    }

    /**
     * Custom exception to signal that a PDF password is required.
     */
    public static class PasswordRequiredException extends RuntimeException {
        public PasswordRequiredException(String message) {
            super(message);
        }
    }
}
