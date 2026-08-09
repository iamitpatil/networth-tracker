package com.networth.service.documentgraph;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutedAction {
    private ProposedAction action;
    private boolean success;
    private Object result;
    private String errorMessage;
}
