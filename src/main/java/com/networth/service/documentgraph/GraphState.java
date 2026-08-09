package com.networth.service.documentgraph;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class GraphState {
    private UUID userId;
    private UUID documentId;
    private UUID correlationId;

    private String originalFilename;
    private String contentType;
    private byte[] fileContent;
    private String password;
    private boolean autoExecute;

    private String rawText;
    private DocumentType classifiedAs;
    private double classificationConfidence;
    private String classificationReasoning;
    private Map<String, Object> extractedData;

    private List<MatchedEntity> matchedEntities;
    private List<ProposedAction> proposedActions;
    private List<ExecutedAction> executedActions;

    private String currentNodeName;
    private boolean completed;
    private String error;
    private boolean requiresHumanReview;
}
