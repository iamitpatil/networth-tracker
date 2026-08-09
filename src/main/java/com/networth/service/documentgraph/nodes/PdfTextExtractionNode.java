package com.networth.service.documentgraph.nodes;

import com.networth.service.CreditCardBillParser;
import com.networth.service.documentgraph.GraphState;
import com.networth.service.documentgraph.ProcessingNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
@Slf4j
public class PdfTextExtractionNode implements ProcessingNode {

    @Override
    public String getName() {
        return "pdfTextExtract";
    }

    @Override
    public void process(GraphState state) {
        byte[] bytes = state.getFileContent();
        if (bytes == null || bytes.length == 0) {
            state.setError("No file content to extract text from");
            return;
        }

        String filename = state.getOriginalFilename();
        boolean isPdf = filename != null && filename.toLowerCase().endsWith(".pdf");

        if (isPdf) {
            try {
                byte[] pdfBytes = new ByteArrayInputStream(bytes).readAllBytes();
                PDDocument doc;
                String password = state.getPassword();
                if (password != null && !password.isBlank()) {
                    doc = Loader.loadPDF(pdfBytes, password);
                } else {
                    doc = Loader.loadPDF(pdfBytes);
                }
                try (doc) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String text = stripper.getText(doc);
                    if (!text.isBlank()) {
                        state.setRawText(text);
                        log.debug("Extracted {} chars from PDF {}", text.length(), filename);
                        return;
                    }
                }
            } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
                state.setError("PASSWORD_REQUIRED");
                state.setRequiresHumanReview(true);
                return;
            } catch (Exception e) {
                log.warn("PDF extraction failed, falling back to raw text: {}", e.getMessage());
            }
        }

        state.setRawText(new String(bytes));
    }
}
