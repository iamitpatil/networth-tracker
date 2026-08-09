package com.networth.service.documentgraph;

public interface ProcessingNode {
    String getName();
    void process(GraphState state);
}
