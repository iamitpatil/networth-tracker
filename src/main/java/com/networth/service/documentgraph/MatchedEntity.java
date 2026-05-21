package com.networth.service.documentgraph;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class MatchedEntity {
    private String entityType;
    private UUID entityId;
    private String entityLabel;
    private double matchConfidence;
}
