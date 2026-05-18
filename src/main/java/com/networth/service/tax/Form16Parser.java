package com.networth.service.tax;

import com.networth.model.entity.Form16Data;
import com.networth.model.enums.TaxRegime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Form 16 PDF (TDS certificate from employer) and extracts:
 * - Employer details (PAN, TAN, name)
 * - Salary breakup (gross, exempt allowances, standard deduction)
 * - TDS deducted (Part A) - quarterly breakup
 * - Deductions claimed (Part B) - 80C, 80D, 80CCD(1B), etc.
 * - Total tax liability
 *
 * Form 16 formats vary by employer; this parser handles common patterns
 * and assigns a confidence score (0-100) based on fields successfully extracted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Form16Parser {

    // ===== Regex patterns for common Form 16 fields =====

    private static final Pattern EMPLOYER_NAME = Pattern.compile(
            "(?:Name and Address of the Employer|Name of the Employer|Employer's Name)[\\s:]*([A-Z][A-Za-z0-9.,&\\s-]+(?:LIMITED|LTD|PVT|PRIVATE|INC|CORPORATION|CORP|LLP|LLC)?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMPLOYER_PAN = Pattern.compile(
            "(?:PAN of the (?:Deductor|Employer)|Employer's PAN)[\\s:]*([A-Z]{5}[0-9]{4}[A-Z])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMPLOYER_TAN = Pattern.compile(
            "(?:TAN of the (?:Deductor|Employer)|TAN)[\\s:]*([A-Z]{4}[0-9]{5}[A-Z])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EMPLOYEE_PAN = Pattern.compile(
            "(?:PAN of the Employee|Employee's PAN)[\\s:]*([A-Z]{5}[0-9]{4}[A-Z])",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FINANCIAL_YEAR = Pattern.compile(
            "(?:Financial Year|FY)[\\s:]*(\\d{4}[-/]\\d{2,4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ASSESSMENT_YEAR = Pattern.compile(
            "(?:Assessment Year|AY)[\\s:]*(\\d{4}[-/]\\d{2,4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GROSS_SALARY = Pattern.compile(
            "(?:Gross Salary|Total Gross Salary|Salary as per Section 17\\(1\\))[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXEMPT_ALLOWANCES = Pattern.compile(
            "(?:Less.*?(?:Allowances|Exemptions)|HRA|Section 10)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STANDARD_DEDUCTION = Pattern.compile(
            "Standard Deduction[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PROFESSIONAL_TAX = Pattern.compile(
            "Professional Tax[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TAXABLE_SALARY = Pattern.compile(
            "(?:Income chargeable under.*?Salaries|Taxable Salary|Income from Salary)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    // TDS patterns
    private static final Pattern TDS_TOTAL = Pattern.compile(
            "(?:Total Tax Deducted|Total TDS|Tax Deducted at Source)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    // Deduction patterns
    private static final Pattern SECTION_80C = Pattern.compile(
            "(?:Section 80C|80C - Deduction)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SECTION_80CCD_1B = Pattern.compile(
            "(?:Section 80CCD\\s*\\(?1B\\)?|80CCD\\(1B\\))[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SECTION_80D = Pattern.compile(
            "(?:Section 80D|80D - Health Insurance)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SECTION_80G = Pattern.compile(
            "(?:Section 80G|80G - Donations)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TOTAL_TAX = Pattern.compile(
            "(?:Total Tax Liability|Tax Payable|Total Income Tax)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern QUARTERLY_TDS = Pattern.compile(
            "(?:Q1|Quarter 1)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*).*?" +
            "(?:Q2|Quarter 2)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*).*?" +
            "(?:Q3|Quarter 3)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*).*?" +
            "(?:Q4|Quarter 4)[\\s:.]*?₹?\\s*([0-9,]+\\.?\\d*)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Parses a Form 16 PDF and returns extracted data as Form16Data entity.
     *
     * @param file The uploaded PDF file
     * @return Form16Data with parsed fields and confidence score
     * @throws IOException if PDF cannot be read
     */
    public Form16Data parse(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.contains("pdf")) {
            log.warn("Form16 file content-type is {}, attempting parse anyway", contentType);
        }

        try (InputStream is = file.getInputStream();
             PDDocument doc = Loader.loadPDF(is.readAllBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);

            return parseText(text);
        }
    }

    /**
     * Parses Form 16 from raw text (separated for testability).
     */
    public Form16Data parseText(String text) {
        if (text == null || text.isBlank()) {
            return Form16Data.builder()
                    .source("FORM_16_PDF")
                    .parseConfidence(0)
                    .build();
        }

        // Normalize text - collapse multiple whitespace
        String normalized = text.replaceAll("\\s+", " ");

        Map<String, Object> rawData = new HashMap<>();
        Form16Data.Form16DataBuilder builder = Form16Data.builder()
                .source("FORM_16_PDF")
                .taxRegime(TaxRegime.OLD);  // Form 16 is mostly for OLD regime users

        int fieldsFound = 0;
        int totalFields = 15;

        // === Employer Info ===
        String employerName = extract(EMPLOYER_NAME, normalized);
        if (employerName != null) {
            builder.employerName(employerName.trim());
            rawData.put("employerName", employerName);
            fieldsFound++;
        }

        String employerPan = extract(EMPLOYER_PAN, normalized);
        if (employerPan != null) {
            builder.employerPan(employerPan);
            rawData.put("employerPan", employerPan);
            fieldsFound++;
        }

        String employerTan = extract(EMPLOYER_TAN, normalized);
        if (employerTan != null) {
            builder.employerTan(employerTan);
            rawData.put("employerTan", employerTan);
            fieldsFound++;
        }

        String employeePan = extract(EMPLOYEE_PAN, normalized);
        if (employeePan != null) {
            builder.employeePan(employeePan);
            rawData.put("employeePan", employeePan);
            fieldsFound++;
        }

        // === Financial/Assessment Year ===
        String fy = extract(FINANCIAL_YEAR, normalized);
        if (fy != null) {
            builder.financialYear(normalizeYear(fy));
            rawData.put("financialYear", fy);
            fieldsFound++;
        }

        String ay = extract(ASSESSMENT_YEAR, normalized);
        if (ay != null) {
            builder.assessmentYear(normalizeYear(ay));
            rawData.put("assessmentYear", ay);
            fieldsFound++;
        }

        // === Salary ===
        BigDecimal grossSalary = extractAmount(GROSS_SALARY, normalized);
        if (grossSalary != null) {
            builder.grossSalary(grossSalary);
            rawData.put("grossSalary", grossSalary);
            fieldsFound++;
        }

        BigDecimal exemptAllowances = extractAmount(EXEMPT_ALLOWANCES, normalized);
        if (exemptAllowances != null) {
            builder.exemptAllowances(exemptAllowances);
            rawData.put("exemptAllowances", exemptAllowances);
            fieldsFound++;
        }

        BigDecimal stdDeduction = extractAmount(STANDARD_DEDUCTION, normalized);
        if (stdDeduction != null) {
            builder.standardDeduction(stdDeduction);
            rawData.put("standardDeduction", stdDeduction);
            fieldsFound++;
        }

        BigDecimal profTax = extractAmount(PROFESSIONAL_TAX, normalized);
        if (profTax != null) {
            builder.professionalTax(profTax);
            rawData.put("professionalTax", profTax);
            fieldsFound++;
        }

        BigDecimal taxableSalary = extractAmount(TAXABLE_SALARY, normalized);
        if (taxableSalary != null) {
            builder.taxableSalary(taxableSalary);
            rawData.put("taxableSalary", taxableSalary);
            fieldsFound++;
        }

        // === TDS ===
        BigDecimal tdsTotal = extractAmount(TDS_TOTAL, normalized);
        if (tdsTotal != null) {
            builder.tdsTotal(tdsTotal);
            rawData.put("tdsTotal", tdsTotal);
            fieldsFound++;
        }

        // Quarterly TDS breakdown
        Map<String, Object> quarterly = extractQuarterlyTds(normalized);
        if (quarterly != null) {
            builder.quarterlyTds(quarterly);
            rawData.put("quarterlyTds", quarterly);
        }

        // === Deductions (Part B) ===
        BigDecimal s80c = extractAmount(SECTION_80C, normalized);
        if (s80c != null) {
            builder.section80c(s80c);
            rawData.put("section80c", s80c);
            fieldsFound++;
        }

        BigDecimal s80ccd1b = extractAmount(SECTION_80CCD_1B, normalized);
        if (s80ccd1b != null) {
            builder.section80ccd1b(s80ccd1b);
            rawData.put("section80ccd1b", s80ccd1b);
            fieldsFound++;
        }

        BigDecimal s80d = extractAmount(SECTION_80D, normalized);
        if (s80d != null) {
            builder.section80d(s80d);
            rawData.put("section80d", s80d);
            fieldsFound++;
        }

        BigDecimal s80g = extractAmount(SECTION_80G, normalized);
        if (s80g != null) {
            builder.section80g(s80g);
            rawData.put("section80g", s80g);
            fieldsFound++;
        }

        // === Total Tax ===
        BigDecimal totalTax = extractAmount(TOTAL_TAX, normalized);
        if (totalTax != null) {
            builder.totalTaxLiability(totalTax);
            rawData.put("totalTaxLiability", totalTax);
            fieldsFound++;
        }

        // Calculate confidence score
        int confidence = Math.min(100, (fieldsFound * 100) / totalFields);
        builder.parseConfidence(confidence);
        builder.rawData(rawData);

        log.info("Form 16 parsed: {} fields found, confidence={}%", fieldsFound, confidence);

        return builder.build();
    }

    // ===== Helper Methods =====

    private String extract(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find() && m.groupCount() >= 1) {
            String value = m.group(1).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    private BigDecimal extractAmount(Pattern pattern, String text) {
        String value = extract(pattern, text);
        if (value == null) return null;
        try {
            // Remove commas and currency symbols
            String cleaned = value.replaceAll("[,₹\\s]", "");
            BigDecimal amount = new BigDecimal(cleaned);
            return amount.compareTo(BigDecimal.ZERO) > 0 ? amount : null;
        } catch (NumberFormatException e) {
            log.debug("Failed to parse amount '{}': {}", value, e.getMessage());
            return null;
        }
    }

    private String normalizeYear(String year) {
        if (year == null) return null;
        // Convert "2024-25" to "2024-2025", "2024/2025" to "2024-2025"
        year = year.replace("/", "-").trim();
        String[] parts = year.split("-");
        if (parts.length == 2 && parts[1].length() == 2) {
            // e.g. "2024-25" → "2024-2025"
            String firstYear = parts[0];
            int yearNum = Integer.parseInt(firstYear) + 1;
            return firstYear + "-" + yearNum;
        }
        return year;
    }

    private Map<String, Object> extractQuarterlyTds(String text) {
        Matcher m = QUARTERLY_TDS.matcher(text);
        if (m.find() && m.groupCount() >= 4) {
            try {
                Map<String, Object> quarterly = new LinkedHashMap<>();
                quarterly.put("q1", new BigDecimal(m.group(1).replaceAll("[,\\s]", "")));
                quarterly.put("q2", new BigDecimal(m.group(2).replaceAll("[,\\s]", "")));
                quarterly.put("q3", new BigDecimal(m.group(3).replaceAll("[,\\s]", "")));
                quarterly.put("q4", new BigDecimal(m.group(4).replaceAll("[,\\s]", "")));
                return quarterly;
            } catch (NumberFormatException e) {
                log.debug("Failed to parse quarterly TDS: {}", e.getMessage());
            }
        }
        return null;
    }
}
