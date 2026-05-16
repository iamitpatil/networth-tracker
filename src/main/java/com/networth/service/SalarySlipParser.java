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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalarySlipParser {

    private final AIChatService aiChatService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> parseSlip(MultipartFile file, String userId) {
        String text = "";
        try {
            byte[] bytes = file.getBytes();
            text = extractText(bytes, file.getOriginalFilename());
            String aiResponse = aiChatService.chat(
                    userId,
                    "You are a salary slip parser. Extract all earnings, deductions, and net pay from the salary slip text below. " +
                    "Return ONLY a valid JSON object with keys: employerName, payDate (YYYY-MM-DD format), grossPay, netPay, and a components map " +
                    "containing each line item as key-value pairs (e.g. {\"Basic Pay\": 50000, \"HRA\": 25000, \"PF\": 6000}). " +
                    "PayDate MUST be in YYYY-MM-DD format. Use null for missing values. No explanation, no markdown, just JSON.\n\n" + text,
                    "import",
                    java.util.List.of()
            );

            String cleaned = aiResponse.replaceAll("```json\\s*|```\\s*", "").trim();
            Map<String, Object> result = objectMapper.readValue(cleaned, new TypeReference<>() {});
//            result = normalizeDates(result, text);
            log.info("Parsed salary slip: {} components", result.size());
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse salary slip: {}", e.getMessage());
//            Map<String, Object> fallback = extractDatesFromText(text);
//            fallback.put("rawText", text);
//            return fallback;
            return Map.of();
        }
    }

//    private Map<String, Object> normalizeDates(Map<String, Object> result, String rawText) {
//        Object payDate = result.get("payDate");
//        if (payDate instanceof String s && !s.isBlank() && !s.equals("null")) {
//            String normalized = normalizeDateString(s);
//            if (normalized != null) {
//                result.put("payDate", normalized);
//                return result;
//            }
//        }
//        Map<String, Object> extracted = extractDatesFromText(rawText);
//        if (extracted.containsKey("payDate")) {
//            result.put("payDate", extracted.get("payDate"));
//        }
//        return result;
//    }

//    private Map<String, Object> extractDatesFromText(String text) {
//        Map<String, Object> result = new HashMap<>();
//        List<Pattern> patterns = List.of(
//                Pattern.compile("\\b(\\d{2})/(\\d{2})/(\\d{4})\\b"),
//                Pattern.compile("\\b(\\d{2})-(\\d{2})-(\\d{4})\\b"),
//                Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b"),
//                Pattern.compile("\\b(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE),
//                Pattern.compile("\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{1,2}),?\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE),
//                Pattern.compile("\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE),
//                Pattern.compile("\\b(0[1-9]|1[0-2])[/-](\\d{4})\\b")
//        );
//        List<DateParser> parsers = List.of(
//                (m) -> LocalDate.parse(m.group(), DateTimeFormatter.ofPattern("dd/MM/yyyy")),
//                (m) -> LocalDate.parse(m.group(), DateTimeFormatter.ofPattern("dd-MM-yyyy")),
//                (m) -> LocalDate.parse(m.group(), DateTimeFormatter.ISO_LOCAL_DATE),
//                (m) -> LocalDate.parse("1 " + m.group(), DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)),
//                (m) -> LocalDate.parse(m.group(), DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH)),
//                (m) -> LocalDate.parse("1 " + m.group(), DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)),
//                (m) -> LocalDate.parse("01/" + m.group(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
//        );
//
//        for (int i = 0; i < patterns.size(); i++) {
//            Matcher m = patterns.get(i).matcher(text);
//            while (m.find()) {
//                try {
//                    LocalDate date = parsers.get(i).parse(m);
//                    result.put("payDate", date.toString());
//                    return result;
//                } catch (DateTimeParseException ignored) {}
//            }
//        }
//        return result;
//    }

//    @FunctionalInterface
//    private interface DateParser {
//        LocalDate parse(Matcher m);
//
//    private String normalizeDateString(String s) {
//        s = s.trim();
//        if (s.length() == 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
//            try {
//                LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
//                return s;
//            } catch (DateTimeParseException ignored) {}
//        }
//        for (DateTimeFormatter fmt : List.of(
//                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
//                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
//                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
//                DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
//                DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.ENGLISH),
//                DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
//        )) {
//            try {
//                return LocalDate.parse(s, fmt).toString();
//            } catch (DateTimeParseException ignored) {}
//        }
//        return null;
//    }
//
//    public byte[] readBytes(MultipartFile file) {
//        try {
//            return file.getBytes();
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to read file: " + e.getMessage());
//        }
//    }

    private String extractText(byte[] bytes, String filename) {
        String name = filename != null ? filename.toLowerCase() : "";
        if (name.endsWith(".pdf")) {
            try (InputStream is = new ByteArrayInputStream(bytes); PDDocument doc = Loader.loadPDF(is.readAllBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(doc);
                if (!text.isBlank()) return text;
                log.warn("PDF text extraction returned empty");
            } catch (Exception e) {
                log.error("Failed to extract text from PDF: {}", e.getMessage());
            }
        }
        return new String(bytes);
    }
}
