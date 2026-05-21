package com.networth.service.documentgraph.mcp.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.service.documentgraph.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentExtractionTools {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    private static final String CC_BILL_PROMPT = """
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
- transactions (array of objects, each with: date, description, amount, category)
- spendSummary (object mapping category to total amount)

Categorize each transaction intelligently based on the merchant name/description.
Use null for any field you cannot determine. No explanation, no markdown fences, just the JSON object.
""";

    private static final String SALARY_PROMPT = """
You are a salary slip parser. Extract all earnings, deductions, and net pay from the salary slip text below.
Return ONLY a valid JSON object with keys:
- employerName (string)
- payDate (string, YYYY-MM-DD format)
- grossPay (number)
- netPay (number)
- components (object mapping each line item name to its amount)

PayDate MUST be in YYYY-MM-DD format. Use null for missing values. No explanation, no markdown, just JSON.
""";

    private static final String GENERIC_PROMPT = """
You are a financial data extractor. Extract all financial information from this document text.
Look for transactions, account details, card details, employer details, investment details.

Return a JSON object:
{
  "documentType": "best guess of document type",
  "entities": [{ "type": "HOLDING|ACCOUNT|CARD|EMPLOYER|OTHER", "name": "...", "identifier": "..." }],
  "transactions": [{ "date": "YYYY-MM-DD", "description": "...", "amount": 0, "type": "CREDIT|DEBIT", "category": "..." }],
  "financialSummary": { "totalCredits": 0, "totalDebits": 0, "startBalance": null, "endBalance": null },
  "notes": "Any other relevant information found"
}
""";

    @Tool(name = "extract_credit_card_bill", description = "Extract structured data from credit card bill/statement text")
    public Map<String, Object> extractCreditCardBill(
            @ToolParam(description = "Text content of the credit card bill") String text) {
        return callAi(CC_BILL_PROMPT, text, "credit card extraction");
    }

    @Tool(name = "extract_salary_slip", description = "Extract earnings, deductions, and net pay from a salary slip")
    public Map<String, Object> extractSalarySlip(
            @ToolParam(description = "Text content of the salary slip") String text) {
        return callAi(SALARY_PROMPT, text, "salary extraction");
    }

    @Tool(name = "extract_bank_statement", description = "Extract transactions and balances from a bank statement")
    public Map<String, Object> extractBankStatement(
            @ToolParam(description = "Text content of the bank statement") String text) {
        return callAi(GENERIC_PROMPT, text, "bank statement extraction");
    }

    @Tool(name = "extract_cas", description = "Extract holdings and transactions from a Consolidated Account Statement")
    public Map<String, Object> extractCas(
            @ToolParam(description = "Text content of the CAS document") String text) {
        if (text == null || text.isBlank()) {
            return Map.of("source", "CAS", "error", "No text to extract");
        }
        return Map.of("source", "CAS", "rawLength", text.length(),
                "note", "Full CAS parsing requires dedicated regex extraction; data passed through AI extraction");
    }

    @Tool(name = "extract_form16", description = "Extract income and deduction details from a Form 16 document")
    public Map<String, Object> extractForm16(
            @ToolParam(description = "Text content of the Form 16 document") String text) {
        // Form 16 uses the existing Form16Parser, which takes MultipartFile
        // This tool extracts the text structure; actual parsing uses the dedicated parser
        return callAi(GENERIC_PROMPT, text, "Form 16 extraction");
    }

    @Tool(name = "extract_generic", description = "Extract any financial information from a generic financial document")
    public Map<String, Object> extractGeneric(
            @ToolParam(description = "Text content of the document") String text) {
        return callAi(GENERIC_PROMPT, text, "generic extraction");
    }

    private Map<String, Object> callAi(String prompt, String text, String context) {
        if (text == null || text.isBlank()) {
            return Map.of("error", "No text to extract", "note", context + ": empty input");
        }

        String response = aiClient.chat(prompt, text);
        if (response == null) {
            return Map.of("note", "AI server unavailable for " + context, "rawLength", text.length());
        }

        try {
            String cleaned = response.replaceAll("```json\\s*|```\\s*", "").trim();
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse {} response: {}", context, e.getMessage());
            return Map.of("note", "Failed to parse AI extraction", "rawText", text.length() > 1000 ? text.substring(0, 1000) : text);
        }
    }
}
