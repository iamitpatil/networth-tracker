# AI Orchestrator — Design & Implementation

> **Status:** Production | **Last updated:** 2026-05-22
> **Model:** Gemma 4 E4B (4.5B effective params, 128K context, native function calling + thinking)
> **Stack:** Spring Boot + llama.cpp + React SSE
> **Tools:** 46 tools across 6 classes + 8 expert agents

---

## Table of Contents

1. [Vision](#1-vision)
2. [Architecture](#2-architecture)
3. [AI Configuration](#3-ai-configuration)
4. [Function Calling & Tool Loop](#4-function-calling--tool-loop)
5. [Token Streaming & Reasoning](#5-token-streaming--reasoning)
6. [SSE Protocol](#6-sse-protocol)
7. [MCP Tools (32)](#7-mcp-tools)
8. [Expert Agents](#8-expert-agents)
9. [Human-in-the-Loop (HITL)](#9-human-in-the-loop-hitl)
10. [PDF Processing](#10-pdf-processing)
11. [Session Management](#11-session-management)
12. [System Prompt & Intent Classification](#12-system-prompt--intent-classification)
13. [Frontend Implementation](#13-frontend-implementation)
14. [Security & Safety](#14-security--safety)
15. [File References](#15-file-references)
16. [Decision Log](#16-decision-log)

---

## 1. Vision

The AI chat is a **universal orchestrator** for the NetWorth Tracker app. Upload any financial document, ask about your portfolio, or request data changes — the AI:

1. **Understands** what you need (intent classification + clarifying questions)
2. **Searches** your portfolio data via 32 tools (holdings, accounts, cards, net worth, analytics)
3. **Delegates** complex analysis to expert agents (stock analyst, portfolio doctor, tax advisor)
4. **Extracts** structured data from documents including PDFs (CC bills, salary slips, statements)
5. **Resolves** entities against your existing portfolio
6. **Proposes** actions and waits for approval (HITL)
7. **Executes** them after confirmation

---

## 2. Architecture

```
Browser (React)                Spring Boot (:8080)               Gemma 4 E4B (:8082)
     |                              |                                   |
     | fetch() SSE                  |                                   |
     |----------------------------->|                                   |
     |                              | AIChatController                  |
     |                              |   POST /ai/chat-stream            |
     |                              |   POST /ai/chat-upload-stream     |
     |                              |                                   |
     |                              | McpAIChatService (@Async)         |
     |                              |   |                               |
     |  event: thought              |   | Intent pre-filter             |
     |<-----------------------------|   | Session load/create           |
     |                              |   |                               |
     |                              |   | AiClient.chatWithToolsStreaming()
     |                              |   |------------------------------>|
     |  event: reasoning_delta      |   |  stream=true                  |
     |<-----------------------------|   |<--- delta.reasoning_content    |
     |  (token by token)            |   |       (token by token)        |
     |                              |   |                               |
     |  event: reasoning_done       |   |<--- finish_reason: tool_calls |
     |<-----------------------------|   |                               |
     |                              |   |                               |
     |  event: tool_start           |   | Regular tool:                 |
     |<-----------------------------|   |   McpToolClient.callTool()    |
     |  event: tool_end             |   |                               |
     |<-----------------------------|   |                               |
     |                              |   | Agent tool:                   |
     |  event: agent_start          |   |   AgentContext.set(emitter)   |
     |<-----------------------------|   |   AgentExecutor.execute()     |
     |  event: agent_step           |   |     |                        |
     |  event: agent_reasoning_delta|   |     | Sub-loop:              |
     |  event: agent_tool_start     |   |     |  AI decides tools      |
     |  event: agent_tool_end       |   |     |  Calls tools           |
     |  event: agent_reasoning_done |   |     |  Streams reasoning     |
     |  event: agent_end            |   |     |  Returns analysis      |
     |<-----------------------------|   |   AgentContext.clear()        |
     |  event: tool_end             |   |                               |
     |<-----------------------------|   |                               |
     |                              |   | (next round...)               |
     |  event: response             |   |                               |
     |<-----------------------------|   | Save session + traces         |
     |                              |                                   |

Tool Classes (in-process, 46 tools):
  DocumentClassificationTools  (1 tool)
  DocumentExtractionTools      (6 tools)
  EntityResolutionTools        (4 tools)
  ExecutionTools               (6 tools)
  FinancialAnalyticsTools      (21 tools)
  AgentTools                   (8 agents)
```

### Key Components

| Component | File | Role |
|---|---|---|
| **AiConfig** | `config/AiConfig.java` | Centralized config (`@ConfigurationProperties(prefix="ai")`) |
| **AiClient** | `service/documentgraph/AiClient.java` | HTTP client to llama.cpp (streaming + non-streaming) |
| **McpAIChatService** | `service/documentgraph/mcp/McpAIChatService.java` | Main orchestrator: tool loop, HITL, sessions, SSE |
| **AgentExecutor** | `service/documentgraph/mcp/AgentExecutor.java` | Sub-orchestrator for expert agents (own tool loop) |
| **AgentContext** | `service/documentgraph/mcp/AgentContext.java` | ThreadLocal SSE bridge for agents to stream events |
| **McpToolClient** | `service/documentgraph/mcp/McpToolClient.java` | Tool registry + execution via `MethodToolCallbackProvider` |
| **AIChatController** | `controller/AIChatController.java` | REST endpoints + PDF extraction |
| **AIChat.jsx** | `web/src/pages/AIChat.jsx` | Full-page chat UI |
| **FloatingChat.jsx** | `web/src/components/FloatingChat.jsx` | Floating widget chat UI |

---

## 3. AI Configuration

All settings centralized in `AiConfig.java`, configurable via `application.properties` or environment variables:

```properties
ai.server-url=${AI_SERVER_URL:http://localhost:8082/v1/chat/completions}
ai.model=${AI_MODEL:llama}
ai.temperature=${AI_TEMPERATURE:0.1}
ai.connect-timeout-ms=${AI_CONNECT_TIMEOUT:120000}
ai.read-timeout-ms=${AI_READ_TIMEOUT:120000}
ai.max-tool-rounds=${AI_MAX_TOOL_ROUNDS:3}
ai.tool-result-max-chars=${AI_TOOL_RESULT_MAX_CHARS:3000}
ai.max-session-messages=${AI_MAX_SESSION_MESSAGES:50}
ai.pending-action-expiry-days=${AI_PENDING_ACTION_EXPIRY_DAYS:7}
ai.sse-timeout-ms=${AI_SSE_TIMEOUT:180000}
ai.stream-tokens=${AI_STREAM_TOKENS:true}
```

### Model: Gemma 4 E4B

| Property | Value |
|---|---|
| Parameters | 4.5B effective (8B total with embeddings) |
| GGUF size | ~5 GB (Q4_K_M quantization) |
| Context window | 128K tokens native |
| Function calling | Native support |
| Thinking mode | Built-in chain-of-thought (`reasoning_content`) |
| Multimodal | Text + Image + Audio |

---

## 4. Function Calling & Tool Loop

### Tool Loop (McpAIChatService.streamChat)

```
1. Build fullMessages = [system_prompt] + [session_history] + [user_message]
2. Pre-filter: isNonFinancialQuery(message, hasHistory)
3. Loop (up to ai.max-tool-rounds):
   a. SSE: thought event
   b. Call AiClient.chatWithToolsStreaming() — streams reasoning tokens
   c. If no tool_calls -> SSE: response event, save session, done
   d. For each tool_call:
      - If agent tool -> set AgentContext, call AgentExecutor (sub-loop)
      - If execution tool -> create AiPendingAction (HITL)
      - If read-only tool -> execute via McpToolClient
      - SSE: tool_start / agent events / tool_end
   e. If round 2+ has no reasoning, emit synthetic "Presenting results"
4. If loop exhausted -> summarize -> SSE: response
5. Save session + tool traces to DB
```

---

## 5. Token Streaming & Reasoning

`AiClient.chatWithToolsStreaming()` opens an SSE connection to llama.cpp with `stream: true` and forwards tokens via callbacks:

```java
callback.onReasoningToken(token)  // -> SSE: reasoning_delta
callback.onContentToken(token)    // -> (reserved)
callback.onComplete(finishReason) // -> SSE: reasoning_done
```

**Live UI:** Collapsible `ReasoningCard` components with streaming text + blinking cursor.
**After response:** All steps collapse into a `ThinkingSection` with ordered timeline.
**Markdown rendering:** Reasoning content renders via `ReactMarkdown` + `remarkGfm`.

---

## 6. SSE Protocol

### Main Orchestrator Events

| Event | Data | When |
|---|---|---|
| `thought` | `{ content, round }` | Before each AI call |
| `reasoning_delta` | `{ token, round }` | Each reasoning token |
| `reasoning_done` | `{ round }` | Reasoning complete for a round |
| `reasoning` | `{ content, round }` | Full reasoning (non-streaming fallback) |
| `tool_start` | `{ name, arguments }` | Before tool execution |
| `tool_end` | `{ name, result, durationMs, type }` | After tool execution |
| `response` | `{ content, sessionId, pendingActionId? }` | Final response |
| `error` | `{ message }` | On failure |

### Agent Events (nested inside agent tool calls)

| Event | Data | When |
|---|---|---|
| `agent_start` | `{ name, agentId }` | Agent begins |
| `agent_step` | `{ step, agentId }` | Progress step ("Calling search_holdings...") |
| `agent_reasoning_delta` | `{ token, agentId }` | Sub-AI reasoning token |
| `agent_reasoning_done` | `{ agentId }` | Sub-AI reasoning round complete |
| `agent_tool_start` | `{ name, arguments, agentId }` | Agent calls a tool |
| `agent_tool_end` | `{ name, durationMs, result, agentId }` | Agent tool result |
| `agent_end` | `{ name, agentId }` | Agent finished |

The `agentId` allows the frontend to associate events with the correct agent tool card, supporting parallel agent execution.

---

## 7. MCP Tools

46 tools across 6 classes:

### Document Tools (7) — `DocumentClassificationTools` + `DocumentExtractionTools`

| Tool | Status | Description |
|---|---|---|
| `classify_document` | Working | AI-powered document classification |
| `extract_credit_card_bill` | Working | CC bill extraction |
| `extract_salary_slip` | Working | Salary slip extraction |
| `extract_bank_statement` | Working | Bank statement extraction |
| `extract_cas` | Stub | CAS metadata only |
| `extract_form16` | Working | Form 16 extraction |
| `extract_generic` | Working | Fallback extraction |

### Search Tools (4) — `EntityResolutionTools`

| Tool | Status | Description |
|---|---|---|
| `search_holdings` | Working | Holdings with qty, value, avgPrice |
| `search_accounts` | Working | Bank accounts (filters soft-deleted) |
| `search_credit_cards` | Working | Cards by issuer or last 4 |
| `resolve_entity` | Working | Match entities to portfolio |

### Execution Tools (6, HITL-Protected) — `ExecutionTools`

| Tool | Status | Description |
|---|---|---|
| `create_transaction` | Working | Via TransactionService |
| `update_cc_spend` | Working | Via SpendAnalyticsService |
| `update_salary` | Working | Via SalaryService |
| `update_account_balance` | Working | Via BankAccountService |
| `update_holding` | Stub | Not implemented |
| `link_document` | Stub | Not implemented |

### Financial Analytics Tools (21) — `FinancialAnalyticsTools`

| Tool | Status | Description |
|---|---|---|
| `get_net_worth` | Working | Total net worth with asset breakdown |
| `get_portfolio_summary` | Working | Invested, current value, PnL, returns |
| `get_financial_health_score` | Working | 0-100 score with recommendations |
| `get_asset_allocation` | Working | Allocation by asset type |
| `get_sector_allocation` | Working | Allocation by sector |
| `calculate_xirr` | Working | True annualized return |
| `calculate_capital_gains` | Working | LTCG/STCG per asset class |
| `compare_tax_regimes` | Working | Old vs new regime comparison |
| `get_goals` | Working | List financial goals |
| `get_goal_progress` | Working | Goal progress with shortfall |
| `get_liabilities` | Working | List loans |
| `calculate_emi` | Working | EMI calculator |
| `get_sip_calendar` | Working | SIP schedule, upcoming/missed SIPs |
| `get_spend_reports` | Working | All CC spend reports |
| `get_monthly_spend` | Working | Month deep-dive with categories |
| `get_spend_trend` | Working | Monthly spend totals for trends |
| `get_payment_summary` | Working | Outstanding bills, due dates |
| `search_news` | Working | Search news by keyword |
| `get_portfolio_news` | Working | News for all held stocks |
| `get_rebalancing_suggestions` | Working | Target vs current allocation |
| `get_loan_summary` | Working | Loan detail with progress |

### Expert Agent Tools (8) — `AgentTools`

| Tool | Description |
|---|---|
| `analyze_stock` | Deep stock analysis via sub-orchestrator |
| `portfolio_doctor` | Full portfolio health check via sub-orchestrator |
| `tax_advisor` | Tax planning via sub-orchestrator |
| `goal_planner` | Financial goal planning via sub-orchestrator |
| `debt_optimizer` | Debt reduction strategy via sub-orchestrator |
| `spend_analyzer` | CC spend analysis via sub-orchestrator |
| `market_scout` | Portfolio news scan via sub-orchestrator |
| `sip_optimizer` | SIP review and optimization via sub-orchestrator |

---

## 8. Expert Agents

Agents are **sub-orchestrators** — they run their own AI tool-calling loop with a scoped set of tools. The AI inside the agent autonomously decides which tools to call and in what order.

### Architecture

```
Main orchestrator calls analyze_stock
  |
  v
AgentContext.set(emitter, objectMapper, agentId, toolClient)
  |
  v
AgentExecutor.execute(systemPrompt, task, allowedTools, maxRounds, userId, toolClient)
  |
  +-- Round 1: AI reasons -> decides to call search_holdings
  |     agent_step: "Thinking (round 1/4)..."
  |     agent_reasoning_delta (streaming)
  |     agent_tool_start: search_holdings
  |     agent_tool_end: search_holdings (result)
  |
  +-- Round 2: AI reasons -> calls get_portfolio_summary + get_asset_allocation
  |     agent_reasoning_delta (streaming)
  |     agent_tool_start: get_portfolio_summary
  |     agent_tool_end: (result)
  |     agent_tool_start: get_asset_allocation
  |     agent_tool_end: (result)
  |
  +-- Round 3: AI produces final analysis (no more tools)
  |     agent_step: "Preparing final report..."
  |     agent_reasoning_delta (streaming)
  |     Returns analysis text
  |
  v
AgentContext.clear()
Main orchestrator presents agent's analysis to user
```

### Agent Definitions

| Agent | Trigger Queries | Allowed Tools | Max Rounds |
|---|---|---|---|
| **Stock Analyst** | "Analyze my INFY stock", "Should I sell TCS?" | search_holdings, search_accounts, get_portfolio_summary, get_net_worth, get_asset_allocation, get_sector_allocation, calculate_xirr, get_financial_health_score, search_credit_cards | 4 |
| **Portfolio Doctor** | "Full portfolio review", "What should I change?" | search_holdings, search_accounts, search_credit_cards, get_net_worth, get_portfolio_summary, get_asset_allocation, get_sector_allocation, get_financial_health_score, calculate_xirr, get_goals, get_liabilities | 4 |
| **Tax Advisor** | "Help minimize taxes", "Tax planning for this year" | search_holdings, get_net_worth, get_portfolio_summary, calculate_capital_gains, compare_tax_regimes, get_goals, get_liabilities, get_financial_health_score | 4 |
| **Goal Planner** | "Am I on track for retirement?", "Create a savings plan" | get_goals, get_goal_progress, search_holdings, get_net_worth, get_portfolio_summary, get_sip_calendar, get_asset_allocation, get_financial_health_score | 4 |
| **Debt Optimizer** | "How to become debt-free?", "Should I prepay my loan?" | get_liabilities, get_loan_summary, calculate_emi, get_net_worth, get_portfolio_summary, get_financial_health_score, search_accounts | 4 |
| **Spend Analyzer** | "Where is my money going?", "Analyze my CC spending" | get_spend_reports, get_monthly_spend, get_spend_trend, get_payment_summary, search_credit_cards, get_net_worth | 4 |
| **Market Scout** | "Any news about my stocks?", "Market update for my portfolio" | search_holdings, get_portfolio_news, search_news, get_portfolio_summary, get_sector_allocation | 4 |
| **SIP Optimizer** | "Review my SIPs", "Optimize monthly investments" | get_sip_calendar, search_holdings, get_portfolio_summary, get_asset_allocation, get_goals, get_financial_health_score, get_rebalancing_suggestions | 4 |

### ThreadLocal Context (AgentContext)

Agents run inside `@Tool` methods called by `McpToolClient.callTool()`. They can't receive the SSE emitter as a parameter (MCP tool interface constraint). Solution: `AgentContext` uses `ThreadLocal` to pass the emitter:

```java
// McpAIChatService sets context before calling agent:
AgentContext.set(emitter, objectMapper, agentId, mcpToolClient);
try {
    toolResult = mcpToolClient.callTool(toolName, arguments);
} finally {
    AgentContext.clear();
}

// Inside agent tool, AgentContext streams events:
AgentContext.step("Fetching holdings...");
AgentContext.reasoningToken("The user");  // streamed to browser
AgentContext.sendEvent("agent_tool_start", Map.of("name", "search_holdings", ...));
```

### Circular Dependency Resolution

`AgentTools` -> `AgentExecutor` -> `McpToolClient` (needed at runtime to call tools).
`McpToolClient` -> `AgentTools` (registered as a tool provider).

Solution: `AgentExecutor` does NOT inject `McpToolClient` in its constructor. Instead, `McpToolClient` is passed through `AgentContext` at runtime and forwarded to `AgentExecutor.execute()` as a parameter.

---

## 9. Human-in-the-Loop (HITL)

Execution tools are intercepted. Frontend shows `PendingActionCard` with Approve/Reject buttons.

States: `pending` -> `approved` -> `executed` | `failed` | `rejected` | `expired`

### Clarifying Questions

When intent is ambiguous, the AI asks specific questions with 2-4 options instead of guessing:
- "Help me with taxes" -> "Would you like me to: (1) Calculate capital gains, (2) Compare tax regimes, (3) Check 80C utilization?"

---

## 10. PDF Processing

### Upload Flow

1. User attaches PDF in chat
2. `AIChatController.enrichWithFile()` detects PDF content type
3. `extractPdfText(bytes, password)` uses Apache PDFBox with `setSortByPosition(true)`
4. Extracted text prepended to user message
5. AI processes text via classify/extract tools

### Password-Protected PDFs

1. `extractPdfText` catches `InvalidPasswordException`
2. Throws `PdfPasswordRequiredException`
3. Controller returns HTTP 422: `{"error": "PASSWORD_REQUIRED", "message": "..."}`
4. Frontend shows `PdfPasswordModal` (reusable component in `Modal.jsx`)
5. User enters password, frontend retries with `password` form param
6. Wrong password -> modal re-opens with error message

### Endpoint

`POST /ai/extract-pdf-text` — Generic PDF text extraction utility for other parts of the app.

---

## 11. Session Management

`ai_chat_sessions` table (PostgreSQL JSONB). Trimming at 100 entries. Orphan pending actions deleted on session delete.

---

## 12. System Prompt & Intent Classification

### Intent -> Tool Mapping

| Intent | Tools/Action |
|---|---|
| Greeting | No tools |
| Net worth / overview | get_net_worth, get_portfolio_summary |
| Portfolio analysis | search_holdings, get_asset_allocation, get_sector_allocation |
| Deep portfolio review | portfolio_doctor agent |
| Specific stock analysis | analyze_stock agent |
| Tax questions | tax_advisor agent or calculate_capital_gains |
| Goal tracking | get_goals, get_goal_progress |
| Health check | get_financial_health_score |
| Loan / EMI | get_liabilities, calculate_emi |
| Document processing | classify -> extract -> resolve -> present |
| Record data | Propose, confirm, execute (HITL) |
| Ambiguous | Ask clarifying question with 2-4 options |

---

## 13. Frontend Implementation

### Components

| Component | Description |
|---|---|
| **AIChat** | Full-page chat with session history sidebar |
| **FloatingChat** | Floating widget with maximize button (-> /ai-chat) |
| **ThinkingSection** | Collapsible ordered timeline of reasoning + tools |
| **ReasoningCard** | Collapsible reasoning block with markdown rendering + streaming cursor |
| **LiveToolCall** | Live tool card with nested agent sub-steps |
| **AgentSubToolCard** | Collapsible agent sub-tool with args + result |
| **ToolCallCard** | Completed tool card in ThinkingSection |
| **PendingActionCard** | HITL approve/reject with wrench icon |
| **PdfPasswordModal** | Password prompt for encrypted PDFs |
| **ConfirmDialog** | Replaces all 14 native `confirm()` dialogs across the app |

### Ordered Timeline (liveSteps)

All reasoning and tool calls are stored in a single `liveSteps` array in arrival order:

```
[ reasoning(round 1), tool(analyze_stock, agent), reasoning(round 2) ]
```

Each agent tool card contains nested `agentSteps`:

```
agentSteps: [
  { type: 'step', text: 'Thinking (round 1/4)...' },
  { type: 'reasoning', content: '...', done: true },
  { type: 'tool', name: 'search_holdings', durationMs: 7, result: '...' },
  { type: 'tool', name: 'get_portfolio_summary', durationMs: 5, result: '...' },
  { type: 'step', text: 'Preparing final report...' },
  { type: 'reasoning', content: '...', done: true },
]
```

### Icons

| Element | Icon | Color |
|---|---|---|
| Regular tool | Wrench | Blue |
| Agent tool | Bot (robot) | Cyan |
| Reasoning | Brain | Purple |
| HITL pending | Wrench | Amber |

---

## 14. Security & Safety

- **Ownership:** userId injected by server, never from LLM
- **HITL:** Execution tools require explicit user approval
- **Expiration:** Pending actions expire after configurable days
- **Error isolation:** Tool failures set status to "failed" with error stored
- **Disconnect detection:** sendEvent returns false -> tool loop aborts
- **PDF passwords:** Never stored, used only for extraction then discarded
- **No native alerts:** All 14 `confirm()` calls replaced with themed `ConfirmDialog`

---

## 15. File References

### Backend

| File | Purpose |
|---|---|
| `config/AiConfig.java` | Centralized AI configuration |
| `service/documentgraph/AiClient.java` | llama.cpp client (streaming + non-streaming) |
| `service/documentgraph/mcp/McpAIChatService.java` | Main orchestrator |
| `service/documentgraph/mcp/AgentExecutor.java` | Agent sub-orchestrator |
| `service/documentgraph/mcp/AgentContext.java` | ThreadLocal SSE bridge for agents |
| `service/documentgraph/mcp/McpToolClient.java` | Tool registry + execution |
| `service/documentgraph/mcp/tools/DocumentClassificationTools.java` | 1 tool |
| `service/documentgraph/mcp/tools/DocumentExtractionTools.java` | 6 tools |
| `service/documentgraph/mcp/tools/EntityResolutionTools.java` | 4 tools |
| `service/documentgraph/mcp/tools/ExecutionTools.java` | 6 tools |
| `service/documentgraph/mcp/tools/FinancialAnalyticsTools.java` | 21 analytics tools |
| `service/documentgraph/mcp/tools/AgentTools.java` | 8 expert agents |
| `controller/AIChatController.java` | REST endpoints + PDF extraction |
| `model/entity/AiChatSession.java` | Session entity |
| `model/entity/AiPendingAction.java` | HITL entity |

### Frontend

| File | Purpose |
|---|---|
| `web/src/pages/AIChat.jsx` | Full-page chat (ordered timeline, agents, reasoning) |
| `web/src/components/FloatingChat.jsx` | Floating widget (same protocol, maximize button) |
| `web/src/components/ui/Modal.jsx` | PdfPasswordModal + ConfirmDialog |

---

## 16. Decision Log

| Date | Decision | Rationale |
|---|---|---|
| 2026-05-21 | Three-phase approach | Fastest path to working feature |
| 2026-05-21 | MCP tools in same JVM | No network overhead; transactional safety |
| 2026-05-21 | Function calling via llama.cpp | Native support; no Python needed |
| 2026-05-21 | Qwen 2.5-7B as initial model | Best function calling at 7B |
| 2026-05-22 | Switched to Gemma 4 E4B | 128K context, thinking mode, better benchmarks |
| 2026-05-22 | Real token streaming | Users see AI thinking like coding agents |
| 2026-05-22 | Centralized AiConfig | All settings configurable without recompiling |
| 2026-05-22 | Immediate rendering on response | Eliminates "stuck thinking" bug |
| 2026-05-22 | isNonFinancialQuery respects history | "yes" is a follow-up, not a greeting |
| 2026-05-22 | Tool result truncation at 3000 chars | 800 was too aggressive for 15 holdings |
| 2026-05-22 | 12 financial analytics tools | Net worth, health score, tax, goals, EMI |
| 2026-05-22 | Expert agents as sub-orchestrators | AI-driven tool selection, not hardcoded Java |
| 2026-05-22 | AgentContext via ThreadLocal | Breaks circular dependency, enables SSE from agents |
| 2026-05-22 | PDF extraction via PDFBox | Enables document upload without copy-paste |
| 2026-05-22 | PdfPasswordModal for encrypted PDFs | Seamless retry with password across the app |
| 2026-05-22 | Replace all 14 native confirm() | Consistent themed ConfirmDialog component |
| 2026-05-22 | Ordered liveSteps timeline | Reasoning + tools interleaved in arrival order |
| 2026-05-22 | Collapsible reasoning (ReasoningCard) | Consistent with collapsible tool cards |
| 2026-05-22 | Wrench icon for tools, Bot for agents | Universal icons, removed per-tool color maps |
| 2026-05-22 | Synthetic round 2 reasoning | Shows activity when model skips thinking |
| 2026-05-22 | 21 financial analytics tools | Net worth, health score, tax, goals, EMI, SIP calendar, spend reports, news, rebalancing |
| 2026-05-22 | 8 expert agents as sub-orchestrators | AI-driven tool selection per agent, not hardcoded Java |
| 2026-05-22 | AgentExecutor with own tool loop | Each agent runs up to 4 rounds, autonomously choosing tools |
| 2026-05-22 | Agent tool results in SSE | agent_tool_end includes full result for UI display |
| 2026-05-22 | 46 total tools | 7 doc + 4 search + 6 exec + 21 analytics + 8 agents |

---

## 17. Tool Count Progression

```
Phase 1 (May 21):  0 tools  — Hardcoded Java graph engine
Phase 2 (May 21): 17 tools  — MCP @Tool annotations (doc + search + exec)
Phase 3 (May 21): 17 tools  — AI-driven orchestrator with SSE streaming
+ Gemma 4 (May 22): 17 tools — Switched model, 128K context, thinking mode
+ Analytics (May 22): 29 tools — Added net worth, health score, tax, goals, EMI
+ Agents v1 (May 22): 32 tools — Stock analyst, portfolio doctor, tax advisor
+ Full suite (May 22): 46 tools — All 8 agents + SIP, spend, news, rebalancing tools
```
