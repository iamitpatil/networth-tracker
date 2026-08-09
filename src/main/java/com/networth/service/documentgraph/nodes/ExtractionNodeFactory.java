package com.networth.service.documentgraph.nodes;

import com.networth.service.documentgraph.DocumentType;
import com.networth.service.documentgraph.ProcessingNode;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ExtractionNodeFactory {

    private final Map<DocumentType, ProcessingNode> extractionNodes;

    private final ProcessingNode genericNode;

    public ExtractionNodeFactory(
            CreditCardExtractionNode ccNode,
            SalaryExtractionNode salaryNode,
            BankStatementExtractionNode bankStmtNode,
            CasExtractionNode casNode,
            Form16ExtractionNode form16Node,
            GenericExtractionNode genericNode) {
        this.genericNode = genericNode;
        this.extractionNodes = Map.of(
                DocumentType.CREDIT_CARD_BILL, ccNode,
                DocumentType.SALARY_SLIP, salaryNode,
                DocumentType.BANK_STATEMENT, bankStmtNode,
                DocumentType.CAS, casNode,
                DocumentType.FORM_16, form16Node
        );
    }

    public ProcessingNode nodeFor(DocumentType type) {
        return extractionNodes.getOrDefault(type, genericNode);
    }
}
