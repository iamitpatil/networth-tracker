package com.networth.service.documentgraph.nodes;

import com.networth.service.documentgraph.GraphState;
import com.networth.service.documentgraph.ProcessingNode;
import com.networth.service.tax.Form16Parser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class Form16ExtractionNode implements ProcessingNode {

    private final Form16Parser form16Parser;

    @Override
    public String getName() {
        return "extractForm16";
    }

    @Override
    public void process(GraphState state) {
        String text = state.getRawText();
        if (text == null || text.isBlank()) {
            state.setError("No text to extract Form 16 data from");
            return;
        }

        try {
            var form16Data = form16Parser.parseText(text);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("employerName", form16Data.getEmployerName());
            result.put("employerPan", form16Data.getEmployerPan());
            result.put("employeePan", form16Data.getEmployeePan());
            result.put("financialYear", form16Data.getFinancialYear());
            result.put("assessmentYear", form16Data.getAssessmentYear());
            result.put("grossSalary", form16Data.getGrossSalary());
            result.put("exemptAllowances", form16Data.getExemptAllowances());
            result.put("standardDeduction", form16Data.getStandardDeduction());
            result.put("professionalTax", form16Data.getProfessionalTax());
            result.put("taxableSalary", form16Data.getTaxableSalary());
            result.put("tdsTotal", form16Data.getTdsTotal());
            result.put("section80c", form16Data.getSection80c());
            result.put("section80ccd1b", form16Data.getSection80ccd1b());
            result.put("section80d", form16Data.getSection80d());
            result.put("section80g", form16Data.getSection80g());
            result.put("confidenceScore", form16Data.getParseConfidence());
            state.setExtractedData(result);
            log.debug("Extracted Form 16 for {} (confidence: {})", form16Data.getEmployerName(), form16Data.getParseConfidence());
        } catch (Exception e) {
            log.warn("Form 16 parsing failed: {}", e.getMessage());
            state.setError("Failed to parse Form 16 data");
        }
    }
}
