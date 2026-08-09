package com.networth.service.documentgraph.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.config.AiConfig;
import com.networth.model.entity.AiChatSession;
import com.networth.model.entity.AiPendingAction;
import com.networth.repository.AiChatSessionRepository;
import com.networth.repository.AiPendingActionRepository;
import com.networth.service.documentgraph.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class McpAIChatService {

    private final AiClient aiClient;
    private final McpToolClient mcpToolClient;
    private final ObjectMapper objectMapper;
    private final AiChatSessionRepository sessionRepository;
    private final AiPendingActionRepository pendingActionRepository;
    private final AiConfig aiConfig;

    private static final Set<String> AGENT_TOOLS = Set.of(
            "analyze_stock", "portfolio_doctor", "tax_advisor",
            "goal_planner", "debt_optimizer", "spend_analyzer",
            "market_scout", "sip_optimizer"
    );

    private static final Set<String> EXECUTION_TOOLS = Set.of(
            "create_transaction", "update_cc_spend", "update_salary",
            "update_holding", "update_account_balance", "link_document"
    );

    private static final List<String> GREETING_PATTERNS = List.of(
            "hello", "hi ", "hey", "good morning", "good evening", "good afternoon",
            "what can you do", "help", "who are you", "thanks", "thank you",
            "how are you", "what's up", "sup"
    );

    private static final Set<String> CONFIRMATION_WORDS = Set.of(
            "yes", "yep", "yeah", "yea", "sure", "ok", "okay", "confirm", "confirmed",
            "approve", "approved", "go", "do it", "go ahead", "proceed", "record",
            "record it", "save", "save it", "add", "add it", "submit", "done",
            "no", "nope", "nah", "cancel", "reject", "stop", "nevermind", "skip"
    );

    private boolean isNonFinancialQuery(String message, boolean hasSessionHistory) {
        String lower = message.toLowerCase().trim();

        // If the user has prior conversation history and sends a short confirmation/rejection,
        // treat it as a follow-up — NOT a greeting. The AI needs tools + context to handle it.
        if (hasSessionHistory && (CONFIRMATION_WORDS.contains(lower) || lower.length() < 15)) {
            return false;
        }

        // Check greetings
        for (String g : GREETING_PATTERNS) {
            if (lower.startsWith(g) || lower.equals(g)) {
                return true;
            }
        }
        // Very short messages (under 6 chars) with no session history are greetings
        if (message.length() < 6) {
            return true;
        }
        // Short messages without financial keywords
        if (message.length() < 120) {
            boolean hasFinancialKeyword = lower.contains("bill") || lower.contains("statement")
                    || lower.contains("salary") || lower.contains("form 16") || lower.contains("form16")
                    || lower.contains("cas") || lower.contains("card") || lower.contains("transaction")
                    || lower.contains("import") || lower.contains("portfolio") || lower.contains("holding")
                    || lower.contains("stock") || lower.contains("invest") || lower.contains("fund")
                    || lower.contains("account") || lower.contains("balance") || lower.contains("spend")
                    || lower.contains("report") || lower.contains("net worth") || lower.contains("networth")
                    || lower.contains("buy") || lower.contains("sell") || lower.contains("sip")
                    || lower.contains("document") || lower.contains("extract") || lower.contains("classify")
                    || lower.contains("performance") || lower.contains("return") || lower.contains("value")
                    || lower.contains("profit") || lower.contains("loss") || lower.contains("dividend")
                    || lower.contains("analysis") || lower.contains("analyze") || lower.contains("advice")
                    || lower.contains("recommend") || lower.contains("strategy") || lower.contains("allocate")
                    || lower.contains("diversify") || lower.contains("diversified") || lower.contains("exposure") || lower.contains("sector")
                    || lower.contains("goal") || lower.contains("save") || lower.contains("tax")
                    || lower.contains("income") || lower.contains("expense") || lower.contains("budget")
                    || lower.contains("upstox") || lower.contains("zerodha") || lower.contains("groww")
                    || lower.contains("mf") || lower.contains("mutual") || lower.contains("nav")
                    || lower.contains("price") || lower.contains("rate") || lower.contains("change")
                    || lower.contains("today") || lower.contains("month") || lower.contains("year")
                    || lower.contains("summary") || lower.contains("overview") || lower.contains("dashboard");
            return !hasFinancialKeyword;
        }
        return false;
    }

    private static final String SYSTEM_PROMPT = """
You are the NetWorth Tracker AI — a unified financial assistant for the NetWorth Tracker app.
You handle ALL user requests using function calling with the tools available to you.

== RULE #1: USE TOOLS, DON'T ASK FOR DATA ==
You have tools to look up the user's financial data. ALWAYS use them instead of asking the user.
- "What's my net worth?" → call get_net_worth
- "Show my portfolio" → call get_portfolio_summary and/or search_holdings
- "Analyze my investments" → call get_portfolio_summary, get_asset_allocation, calculate_xirr
- "Am I on track for retirement?" → call get_goals, then get_goal_progress
- "What tax do I owe?" → call calculate_capital_gains
NEVER say "Could you provide details?" when you can look it up.

== RULE #2: ASK CLARIFYING QUESTIONS WHEN AMBIGUOUS ==
If the user's request is vague or could mean multiple things, ASK a specific question.
Do NOT guess. Do NOT call tools randomly. Examples:
- "Help me with taxes" → Ask: "Would you like me to: (1) Calculate your capital gains for this year, (2) Compare old vs new tax regime, or (3) Check your 80C utilization?"
- "Update my data" → Ask: "What would you like to update? Your account balance, a transaction, or salary details?"
- "I need advice" → Ask: "What kind of advice? I can help with portfolio diversification, tax planning, goal tracking, or debt management."
Always present 2-4 specific options so the user can choose.

== AVAILABLE TOOLS BY CATEGORY ==

**Financial Overview:**
- get_net_worth — Total net worth with asset breakdown
- get_portfolio_summary — Invested, current value, P&L, returns
- get_financial_health_score — Health score (0-100) with recommendations

**Portfolio Analytics:**
- search_holdings — Search/list holdings with values
- get_asset_allocation — Allocation by asset type (equity, debt, gold, etc.)
- get_sector_allocation — Allocation by sector (IT, banking, FMCG, etc.)
- calculate_xirr — True annualized return

**Tax:**
- calculate_capital_gains — LTCG/STCG per asset class for a financial year
- compare_tax_regimes — Old vs new regime comparison

**Goals & Liabilities:**
- get_goals — List financial goals
- get_goal_progress — Detailed progress for a goal
- get_liabilities — List loans
- calculate_emi — EMI calculator (no user data needed)

**Document Processing:**
- classify_document, extract_credit_card_bill, extract_salary_slip, extract_bank_statement, extract_nps_statement, extract_generic
- resolve_entity — Match extracted entities to portfolio

**Search:**
- search_holdings, search_accounts, search_credit_cards

**Record Data (HITL — needs user approval):**
- create_transaction, update_cc_spend, update_salary, update_account_balance

**Expert Agents (deep multi-step analysis — use for complex requests):**
- analyze_stock — Deep-dive on a specific stock
- portfolio_doctor — Full portfolio health check with action plan
- tax_advisor — Comprehensive tax planning
- goal_planner — Financial goal planning and tracking
- debt_optimizer — Debt reduction strategy
- spend_analyzer — Credit card spend analysis and budgeting
- market_scout — Market news scan for portfolio holdings
- sip_optimizer — SIP review and optimization

**Additional Tools:**
- get_sip_calendar, get_spend_reports, get_monthly_spend, get_spend_trend
- get_payment_summary, search_news, get_portfolio_news
- get_rebalancing_suggestions, get_loan_summary
- list_salaries — List all salary records (check for duplicates before importing)

== INTENT → TOOL MAPPING ==

1. **GREETING** → No tools. Respond conversationally.
2. **NET WORTH / OVERVIEW** → get_net_worth, get_portfolio_summary
3. **PORTFOLIO ANALYSIS** → search_holdings, get_asset_allocation, get_sector_allocation
4. **DEEP PORTFOLIO REVIEW** → portfolio_doctor agent
5. **SPECIFIC STOCK ANALYSIS** → analyze_stock agent
6. **TAX QUESTIONS** → tax_advisor agent (comprehensive) or calculate_capital_gains (quick)
7. **GOAL PLANNING** → goal_planner agent (comprehensive) or get_goals (quick list)
8. **DEBT / LOANS** → debt_optimizer agent (comprehensive) or get_liabilities (quick list)
9. **SPENDING / BUDGET** → spend_analyzer agent (comprehensive) or get_spend_reports (quick)
10. **MARKET NEWS** → market_scout agent (portfolio-wide) or search_news (specific topic)
11. **SIP / MUTUAL FUNDS** → sip_optimizer agent (comprehensive) or get_sip_calendar (quick)
12. **HEALTH CHECK** → get_financial_health_score
13. **REBALANCING** → get_rebalancing_suggestions
14. **DOCUMENT PROCESSING** → classify_document → appropriate extract tool → resolve_entity → present → ask to record
15. **RECORD DATA** → Propose clearly, then call execution tool only after "yes"
16. **AMBIGUOUS** → Ask clarifying question with 2-4 options. Do NOT guess.

== DOCUMENT UPLOAD RULES ==
When the user message contains "--- Extracted PDF Content ---" or "--- File Content ---":
- The document text has ALREADY been extracted from the uploaded file. It is inline in the message.
- Do NOT ask the user to paste text. The text is already there.
- IMMEDIATELY call classify_document with the extracted text content (everything after the --- separator).
- Based on classification result, call the matching extract tool:
  CREDIT_CARD_BILL → extract_credit_card_bill
  SALARY_SLIP → extract_salary_slip
  BANK_STATEMENT → extract_bank_statement
  NPS_STATEMENT → extract_nps_statement
  Any other type → extract_generic
- Present extracted data clearly in a FORMATTED TABLE before asking to record.
- NEVER say "please paste the text" or "provide the document text" — you already have it.

After extracting data from a document, follow this flow per document type:

SALARY_SLIP:
  1. Call extract_salary_slip with the full PDF text → returns JSON with employerName, payDate, grossPay, netPay, components (with earnings/deductions sub-objects)
  2. Call list_salaries to check if this salary already exists (match by employer + payDate to avoid duplicates)
  3. Do NOT call resolve_entity (salary data is self-contained)
  4. Present the extracted data as SEPARATE tables:
     - Summary: Employer, Employee (if found), Pay Period, Pay Date
     - Earnings table: iterate components.earnings object — show each key (Basic, HRA, etc.) with its amount
     - Deductions table: iterate components.deductions object — show each key (PF, Tax, etc.) with its amount
     - Totals: Gross Pay, Total Deductions, **Net Pay**
     IMPORTANT: components contains nested objects like {"earnings": {"Basic": 45000}, "deductions": {"PF": 5400}}.
     Show each key-value pair. Do NOT try to format the object itself as a number (that causes NaN).
  5. Tell the user: "An editable salary card has appeared below. You can review and modify any values, then click Save Salary to record it."
     The frontend will automatically render an EditableSalaryCard with the extracted data.
  6. If user asks to correct any value, acknowledge and tell them to edit it in the card below.
  7. Do NOT call update_salary tool — the frontend EditableSalaryCard handles saving directly via the API.
  8. If extraction fails or returns empty data, tell the user what went wrong and ask them to try again or enter the data manually on the Salaries page.

CREDIT_CARD_BILL:
  1. Call extract_credit_card_bill → get issuer, transactions, totals
  2. Call resolve_entity to match card issuer to user's credit cards
  3. Present: issuer, card last 4, statement period, total due, transactions table
  4. Ask: "Would you like me to save this credit card spend data?"
  5. On approval: call update_cc_spend with the parsed bill data

BANK_STATEMENT:
  1. Call extract_bank_statement → get bank, account, transactions, balances
  2. Call resolve_entity to match bank account
  3. Present: bank, account, period, opening/closing balance, transactions
  4. Ask: "Would you like me to update the account balance?"
  5. On approval: call update_account_balance

NPS_STATEMENT:
  1. Call extract_nps_statement → get PRAN, schemes, units, NAV, contributions
  2. Do NOT call resolve_entity (NPS data is self-contained)
  3. Present: PRAN, fund manager, tier, scheme-wise units/NAV/value, contributions table
  4. Ask: "Would you like me to record these NPS transactions?"

FORM_16:
  1. Call extract_form16 → get employer, PAN, income, deductions, tax
  2. Do NOT call resolve_entity
  3. Present: employer, assessment year, gross salary, deductions, taxable income, tax paid

Any other type:
  1. Call extract_generic
  2. Call resolve_entity if entities found
  3. Present extracted data
  4. Suggest appropriate action

IMPORTANT: Only call execution tools AFTER the user explicitly confirms ("yes", "approve", "record it").

== HITL RULES ==
Execution tools (create_transaction, update_cc_spend, update_salary, update_account_balance):
  NEVER call without explicit user confirmation. Present the extracted data first, then wait for approval.
All other tools: Call freely — they are read-only and safe.

== STYLE ==
- Concise, practical. Bullet points and numbers.
- Always cite specific numbers from tool results.
- If data is empty, say so clearly: "You don't have any X yet."
- For ambiguous requests, ask a specific clarifying question.
""";

    @Transactional
    public McpChatResult chat(String sessionId, String userId, String message, String mode) {
        UUID userUuid = UUID.fromString(userId);

        // Load or create session
        AiChatSession session = loadOrCreateSession(sessionId, userUuid, mode);

        // Build messages array from session history + new message
        List<Map<String, Object>> messages = new ArrayList<>(session.getMessages());

        // Add system prompt (first message always)
        List<Map<String, Object>> fullMessages = new ArrayList<>();
        fullMessages.add(Map.of("role", "system", "content", buildSystemPrompt(mode, userId)));
        for (Map<String, Object> m : messages) {
            fullMessages.add(m);
        }
        fullMessages.add(Map.of("role", "user", "content", message));

        // Pre-filter: skip function calling for greetings and short non-financial queries
        boolean hasHistory = !messages.isEmpty();
        if (isNonFinancialQuery(message, hasHistory)) {
            log.debug("Bypassing function calling for non-financial query: {}", message);
            String response = aiClient.chat(buildSystemPrompt(mode, userId), message,
                    messages.stream().map(m -> Map.of(
                            "role", (String) m.getOrDefault("role", "user"),
                            "content", (String) m.getOrDefault("content", ""))
                    ).collect(Collectors.toList()));
            if (response == null) response = "Hello! I'm your NetWorth Tracker AI. I can help with document processing, portfolio queries, and financial advice. What would you like help with?";
            session.getMessages().add(Map.of("role", "user", "content", message));
            session.getMessages().add(Map.of("role", "assistant", "content", response));
            session.setTitle(session.getTitle() != null ? session.getTitle() :
                    message.length() > 60 ? message.substring(0, 57) + "..." : message);
            trimMessages(session);
            sessionRepository.save(session);
            return new McpChatResult(response, List.of(), session.getId().toString(), null);
        }

        List<Map<String, Object>> tools = buildToolDefinitions();
        List<Map<String, Object>> toolCallTrace = new ArrayList<>();

        if (tools == null || tools.isEmpty()) {
            String response = aiClient.chat(buildSystemPrompt(mode, userId), message,
                    messages.stream().map(m -> Map.of(
                            "role", (String) m.getOrDefault("role", "user"),
                            "content", (String) m.getOrDefault("content", ""))
                    ).collect(Collectors.toList()));
            if (response == null) response = "AI server unavailable";
            session.getMessages().add(Map.of("role", "user", "content", message));
            session.getMessages().add(Map.of("role", "assistant", "content", response));
            trimMessages(session);
            sessionRepository.save(session);
            return new McpChatResult(response, List.of(), session.getId().toString(), null);
        }

        int rounds = 0;
        Map<String, Object> pendingAction = null;

        while (rounds < aiConfig.getMaxToolRounds()) {
            rounds++;

            AiClient.ChatWithToolsResponse response = aiClient.chatWithToolsFull(
                    buildSystemPrompt(mode, userId), fullMessages, tools);
            if (response == null) {
                String msg = "AI server unavailable. Please check if the AI model is running.";
                return new McpChatResult(msg, toolCallTrace, session.getId().toString(), null);
            }

            Map<String, Object> assistantMsg = new LinkedHashMap<>();
            assistantMsg.put("role", "assistant");
            if (response.content() != null) {
                assistantMsg.put("content", response.content());
            }
            if (response.hasToolCalls()) {
                assistantMsg.put("tool_calls", response.toolCalls());
            }
            fullMessages.add(assistantMsg);

            if (!response.hasToolCalls()) {
                // Save session
                String content = response.content() != null ? response.content() : "";
                session.getMessages().add(Map.of("role", "user", "content", message));
                session.getMessages().add(Map.of("role", "assistant", "content", content));
                if (!toolCallTrace.isEmpty()) {
                    session.getToolCallTraces().add(toolCallTrace);
                }
                if (session.getTitle() == null && message.length() > 10) {
                    session.setTitle(message.length() > 60 ? message.substring(0, 57) + "..." : message);
                }
                trimMessages(session);
                sessionRepository.save(session);

                return new McpChatResult(content, toolCallTrace, session.getId().toString(),
                        pendingAction != null ? pendingAction.get("id").toString() : null);
            }

            for (Map<String, Object> toolCall : response.toolCalls()) {
                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                if (function == null) continue;

                String toolName = (String) function.get("name");
                String argumentsStr = (String) function.get("arguments");
                String toolCallId = (String) toolCall.get("id");
                if (toolCallId == null) toolCallId = "call_" + UUID.randomUUID().toString().substring(0, 8);

                Map<String, Object> arguments;
                try {
                    arguments = objectMapper.readValue(argumentsStr, LinkedHashMap.class);
                } catch (Exception e) {
                    arguments = new LinkedHashMap<>();
                }

                Set<String> toolsWithoutUserId = Set.of("link_document");
                if (!toolsWithoutUserId.contains(toolName)) {
                    arguments.putIfAbsent("userId", userId);
                }

                long startTime = System.currentTimeMillis();

                // Check if tool actually exists
                if (!mcpToolClient.hasTool(toolName)) {
                    Map<String, Object> toolResult = new LinkedHashMap<>();
                    toolResult.put("error", "Unknown tool: " + toolName);
                    toolResult.put("availableTools", mcpToolClient.getToolList().stream()
                            .map(t -> t.get("name")).collect(Collectors.toList()));
                    long elapsed = System.currentTimeMillis() - startTime;
                    Map<String, Object> traceEntry = new LinkedHashMap<>();
                    traceEntry.put("tool", toolName);
                    traceEntry.put("arguments", arguments);
                    traceEntry.put("result", toolResult);
                    traceEntry.put("durationMs", elapsed);
                    traceEntry.put("type", "error");
                    toolCallTrace.add(traceEntry);
                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", toolCallId);
                    toolMsg.put("content", "{\"error\":\"Unknown tool: " + toolName + "\"}");
                    fullMessages.add(toolMsg);
                    continue;
                }

                // HITL: intercept execution tools
                Map<String, Object> toolResult;
                boolean isExecutionTool = EXECUTION_TOOLS.contains(toolName);

                if (isExecutionTool) {
                    // Create a pending action instead of executing
                    AiPendingAction pending = new AiPendingAction();
                    pending.setUserId(userUuid);
                    pending.setSessionId(session.getId());
                    pending.setToolName(toolName);
                    pending.setArguments(arguments);
                    pending.setSummary(buildSummary(toolName, arguments));
                    pending.setStatus("pending");
                    pending.setExpiresAt(Instant.now().plus(aiConfig.getPendingActionExpiryDays(), ChronoUnit.DAYS));
                    pending = pendingActionRepository.save(pending);

                    toolResult = new LinkedHashMap<>();
                    toolResult.put("pendingActionId", pending.getId().toString());
                    toolResult.put("status", "pending_approval");
                    toolResult.put("message", "This action requires your approval. Please confirm to proceed.");

                    pendingAction = new LinkedHashMap<>();
                    pendingAction.put("id", pending.getId().toString());
                    pendingAction.put("toolName", toolName);
                    pendingAction.put("summary", pending.getSummary());
                    pendingAction.put("arguments", arguments);
                } else {
                    toolResult = mcpToolClient.callTool(toolName, arguments);
                }

                long elapsed = System.currentTimeMillis() - startTime;

                Map<String, Object> traceEntry = new LinkedHashMap<>();
                traceEntry.put("tool", toolName);
                traceEntry.put("arguments", arguments);
                traceEntry.put("result", toolResult);
                traceEntry.put("durationMs", elapsed);
                traceEntry.put("type", isExecutionTool ? "pending_approval" : "executed");
                toolCallTrace.add(traceEntry);

                Map<String, Object> toolMsg = new LinkedHashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCallId);
                try {
                    String content = objectMapper.writeValueAsString(toolResult);
                    // Truncate tool results to prevent context overflow
                    int maxChars = aiConfig.getToolResultMaxChars();
                    if (content.length() > maxChars) {
                        content = content.substring(0, maxChars - 3) + "...";
                    }
                    toolMsg.put("content", content);
                } catch (Exception e) {
                    toolMsg.put("content", "{}");
                }
                fullMessages.add(toolMsg);

            }
        }

        // After tool loop ends, do a final summary call without tools
        String finalResponse = summarize(fullMessages, buildSystemPrompt(mode, userId));

        // Save to session
        session.getMessages().add(Map.of("role", "user", "content", message));
        session.getMessages().add(Map.of("role", "assistant", "content", finalResponse));
        if (!toolCallTrace.isEmpty()) {
            session.getToolCallTraces().add(toolCallTrace);
        }
        if (session.getTitle() == null && message.length() > 10) {
            session.setTitle(message.length() > 60 ? message.substring(0, 57) + "..." : message);
        }
        trimMessages(session);
        sessionRepository.save(session);

        return new McpChatResult(finalResponse, toolCallTrace, session.getId().toString(),
                pendingAction != null ? pendingAction.get("id").toString() : null);
    }

    private String summarize(List<Map<String, Object>> fullMessages, String systemPrompt) {
        // Build a clean messages array: system prompt + last few exchanges + a summary request
        List<Map<String, Object>> summaryMsgs = new ArrayList<>();
        summaryMsgs.add(Map.of("role", "system", "content", systemPrompt));

        // Include last 4 messages from the conversation (2 exchanges)
        int start = Math.max(0, fullMessages.size() - 4);
        for (int i = start; i < fullMessages.size(); i++) {
            Map<String, Object> m = fullMessages.get(i);
            if (!"system".equals(m.get("role"))) {
                Map<String, Object> copy = new LinkedHashMap<>();
                copy.put("role", m.get("role"));
                // Only include text content, omit tool_calls/tool_call_id to save context
                if (m.containsKey("content")) {
                    copy.put("content", m.get("content"));
                } else {
                    copy.put("content", "[tool call results available in trace]");
                }
                summaryMsgs.add(copy);
            }
        }

        summaryMsgs.add(Map.of("role", "user", "content",
                "Based on the tool results, provide a concise final response to the user. Do not call any tools."));

        return summarizeWithRetry(summaryMsgs);
    }

    private String summarizeWithRetry(List<Map<String, Object>> summaryMsgs) {
        try {
            // Send a simple chat request (no tools) to get a clean summary
            String response = aiClient.chat(
                    (String) summaryMsgs.get(0).get("content"),
                    (String) summaryMsgs.get(summaryMsgs.size() - 1).get("content"),
                    summaryMsgs.subList(1, summaryMsgs.size() - 1).stream()
                            .map(m -> Map.of("role", (String) m.get("role"), "content", (String) m.getOrDefault("content", "")))
                            .collect(Collectors.toList())
            );
            if (response != null && !response.isBlank()) return response;
        } catch (Exception e) {
            log.warn("Summary call failed: {}", e.getMessage());
        }

        // Fallback: try the most recent non-tool assistant message
        for (int i = summaryMsgs.size() - 1; i >= 0; i--) {
            Object role = summaryMsgs.get(i).get("role");
            Object content = summaryMsgs.get(i).get("content");
            if ("assistant".equals(role) && content != null && !content.toString().isBlank()) {
                return content.toString();
            }
        }
        return "Analysis complete. Please review the details above.";
    }

    @Async
    public void streamChat(SseEmitter emitter, String sessionId, String userId, String message, String mode) {
        UUID userUuid = UUID.fromString(userId);
        AiChatSession session = loadOrCreateSession(sessionId, userUuid, mode);
        List<Map<String, Object>> toolCallTrace = new CopyOnWriteArrayList<>();
        Map<String, Object> pendingActionRef = new LinkedHashMap<>();

        try {
            // Add user message to session
            session.getMessages().add(Map.of("role", "user", "content", message));

            // Build messages array
            List<Map<String, Object>> messages = new ArrayList<>(session.getMessages());
            List<Map<String, Object>> fullMessages = new ArrayList<>();
            fullMessages.add(Map.of("role", "system", "content", buildSystemPrompt(mode, userId)));
            for (Map<String, Object> m : messages) {
                fullMessages.add(m);
            }

            // Greeting pre-filter
            boolean hasHistory = !session.getMessages().isEmpty();
            if (isNonFinancialQuery(message, hasHistory)) {
                // Pass session history so the AI understands context for follow-ups
                List<Map<String, String>> history = messages.stream()
                        .map(m -> Map.of(
                                "role", String.valueOf(m.getOrDefault("role", "user")),
                                "content", String.valueOf(m.getOrDefault("content", ""))))
                        .collect(Collectors.toList());
                String response = aiClient.chat(buildSystemPrompt(mode, userId), message, history);
                if (response == null) response = "Hello! I can help with documents, portfolio, and advice.";
                sendEvent(emitter, "thought", Map.of("content", "Analyzing your message..."));
                Map<String, Object> greetingResp = new LinkedHashMap<>();
                greetingResp.put("content", response);
                greetingResp.put("sessionId", session.getId().toString());
                sendEvent(emitter, "response", greetingResp);
                session.getMessages().add(Map.of("role", "assistant", "content", response));
                session.setTitle(session.getTitle() != null ? session.getTitle() :
                        message.length() > 60 ? message.substring(0, 57) + "..." : message);
                trimMessages(session);
                sessionRepository.save(session);
                emitter.complete();
                return;
            }

            List<Map<String, Object>> tools = buildToolDefinitions();
            if (tools == null || tools.isEmpty()) {
                String response = aiClient.chat(buildSystemPrompt(mode, userId), message, List.of());
                if (response == null) response = "AI server unavailable";
                Map<String, Object> noToolResp = new LinkedHashMap<>();
                noToolResp.put("content", response);
                noToolResp.put("sessionId", session.getId().toString());
                sendEvent(emitter, "response", noToolResp);
                session.getMessages().add(Map.of("role", "assistant", "content", response));
                trimMessages(session);
                sessionRepository.save(session);
                emitter.complete();
                return;
            }

            int rounds = 0;
            while (rounds < aiConfig.getMaxToolRounds()) {
                rounds++;

                if (!sendEvent(emitter, "thought", Map.of("round", rounds, "content", "Thinking..."))) {
                    log.info("Client disconnected during streaming, aborting tool loop");
                    return;
                }

                final int currentRound = rounds;
                AiClient.ChatWithToolsResponse response;

                if (aiConfig.isStreamTokens()) {
                    // Token-level streaming: forward reasoning tokens to browser in real-time
                    response = aiClient.chatWithToolsStreaming(
                            buildSystemPrompt(mode, userId), fullMessages, tools,
                            new AiClient.StreamCallback() {
                                @Override
                                public void onReasoningToken(String token) {
                                    sendEvent(emitter, "reasoning_delta", Map.of("token", token, "round", currentRound));
                                }
                                @Override
                                public void onContentToken(String token) {
                                    // Content tokens arrive after reasoning; could stream these too in future
                                }
                                @Override
                                public void onToolCallDelta(int index, String id, String name, String argsDelta) {
                                    // Tool call args stream in; we already show tool_start/tool_end events
                                }
                                @Override
                                public void onComplete(String finishReason) {
                                    // Signal end of reasoning for this round
                                    sendEvent(emitter, "reasoning_done", Map.of("round", currentRound));
                                }
                                @Override
                                public void onError(String error) {
                                    log.warn("Streaming AI error in round {}: {}", currentRound, error);
                                }
                            });
                } else {
                    // Non-streaming: get full response at once
                    response = aiClient.chatWithToolsFull(
                            buildSystemPrompt(mode, userId), fullMessages, tools);
                    // Send full reasoning block if available
                    if (response != null && response.reasoningContent() != null && !response.reasoningContent().isBlank()) {
                        sendEvent(emitter, "reasoning", Map.of("content", response.reasoningContent(), "round", currentRound));
                    }
                }

                if (response == null) {
                    sendEvent(emitter, "response", Map.of("content", "AI server unavailable."));
                    emitter.complete();
                    return;
                }

                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                if (response.content() != null) {
                    assistantMsg.put("content", response.content());
                }
                if (response.hasToolCalls()) {
                    assistantMsg.put("tool_calls", response.toolCalls());
                }
                fullMessages.add(assistantMsg);

                if (!response.hasToolCalls()) {
                    // If AI skipped reasoning (no reasoning_delta tokens emitted) but has content,
                    // emit a brief synthetic reasoning note so the UI shows round 2 activity
                    if (rounds > 1 && (response.reasoningContent() == null || response.reasoningContent().isBlank())) {
                        sendEvent(emitter, "reasoning_delta", Map.of("token", "Presenting results from tool analysis.", "round", rounds));
                        sendEvent(emitter, "reasoning_done", Map.of("round", rounds));
                    }
                    String content = response.content() != null ? response.content() : "";
                    session.getMessages().add(Map.of("role", "assistant", "content", content));
                    if (!toolCallTrace.isEmpty()) {
                        session.getToolCallTraces().add(new ArrayList<>(toolCallTrace));
                    }
                    if (session.getTitle() == null && message.length() > 10) {
                        session.setTitle(message.length() > 60 ? message.substring(0, 57) + "..." : message);
                    }
                    Map<String, Object> responseEvent = new LinkedHashMap<>();
                    responseEvent.put("content", content);
                    responseEvent.put("sessionId", session.getId().toString());
                    if (!pendingActionRef.isEmpty()) {
                        responseEvent.put("pendingActionId", pendingActionRef.get("id"));
                    }
                    sendEvent(emitter, "response", responseEvent);
                    trimMessages(session);
                    sessionRepository.save(session);
                    emitter.complete();
                    return;
                }

                for (Map<String, Object> toolCall : response.toolCalls()) {
                    Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                    if (function == null) continue;

                    String toolName = (String) function.get("name");
                    String argumentsStr = (String) function.get("arguments");

                    Map<String, Object> arguments;
                    try {
                        arguments = objectMapper.readValue(argumentsStr, LinkedHashMap.class);
                    } catch (Exception e) {
                        arguments = new LinkedHashMap<>();
                    }

                    String toolCallId = (String) toolCall.get("id");
                    if (toolCallId == null) toolCallId = "call_" + UUID.randomUUID().toString().substring(0, 8);

                    Set<String> toolsWithoutUserId = Set.of("link_document");
                    if (!toolsWithoutUserId.contains(toolName)) {
                        arguments.putIfAbsent("userId", userId);
                    }

                    // Check tool exists
                    if (!mcpToolClient.hasTool(toolName)) {
                        Map<String, Object> err = Map.of("error", "Unknown tool: " + toolName);
                        sendEvent(emitter, "tool_start", Map.of("name", toolName, "arguments", arguments));
                        sendEvent(emitter, "tool_end", Map.of("name", toolName, "result", err, "durationMs", 0));
                        Map<String, Object> toolMsg = new LinkedHashMap<>();
                        toolMsg.put("role", "tool");
                        toolMsg.put("tool_call_id", toolCallId);
                        toolMsg.put("content", "{\"error\":\"Unknown tool: " + toolName + "\"}");
                        fullMessages.add(toolMsg);
                        continue;
                    }

                    sendEvent(emitter, "tool_start", Map.of("name", toolName, "arguments", arguments));

                    boolean isExecutionTool = EXECUTION_TOOLS.contains(toolName);
                    Map<String, Object> toolResult;
                    long startTime = System.currentTimeMillis();

                    if (isExecutionTool) {
                        AiPendingAction pending = new AiPendingAction();
                        pending.setUserId(userUuid);
                        pending.setSessionId(session.getId());
                        pending.setToolName(toolName);
                        pending.setArguments(arguments);
                        pending.setSummary(buildSummary(toolName, arguments));
                        pending.setStatus("pending");
                        pending.setExpiresAt(Instant.now().plus(aiConfig.getPendingActionExpiryDays(), ChronoUnit.DAYS));
                        pending = pendingActionRepository.save(pending);

                        toolResult = new LinkedHashMap<>();
                        toolResult.put("pendingActionId", pending.getId().toString());
                        toolResult.put("status", "pending_approval");
                        toolResult.put("message", "Requires your approval.");
                        pendingActionRef.put("id", pending.getId().toString());
                    } else if (AGENT_TOOLS.contains(toolName)) {
                        // Agent tools get SSE context for streaming their internal reasoning
                        String agentId = toolCallId;
                        sendEvent(emitter, "agent_start", Map.of("name", toolName, "agentId", agentId));
                        try {
                            AgentContext.set(emitter, objectMapper, agentId, mcpToolClient);
                            toolResult = mcpToolClient.callTool(toolName, arguments);
                        } finally {
                            AgentContext.clear();
                        }
                        sendEvent(emitter, "agent_end", Map.of("name", toolName, "agentId", agentId));
                    } else {
                        toolResult = mcpToolClient.callTool(toolName, arguments);
                    }

                    long elapsed = System.currentTimeMillis() - startTime;

                    Map<String, Object> traceEntry = new LinkedHashMap<>();
                    traceEntry.put("tool", toolName);
                    traceEntry.put("arguments", arguments);
                    traceEntry.put("result", toolResult);
                    traceEntry.put("durationMs", elapsed);
                    traceEntry.put("type", isExecutionTool ? "pending_approval" : "executed");
                    toolCallTrace.add(traceEntry);

                    sendEvent(emitter, "tool_end", Map.of(
                            "name", toolName,
                            "result", toolResult,
                            "durationMs", elapsed,
                            "type", isExecutionTool ? "pending_approval" : "executed"
                    ));

                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", toolCallId);
                    String resultContent;
                    try {
                        resultContent = objectMapper.writeValueAsString(toolResult);
                        int maxTrunc = aiConfig.getToolResultMaxChars();
                        if (resultContent.length() > maxTrunc) resultContent = resultContent.substring(0, maxTrunc - 3) + "...";
                    } catch (Exception e) {
                        resultContent = "{}";
                    }
                    toolMsg.put("content", resultContent);
                    fullMessages.add(toolMsg);
                }
            }

            // Summarize — send a thought event so the UI shows progress
            sendEvent(emitter, "thought", Map.of("content", "Preparing final response..."));
            String finalResponse = summarize(fullMessages, buildSystemPrompt(mode, userId));
            session.getMessages().add(Map.of("role", "assistant", "content", finalResponse));
            if (!toolCallTrace.isEmpty()) {
                session.getToolCallTraces().add(new ArrayList<>(toolCallTrace));
            }
            if (session.getTitle() == null && message.length() > 10) {
                session.setTitle(message.length() > 60 ? message.substring(0, 57) + "..." : message);
            }
            Map<String, Object> responseMap = new LinkedHashMap<>();
            responseMap.put("content", finalResponse);
            responseMap.put("sessionId", session.getId().toString());
            if (!pendingActionRef.isEmpty()) {
                responseMap.put("pendingActionId", pendingActionRef.get("id"));
            }
            sendEvent(emitter, "response", responseMap);
            trimMessages(session);
            sessionRepository.save(session);
            emitter.complete();
        } catch (Exception e) {
            log.warn("Streaming error: {}", e.getMessage() != null ? e.getMessage() : "unknown");
            try {
                Map<String, Object> errData = new LinkedHashMap<>();
                errData.put("message", e.getMessage() != null ? e.getMessage() : "Unknown error");
                sendEvent(emitter, "error", errData);
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Sends an SSE event. Returns false if the client disconnected (emit failed),
     * signaling the caller to abort processing.
     */
    private boolean sendEvent(SseEmitter emitter, String eventName, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(objectMapper.writeValueAsString(data)));
            return true;
        } catch (Exception e) {
            log.warn("Failed to send SSE event {} (client likely disconnected): {}", eventName, e.getMessage());
            return false;
        }
    }

    @Transactional
    public Map<String, Object> confirmAction(String userId, String actionId) {
        UUID userUuid = UUID.fromString(userId);
        UUID actionUuid = UUID.fromString(actionId);

        AiPendingAction pending = pendingActionRepository.findByIdAndUserId(actionUuid, userUuid)
                .orElseThrow(() -> new IllegalArgumentException("Pending action not found"));

        if (!"pending".equals(pending.getStatus())) {
            throw new IllegalStateException("Action is already " + pending.getStatus());
        }

        // Check if the action has expired
        if (pending.getExpiresAt() != null && Instant.now().isAfter(pending.getExpiresAt())) {
            pending.setStatus("expired");
            pendingActionRepository.save(pending);
            throw new IllegalStateException("Action has expired and can no longer be executed");
        }

        pending.setStatus("approved");
        pendingActionRepository.save(pending);

        Map<String, Object> arguments = pending.getArguments();
        Set<String> toolsWithoutUserId = Set.of("link_document");
        if (!toolsWithoutUserId.contains(pending.getToolName())) {
            arguments.putIfAbsent("userId", userId);
        }

        Map<String, Object> result;
        try {
            result = mcpToolClient.callTool(pending.getToolName(), arguments);
        } catch (Exception e) {
            pending.setStatus("failed");
            pending.setResult(Map.of("error", e.getMessage() != null ? e.getMessage() : "Tool execution failed"));
            pendingActionRepository.save(pending);
            throw new IllegalStateException("Tool execution failed: " + (e.getMessage() != null ? e.getMessage() : "unknown error"));
        }

        // Check if the tool itself returned an error
        if (result != null && result.containsKey("error")) {
            pending.setStatus("failed");
            pending.setResult(result);
            pendingActionRepository.save(pending);
            throw new IllegalStateException("Tool returned error: " + result.get("error"));
        }

        pending.setStatus("executed");
        pending.setResult(result);
        pendingActionRepository.save(pending);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "executed");
        response.put("toolName", pending.getToolName());
        response.put("summary", pending.getSummary());
        response.put("result", result);
        return response;
    }

    @Transactional
    public Map<String, Object> rejectAction(String userId, String actionId) {
        UUID userUuid = UUID.fromString(userId);
        UUID actionUuid = UUID.fromString(actionId);

        AiPendingAction pending = pendingActionRepository.findByIdAndUserId(actionUuid, userUuid)
                .orElseThrow(() -> new IllegalArgumentException("Pending action not found"));

        pending.setStatus("rejected");
        pendingActionRepository.save(pending);

        return Map.of("status", "rejected", "actionId", actionId);
    }

    public List<Map<String, Object>> getSessions(String userId) {
        UUID userUuid = UUID.fromString(userId);
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userUuid).stream()
                .map(s -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", s.getId().toString());
                    entry.put("title", s.getTitle());
                    entry.put("mode", s.getMode());
                    entry.put("messageCount", s.getMessages().size() / 2);
                    entry.put("createdAt", s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
                    entry.put("updatedAt", s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null);
                    return entry;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSession(String userId, String sessionId) {
        UUID userUuid = UUID.fromString(userId);
        UUID sessionUuid = UUID.fromString(sessionId);

        AiChatSession session = sessionRepository.findById(sessionUuid)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getUserId().equals(userUuid)) {
            throw new IllegalArgumentException("Session not found");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", session.getId().toString());
        result.put("title", session.getTitle());
        result.put("mode", session.getMode());
        result.put("messages", session.getMessages());
        result.put("toolCallTraces", session.getToolCallTraces());
        result.put("createdAt", session.getCreatedAt() != null ? session.getCreatedAt().toString() : null);
        result.put("updatedAt", session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : null);
        return result;
    }

    @Transactional
    public void deleteSession(String userId, String sessionId) {
        UUID userUuid = UUID.fromString(userId);
        UUID sessionUuid = UUID.fromString(sessionId);
        // Clean up orphan pending actions before deleting session
        pendingActionRepository.deleteBySessionId(sessionUuid);
        sessionRepository.deleteByUserIdAndId(userUuid, sessionUuid);
    }

    public List<Map<String, Object>> getPendingActions(String userId) {
        UUID userUuid = UUID.fromString(userId);
        return pendingActionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userUuid, "pending")
                .stream()
                .map(a -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", a.getId().toString());
                    entry.put("toolName", a.getToolName());
                    entry.put("summary", a.getSummary());
                    entry.put("arguments", a.getArguments());
                    entry.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
                    return entry;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, String>> getToolDefinitions() {
        List<Map<String, String>> tools = mcpToolClient.getToolList();
        return tools.stream()
                .map(t -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("name", t.get("name"));
                    entry.put("description", t.get("description"));
                    return entry;
                })
                .collect(Collectors.toList());
    }

    // ---- Private helpers ----

    private AiChatSession loadOrCreateSession(String sessionId, UUID userUuid, String mode) {
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                UUID id = UUID.fromString(sessionId);
                return sessionRepository.findById(id)
                        .filter(s -> s.getUserId().equals(userUuid))
                        .orElseGet(() -> createSession(userUuid, mode));
            } catch (Exception e) {
                return createSession(userUuid, mode);
            }
        }
        return createSession(userUuid, mode);
    }

    private AiChatSession createSession(UUID userUuid, String mode) {
        AiChatSession session = new AiChatSession();
        session.setUserId(userUuid);
        session.setMode(mode);
        session.setMessages(new ArrayList<>());
        session.setToolCallTraces(new ArrayList<>());
        return sessionRepository.save(session);
    }

    private void trimMessages(AiChatSession session) {
        List<Map<String, Object>> msgs = session.getMessages();
        if (msgs.size() > aiConfig.getMaxSessionMessages() * 2) {
            int excess = msgs.size() - aiConfig.getMaxSessionMessages() * 2;
            // Keep the first few messages (context) + trim the oldest
            List<Map<String, Object>> trimmed = new ArrayList<>(msgs.subList(excess, msgs.size()));
            session.getToolCallTraces().clear(); // Reset traces when trimming
            session.setMessages(trimmed);
        }
    }

    private String buildSummary(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "create_transaction" -> {
                String symbol = (String) args.getOrDefault("holdingId", "?");
                String type = (String) args.getOrDefault("type", "?");
                Object amt = args.getOrDefault("amount", "?");
                yield String.format("Record %s transaction for %s of ₹%s", type, symbol, amt);
            }
            case "update_cc_spend" -> "Record credit card spend report";
            case "update_salary" -> {
                String employer = (String) args.getOrDefault("employerName", "?");
                Object pay = args.getOrDefault("netPay", "?");
                yield String.format("Record salary from %s of ₹%s", employer, pay);
            }
            case "update_holding" -> "Update investment holding details";
            case "update_account_balance" -> {
                Object bal = args.getOrDefault("balance", "?");
                yield String.format("Update account balance to ₹%s", bal);
            }
            case "link_document" -> "Link document to entity";
            default -> toolName.replace("_", " ");
        };
    }

    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, String>> tools = mcpToolClient.getToolList();
        if (tools.isEmpty()) return null;

        Map<String, List<Map<String, Object>>> toolParams = new LinkedHashMap<>();
        toolParams.put("classify_document", List.of(param("text", "string", "The full text content of the document to classify", true)));
        toolParams.put("extract_credit_card_bill", List.of(param("text", "string", "The full text of the credit card bill or statement", true)));
        toolParams.put("extract_salary_slip", List.of(param("text", "string", "The full text of the salary slip", true)));
        toolParams.put("extract_bank_statement", List.of(param("text", "string", "The full text of the bank statement", true)));
        toolParams.put("extract_cas", List.of(param("text", "string", "The full text of the Consolidated Account Statement", true)));
        toolParams.put("extract_form16", List.of(param("text", "string", "The full text of the Form 16 document", true)));
        toolParams.put("extract_generic", List.of(param("text", "string", "The full text of the financial document", true)));
        toolParams.put("resolve_entity", List.of(
                param("extractedData", "object", "The extracted data from the document to match against user's portfolio", true)
        ));
        toolParams.put("search_holdings", List.of(
                param("query", "string", "Search query for holding symbol or name", true),
                param("limit", "integer", "Maximum number of results to return", false)
        ));
        toolParams.put("search_accounts", List.of(
                param("query", "string", "Search query for account name or number", true),
                param("limit", "integer", "Maximum number of results to return", false)
        ));
        toolParams.put("search_credit_cards", List.of(
                param("query", "string", "Search query for credit card issuer or last four digits", true),
                param("limit", "integer", "Maximum number of results to return", false)
        ));
        toolParams.put("create_transaction", List.of(
                param("holdingId", "string", "The ID of the holding to add the transaction to", true),
                param("type", "string", "Transaction type: BUY, SELL, SIP, LUMPSUM, DEPOSIT, WITHDRAWAL, CONTRIBUTION", true),
                param("amount", "number", "Transaction amount in INR", true),
                param("date", "string", "Transaction date in YYYY-MM-DD format", true),
                param("description", "string", "Optional description for the transaction", true)
        ));
        toolParams.put("update_cc_spend", List.of(
                param("billData", "object", "The parsed credit card bill data including transactions", true)
        ));
        toolParams.put("update_salary", List.of(
                param("employerName", "string", "Name of the employer", true),
                param("netPay", "number", "Net take-home pay amount in INR", true),
                param("payDate", "string", "Pay date in YYYY-MM-DD format", true),
                param("components", "object", "Earnings and deductions breakdown", false)
        ));
        toolParams.put("update_holding", List.of(
                param("holdingId", "string", "The ID of the holding to update", true),
                param("data", "object", "The holding data fields to update", true)
        ));
        toolParams.put("update_account_balance", List.of(
                param("accountId", "string", "The ID of the bank account to update", true),
                param("balance", "number", "The new current balance in INR", true)
        ));
        toolParams.put("link_document", List.of(
                param("documentId", "string", "The ID of the document to link", true),
                param("entityType", "string", "Entity type: HOLDING, ACCOUNT, CREDIT_CARD", true),
                param("entityId", "string", "The ID of the entity to link to", true)
        ));

        // ── Agent Tools ──
        toolParams.put("analyze_stock", List.of(
                param("symbolOrName", "string", "Stock symbol or name to analyze (e.g. INFY, Reliance, TCS)", true)
        ));
        toolParams.put("portfolio_doctor", List.of());
        toolParams.put("tax_advisor", List.of());
        toolParams.put("goal_planner", List.of());
        toolParams.put("debt_optimizer", List.of());
        toolParams.put("spend_analyzer", List.of());
        toolParams.put("market_scout", List.of());
        toolParams.put("sip_optimizer", List.of());

        // ── Financial Analytics Tools ──
        toolParams.put("get_net_worth", List.of());
        toolParams.put("get_portfolio_summary", List.of());
        toolParams.put("get_financial_health_score", List.of());
        toolParams.put("get_asset_allocation", List.of());
        toolParams.put("get_sector_allocation", List.of());
        toolParams.put("calculate_xirr", List.of());
        toolParams.put("calculate_capital_gains", List.of(
                param("financialYear", "string", "Financial year in YYYY-YYYY format, e.g. 2024-2025", true)
        ));
        toolParams.put("compare_tax_regimes", List.of(
                param("grossSalary", "number", "Annual gross salary in INR", true),
                param("totalDeductions", "number", "Total deductions under old regime (80C, 80D, HRA, etc.) in INR", true),
                param("hraExemption", "number", "HRA exemption amount in INR (0 if not applicable)", false)
        ));
        toolParams.put("get_goals", List.of());
        toolParams.put("get_goal_progress", List.of(
                param("goalId", "string", "Goal ID (UUID)", true)
        ));
        toolParams.put("get_liabilities", List.of());
        toolParams.put("calculate_emi", List.of(
                param("principal", "number", "Loan principal amount in INR", true),
                param("annualRate", "number", "Annual interest rate (e.g. 8.5 for 8.5%)", true),
                param("tenureMonths", "number", "Loan tenure in months", true)
        ));
        toolParams.put("get_sip_calendar", List.of());
        toolParams.put("get_spend_reports", List.of());
        toolParams.put("get_monthly_spend", List.of(
                param("month", "string", "Month in YYYY-MM format", true)
        ));
        toolParams.put("get_spend_trend", List.of());
        toolParams.put("get_payment_summary", List.of());
        toolParams.put("list_salaries", List.of());
        toolParams.put("search_news", List.of(
                param("query", "string", "Search query (stock name, topic)", true),
                param("limit", "integer", "Max results", false)
        ));
        toolParams.put("get_portfolio_news", List.of());
        toolParams.put("get_rebalancing_suggestions", List.of(
                param("riskProfile", "string", "Risk profile: conservative, moderate, or aggressive", true)
        ));
        toolParams.put("get_loan_summary", List.of(
                param("liabilityId", "string", "Loan/Liability ID (UUID)", true)
        ));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, String> tool : tools) {
            String name = tool.get("name");
            Map<String, Object> paramsMap = new LinkedHashMap<>();
            paramsMap.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            List<Map<String, Object>> params = toolParams.getOrDefault(name, List.of());
            for (Map<String, Object> p : params) {
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", p.get("type"));
                prop.put("description", p.get("description"));
                properties.put((String) p.get("name"), prop);
                if (Boolean.TRUE.equals(p.get("required"))) {
                    required.add((String) p.get("name"));
                }
            }
            paramsMap.put("properties", properties);
            paramsMap.put("required", required);

            Map<String, Object> functionDef = new LinkedHashMap<>();
            functionDef.put("name", name);
            functionDef.put("description", tool.get("description"));
            functionDef.put("parameters", paramsMap);

            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", "function");
            toolDef.put("function", functionDef);

            result.add(toolDef);
        }
        return result;
    }

    private Map<String, Object> param(String name, String type, String description, boolean required) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("type", type);
        p.put("description", description);
        p.put("required", required);
        return p;
    }

    private String buildSystemPrompt(String mode, String userId) {
        if ("import".equals(mode)) {
            return SYSTEM_PROMPT + """

When the user provides transaction descriptions or document text, follow the intent graph.
Always present extracted data as a structured proposal and ask for confirmation before recording.
""";
        }
        return SYSTEM_PROMPT;
    }

    public record McpChatResult(
            String response,
            List<Map<String, Object>> toolCalls,
            String sessionId,
            String pendingActionId
    ) {}
}
