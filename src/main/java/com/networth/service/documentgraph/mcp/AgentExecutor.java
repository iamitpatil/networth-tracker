package com.networth.service.documentgraph.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.config.AiConfig;
import com.networth.service.documentgraph.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent sub-orchestrator. Runs its own tool-calling loop (like the main orchestrator)
 * but scoped to a subset of tools and with its own system prompt.
 *
 * Streams progress through AgentContext SSE events:
 * - agent_step: progress text
 * - agent_reasoning_delta: AI thinking tokens
 * - agent_reasoning_done: round complete
 * - agent_tool_start / agent_tool_end: tool invocations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentExecutor {

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final AiConfig aiConfig;

    /**
     * Run an agent sub-loop.
     *
     * @param systemPrompt  Agent persona prompt
     * @param userMessage   The task
     * @param allowedTools  Set of tool names this agent can call
     * @param maxRounds     Max tool-calling rounds
     * @param userId        User ID injected into tool args
     * @return Final text response from the agent
     */
    public String execute(String systemPrompt, String userMessage,
                          Set<String> allowedTools, int maxRounds, String userId,
                          McpToolClient toolClient) {

        // Build filtered tool definitions
        List<Map<String, Object>> toolDefs = buildFilteredToolDefs(allowedTools, toolClient);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));

        int rounds = 0;
        while (rounds < maxRounds) {
            rounds++;
            final int currentRound = rounds;

            AgentContext.step("Thinking (round " + rounds + "/" + maxRounds + ")...");

            // Call AI with streaming reasoning
            AiClient.ChatWithToolsResponse response = aiClient.chatWithToolsStreaming(
                    systemPrompt, messages, toolDefs,
                    new AiClient.StreamCallback() {
                        @Override
                        public void onReasoningToken(String token) {
                            AgentContext.reasoningToken(token);
                        }
                        @Override public void onContentToken(String token) { }
                        @Override public void onToolCallDelta(int idx, String id, String name, String args) { }
                        @Override
                        public void onComplete(String finishReason) {
                            AgentContext.reasoningDone();
                        }
                        @Override
                        public void onError(String error) {
                            log.warn("Agent round {} error: {}", currentRound, error);
                        }
                    });

            if (response == null) {
                return "Agent AI unavailable.";
            }

            // Add assistant message to history
            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            if (response.content() != null) assistantMsg.put("content", response.content());
            if (response.hasToolCalls()) assistantMsg.put("tool_calls", response.toolCalls());
            messages.add(assistantMsg);

            // No tool calls = final answer
            if (!response.hasToolCalls()) {
                return response.content() != null ? response.content() : "";
            }

            // Execute tool calls
            for (Map<String, Object> toolCall : response.toolCalls()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                if (function == null) continue;

                String toolName = (String) function.get("name");
                String argumentsStr = (String) function.get("arguments");
                String toolCallId = toolCall.get("id") != null ? toolCall.get("id").toString()
                        : "call_" + UUID.randomUUID().toString().substring(0, 8);

                Map<String, Object> arguments;
                try {
                    arguments = objectMapper.readValue(argumentsStr, LinkedHashMap.class);
                } catch (Exception e) {
                    arguments = new LinkedHashMap<>();
                }
                arguments.putIfAbsent("userId", userId);

                // Verify tool is allowed
                if (!allowedTools.contains(toolName) || !toolClient.hasTool(toolName)) {
                    messages.add(toolErrorMsg(toolCallId, "Tool not available: " + toolName));
                    continue;
                }

                // Emit agent tool events
                AgentContext.sendEvent("agent_tool_start",
                        Map.of("name", toolName, "arguments", arguments));

                long start = System.currentTimeMillis();
                Map<String, Object> result = toolClient.callTool(toolName, arguments);
                long elapsed = System.currentTimeMillis() - start;

                // Truncate result for SSE (full result goes to LLM context)
                String resultPreview;
                try {
                    resultPreview = objectMapper.writeValueAsString(result);
                    if (resultPreview.length() > 2000) resultPreview = resultPreview.substring(0, 1997) + "...";
                } catch (Exception e) {
                    resultPreview = "{}";
                }
                AgentContext.sendEvent("agent_tool_end",
                        Map.of("name", toolName, "durationMs", elapsed, "result", resultPreview));

                // Add tool result to conversation
                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCallId);
                try {
                    String content = objectMapper.writeValueAsString(result);
                    int max = aiConfig.getToolResultMaxChars();
                    if (content.length() > max) content = content.substring(0, max - 3) + "...";
                    toolMsg.put("content", content);
                } catch (Exception e) {
                    toolMsg.put("content", "{}");
                }
                messages.add(toolMsg);
            }
        }

        // Loop exhausted — final summary
        AgentContext.step("Preparing final report...");
        messages.add(Map.of("role", "user", "content",
                "Based on all the data, provide your final analysis. Do not call any more tools."));

        AiClient.ChatWithToolsResponse finalResp = aiClient.chatWithToolsStreaming(
                systemPrompt, messages, null,
                new AiClient.StreamCallback() {
                    @Override public void onReasoningToken(String token) { AgentContext.reasoningToken(token); }
                    @Override public void onContentToken(String token) { }
                    @Override public void onToolCallDelta(int idx, String id, String name, String args) { }
                    @Override public void onComplete(String finishReason) { AgentContext.reasoningDone(); }
                    @Override public void onError(String error) { }
                });

        return finalResp != null && finalResp.content() != null ? finalResp.content() : "Analysis complete.";
    }

    /**
     * Build OpenAI-compatible tool definitions filtered to allowed set.
     * Includes basic parameter schemas so the AI knows what args to pass.
     */
    private List<Map<String, Object>> buildFilteredToolDefs(Set<String> allowedTools, McpToolClient toolClient) {
        // Common param schemas for tools agents might call
        Map<String, Map<String, Object>> paramSchemas = new LinkedHashMap<>();
        paramSchemas.put("search_holdings", Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Search query (symbol or name, use empty string for all)"),
                        "limit", Map.of("type", "integer", "description", "Max results")),
                "required", List.of("query")));
        paramSchemas.put("search_accounts", Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Search query (bank name or account name)")),
                "required", List.of("query")));
        paramSchemas.put("search_credit_cards", Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Search query (issuer or last 4)")),
                "required", List.of("query")));
        paramSchemas.put("calculate_capital_gains", Map.of(
                "type", "object",
                "properties", Map.of(
                        "financialYear", Map.of("type", "string", "description", "FY in YYYY-YYYY format")),
                "required", List.of("financialYear")));
        paramSchemas.put("compare_tax_regimes", Map.of(
                "type", "object",
                "properties", Map.of(
                        "grossSalary", Map.of("type", "number", "description", "Annual gross salary INR"),
                        "totalDeductions", Map.of("type", "number", "description", "Total deductions INR")),
                "required", List.of("grossSalary", "totalDeductions")));
        paramSchemas.put("get_goal_progress", Map.of(
                "type", "object",
                "properties", Map.of(
                        "goalId", Map.of("type", "string", "description", "Goal ID (UUID)")),
                "required", List.of("goalId")));

        // Default: no params needed (userId is injected automatically)
        Map<String, Object> emptyParams = Map.of("type", "object", "properties", Map.of(), "required", List.of());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, String> tool : toolClient.getToolList()) {
            String name = tool.get("name");
            if (!allowedTools.contains(name)) continue;

            Map<String, Object> functionDef = new LinkedHashMap<>();
            functionDef.put("name", name);
            functionDef.put("description", tool.get("description"));
            functionDef.put("parameters", paramSchemas.getOrDefault(name, emptyParams));

            result.add(Map.of("type", "function", "function", functionDef));
        }
        return result;
    }

    private Map<String, Object> toolErrorMsg(String toolCallId, String error) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "tool");
        msg.put("tool_call_id", toolCallId);
        msg.put("content", "{\"error\":\"" + error + "\"}");
        return msg;
    }
}
