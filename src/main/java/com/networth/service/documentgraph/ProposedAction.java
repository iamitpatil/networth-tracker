package com.networth.service.documentgraph;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ProposedAction {
    private ActionType type;
    private String entityType;
    private UUID entityId;
    private Map<String, Object> data;
    private Confidence confidence;
}
