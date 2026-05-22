# AI Orchestrator — Design & Implementation

> **Status:** Production | **Last updated:** 2026-05-22
> **Model:** Gemma 4 E4B (4.5B effective params, 128K context, native function calling + thinking)
> **Stack:** Spring Boot + llama.cpp + React SSE

---

## Table of Contents

1. [Vision](#1-vision)
2. [Architecture](#2-architecture)
3. [AI Configuration](#3-ai-configuration)
4. [Function Calling & Tool Loop](#4-function-calling--tool-loop)
5. [Token Streaming & Reasoning](#5-token-streaming--reasoning)
6. [SSE Protocol](#6-sse-protocol)
7. [MCP Tools](#7-mcp-tools)
8. [Human-in-the-Loop (HITL)](#8-human-in-the-loop-hitl)
9. [Session Management](#9-session-management)
10. [System Prompt & Intent Classification](#10-system-prompt--intent-classification)
11. [Frontend Implementation](#11-frontend-implementation)
12. [Security & Safety](#12-security--safety)
13. [File References](#13-file-references)
14. [Decision Log](#14-decision-log)

---

## 1. Vision

The AI chat is a **universal orchestrator** for the NetWorth Tracker app. Upload any financial document, ask about your portfolio, or request data changes — the AI:

1. **Understands** what you need (intent classification)
2. **Searches** your portfolio data via tools (holdings, accounts, cards)
3. **Extracts** structured data from documents (CC bills, salary slips, statements)
4. **Resolves** entities against your existing portfolio
5. **Proposes** actions and waits for approval (HITL)
6. **Executes** them after confirmation

Built in 3 phases: Java Graph Engine (Phase 1) -> MCP Tool Layer (Phase 2) -> AI-Driven Orchestrator (Phase 3, current).

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
     |  event: reasoning_delta      |   |<--- delta.reasoning_content   |
     |  event: reasoning_delta      |   |<--- delta.reasoning_content   |
     |<-----------------------------|   |       (token by token)        |
     |  (token by token)            |   |                               |
     |                              |   |<--- delta.tool_calls          |
     |  event: reasoning_done       |   |<--- finish_reason: tool_calls |
     |<-----------------------------|   |                               |
     |                              |   | McpToolClient.callTool()      |
     |  event: tool_start           |   |---> Execute MCP tool          |
     |<-----------------------------|   |                               |
     |  event: tool_end             |   |<--- Tool result               |
     |<-----------------------------|   |                               |
     |                              |   | (next round...)               |
     |  event: response             |   |                               |
     |<-----------------------------|   | Save session + traces         |
     |                              |                                   |

MCP Tool Classes (in-process):
  DocumentClassificationTools  (1 tool)
  DocumentExtractionTools      (6 tools)
  EntityResolutionTools        (4 tools)
  ExecutionTools               (6 tools)
```

### Key Components

| Component | File | Role |
|---|---|---|
| **AiConfig** | `config/AiConfig.java` | Centralized config (`@ConfigurationProperties(prefix="ai")`) |
| **AiClient** | `service/documentgraph/AiClient.java` | HTTP client to llama.cpp (streaming + non-streaming) |
| **McpAIChatService** | `service/documentgraph/mcp/McpAIChatService.java` | Orchestrator: tool loop, HITL, sessions, SSE |
| **McpToolClient** | `service/documentgraph/mcp/McpToolClient.java` | Tool registry + execution via `MethodToolCallbackProvider` |
| **AIChatController** | `controller/AIChatController.java` | REST endpoints (10 endpoints) |
| **AIChat.jsx** | `web/src/pages/AIChat.jsx` | Full-page chat UI |
| **FloatingChat.jsx** | `web/src/components/FloatingChat.jsx` | Floating widget chat UI |

---

## 3. AI Configuration

All settings are centralized in `AiConfig.java` and configurable via `application.properties` or environment variables:

```properties
# application.properties
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

| Property | Default | Description |
|---|---|---|
| `ai.server-url` | `http://localhost:8082/v1/chat/completions` | LLM server endpoint |
| `ai.model` | `llama` | Model name sent in requests |
| `ai.temperature` | `0.1` | LLM temperature (0=deterministic, 1=creative) |
| `ai.connect-timeout-ms` | `120000` | HTTP connect timeout |
| `ai.read-timeout-ms` | `120000` | HTTP read timeout |
| `ai.max-tool-rounds` | `3` | Max tool-calling loop iterations |
| `ai.tool-result-max-chars` | `3000` | Truncation limit for tool results sent to LLM |
| `ai.max-session-messages` | `50` | Message pairs before session trimming |
| `ai.pending-action-expiry-days` | `7` | HITL action expiration |
| `ai.sse-timeout-ms` | `180000` | SSE emitter timeout (3 minutes) |
| `ai.stream-tokens` | `true` | Enable real-time token streaming from LLM |

### Model: Gemma 4 E4B

| Property | Value |
|---|---|
| Parameters | 4.5B effective (8B total with embeddings) |
| GGUF size | ~5 GB (Q4_K_M quantization) |
| Context window | 128K tokens native |
| Function calling | Native support |
| Thinking mode | Built-in chain-of-thought (`reasoning_content`) |
| Multimodal | Text + Image + Audio |
| License | Apache 2.0 |

**llama-server command:**
```bash
llama-server \
  -m models/gemma-4-E4B-it-Q4_K_M.gguf \
  --port 8082 \
  --ctx-size 32768 \
  --n-gpu-layers 99 \
  --threads 8 \
  --no-mmap
```

---

## 4. Function Calling & Tool Loop

### Protocol

`AiClient` sends OpenAI-compatible requests to llama.cpp:

```json
{
  "model": "llama",
  "temperature": 0.1,
  "stream": true,
  "messages": [
    { "role": "system", "content": "SYSTEM_PROMPT" },
    { "role": "user", "content": "..." },
    { "role": "assistant", "tool_calls": [...] },
    { "role": "tool", "tool_call_id": "call_xxx", "content": "{...}" }
  ],
  "tools": [ ...17 TOOL_DEFINITIONS... ],
  "tool_choice": "auto"
}
```

### Tool Loop (McpAIChatService.streamChat)

```
1. Build fullMessages = [system_prompt] + [session_history] + [user_message]
2. Pre-filter: isNonFinancialQuery(message, hasHistory)
   - Greetings without history -> skip tools, respond directly
   - Confirmations WITH history ("yes", "ok") -> keep tools for follow-up
3. Loop (up to ai.max-tool-rounds):
   a. SSE: thought event
   b. Call AiClient (streaming or non-streaming based on ai.stream-tokens)
   c. Forward reasoning tokens as reasoning_delta SSE events
   d. If no tool_calls -> SSE: response event, save session, done
   e. For each tool_call:
      - SSE: tool_start
      - If execution tool -> create AiPendingAction (HITL)
      - If read-only tool -> execute via McpToolClient
      - SSE: tool_end
      - Append tool result to fullMessages
4. If loop exhausted -> summarize(fullMessages) -> SSE: response
5. Save session + tool traces to DB
```

---

## 5. Token Streaming & Reasoning

Gemma 4 supports chain-of-thought reasoning via `reasoning_content`. When `ai.stream-tokens=true`:

### Backend Flow

`AiClient.chatWithToolsStreaming()` opens an SSE connection to llama.cpp (`stream: true`) and parses chunks:

```java
// Each token callback:
callback.onReasoningToken(token)  // -> sendEvent(emitter, "reasoning_delta", {token, round})
callback.onContentToken(token)    // -> (reserved for future content streaming)
callback.onComplete(finishReason) // -> sendEvent(emitter, "reasoning_done", {round})
```

After the stream completes, the method assembles the full `ChatWithToolsResponse` (content + tool_calls + reasoning) for the tool loop to process.

### Frontend Flow

The SSE parser accumulates reasoning tokens:

```
reasoning_delta {token: "The", round: 1}    -> append to round 1 text
reasoning_delta {token: " user", round: 1}  -> append
reasoning_delta {token: " is", round: 1}    -> append
...hundreds of tokens...
reasoning_done  {round: 1}                  -> mark round complete
```

**Live UI (during streaming):**
- Purple card per reasoning round with text growing token-by-token
- Blinking cursor (`animate-pulse` purple bar) at the end of streaming text
- Spinner next to "Round N -- thinking..." while streaming
- Brain icon replaces spinner when round completes

**After response (collapsed):**
- `ThinkingSection` component renders collapsed by default
- Shows: "Thinking . N tools used . N steps"
- Click to expand: reasoning blocks + tool call cards with full JSON results

### Fallback

When `ai.stream-tokens=false`, the backend uses `chatWithToolsFull()` (non-streaming) and sends the full reasoning as a single `reasoning` SSE event.

---

## 6. SSE Protocol

Events sent via `SseEmitter` (timeout: `ai.sse-timeout-ms`):

| Event | Data | When |
|---|---|---|
| `thought` | `{ content: "Thinking...", round: N }` | Before each AI call |
| `reasoning_delta` | `{ token: "The", round: N }` | Each reasoning token (streaming mode) |
| `reasoning_done` | `{ round: N }` | Reasoning complete for a round |
| `reasoning` | `{ content: "full text", round: N }` | Full reasoning block (non-streaming fallback) |
| `tool_start` | `{ name: "search_holdings", arguments: {...} }` | Before tool execution |
| `tool_end` | `{ name: "...", result: {...}, durationMs: N, type: "executed"\|"pending_approval" }` | After tool execution |
| `response` | `{ content: "...", sessionId: "uuid", pendingActionId?: "uuid" }` | Final response |
| `error` | `{ message: "..." }` | On failure |

`sendEvent()` returns `boolean` -- `false` means client disconnected, and the tool loop aborts immediately.

### TCP Chunk Handling (Frontend)

SSE JSON payloads can split across TCP chunks. Both chat UIs use a `pendingData` accumulator:

```javascript
pendingData += line.slice(5)  // append data line
try {
  const d = JSON.parse(pendingData)
  pendingData = ''  // success -- reset
  // process event...
} catch {
  // incomplete JSON -- keep pendingData for next chunk
}
```

---

## 7. MCP Tools

17 tools across 4 classes, registered via `MethodToolCallbackProvider`:

### Document Tools

| Tool | Class | Status | Description |
|---|---|---|---|
| `classify_document` | DocumentClassificationTools | Working | AI-powered document type classification |
| `extract_credit_card_bill` | DocumentExtractionTools | Working | Structured CC bill extraction |
| `extract_salary_slip` | DocumentExtractionTools | Working | Salary slip data extraction |
| `extract_bank_statement` | DocumentExtractionTools | Working | Bank statement extraction (generic prompt) |
| `extract_cas` | DocumentExtractionTools | Stub | Returns metadata only |
| `extract_form16` | DocumentExtractionTools | Working | Form 16 extraction (generic prompt) |
| `extract_generic` | DocumentExtractionTools | Working | Fallback financial extraction |

### Search Tools (Read-Only)

| Tool | Class | Status | Description |
|---|---|---|---|
| `search_holdings` | EntityResolutionTools | Working | Search by symbol/name, returns qty+value+avgPrice |
| `search_accounts` | EntityResolutionTools | Working | Search bank accounts (filters soft-deleted) |
| `search_credit_cards` | EntityResolutionTools | Working | Search by issuer or last 4 digits |
| `resolve_entity` | EntityResolutionTools | Working | Match extracted entities to portfolio |

### Execution Tools (HITL-Protected)

| Tool | Class | Status | Description |
|---|---|---|---|
| `create_transaction` | ExecutionTools | Working | Create investment transaction via TransactionService |
| `update_cc_spend` | ExecutionTools | Working | Save CC spend report via SpendAnalyticsService |
| `update_salary` | ExecutionTools | Working | Create salary entry via SalaryService |
| `update_account_balance` | ExecutionTools | Working | Update bank balance via BankAccountService |
| `update_holding` | ExecutionTools | Stub | Returns not_implemented |
| `link_document` | ExecutionTools | Stub | Returns not_implemented |

### userId Injection

`McpAIChatService` auto-injects `userId` into tool arguments via `arguments.putIfAbsent("userId", userId)` for all tools except `link_document`. The LLM never needs to know or guess user IDs.

---

## 8. Human-in-the-Loop (HITL)

All execution tools are intercepted before execution:

```
1. AI calls execution tool (e.g., create_transaction)
2. McpAIChatService creates AiPendingAction record:
   - status: "pending"
   - expiresAt: now + ai.pending-action-expiry-days
   - toolName, arguments, summary stored
3. Returns pending_approval status to AI (not executed)
4. Frontend shows PendingActionCard with Approve/Reject buttons
5. User clicks Approve:
   POST /ai/actions/:id/confirm
   -> Checks expiration (rejects if expired)
   -> Executes tool via McpToolClient
   -> Handles tool failure (status -> "failed")
   -> On success: status -> "executed", result stored
6. User clicks Reject:
   POST /ai/actions/:id/reject -> status: "rejected"
```

### Pending Action States

`pending` -> `approved` -> `executed` (success)
`pending` -> `approved` -> `failed` (tool error)
`pending` -> `rejected`
`pending` -> `expired` (checked on confirm attempt)

Orphan pending actions are deleted when their parent session is deleted.

---

## 9. Session Management

### Storage

`ai_chat_sessions` table (PostgreSQL):

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id` | UUID | Owner (indexed) |
| `mode` | String | `"advice"` or `"import"` |
| `title` | String | Auto-set from first message |
| `messages` | JSONB | `[{role, content}]` array |
| `tool_call_traces` | JSONB | `[[{tool, arguments, result, durationMs, type}]]` |
| `created_at` | Timestamp | Auto |
| `updated_at` | Timestamp | Auto |

### Trimming

When messages exceed `ai.max-session-messages * 2` (default: 100 entries), oldest messages are removed. Tool traces are cleared on trim.

### Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/ai/sessions` | List user's sessions |
| GET | `/ai/sessions/:id` | Get session with messages + traces |
| DELETE | `/ai/sessions/:id` | Delete session + orphan pending actions |

---

## 10. System Prompt & Intent Classification

### Pre-Filter: `isNonFinancialQuery(message, hasSessionHistory)`

Skips tool calling for greetings and non-financial queries:

- **With no session history:** "hi", "hello", messages < 6 chars, short messages without financial keywords -> bypass tools
- **With session history:** Short confirmations ("yes", "ok", "sure", < 15 chars) are NOT filtered, so the AI gets context + tools for follow-ups
- **Financial keywords:** 80+ keywords checked (portfolio, stock, bill, salary, performance, etc.)

### System Prompt

The prompt uses 6 intents with a critical rule at the top:

```
ALWAYS SEARCH BEFORE ANSWERING
When user mentions "my portfolio/holdings/stocks/accounts/investments":
  -> MUST call search tools FIRST
  -> NEVER ask user to provide data you can look up
  -> Use broad queries: search_holdings(query="", limit=50)
```

Intent categories: GREETING, PORTFOLIO QUERY, DOCUMENT PROCESSING, RECORD/UPDATE, FINANCIAL ADVICE, UNKNOWN.

---

## 11. Frontend Implementation

### Components

| Component | File | Description |
|---|---|---|
| **AIChat** | `web/src/pages/AIChat.jsx` | Full-page chat with session sidebar |
| **FloatingChat** | `web/src/components/FloatingChat.jsx` | Floating widget overlay |
| **ThinkingSection** | (in AIChat.jsx) | Collapsible reasoning + tool steps |
| **LiveToolCall** | (in both) | Live tool card during streaming |
| **ToolCallCard** | (in both) | Completed tool card (expandable, full JSON) |
| **PendingActionCard** | (in both) | HITL approve/reject card |
| **TransactionCard** | (in both) | Rich CC bill transaction display |

### SSE Implementation Details

- `fetch()` + `ReadableStream` reader (not EventSource, for POST support)
- `pendingData` accumulator handles JSON split across TCP chunks
- Functional `setMessages(prev => ...)` prevents stale closure bugs
- `nextId()` counter for stable React keys (no `Date.now()` collisions)
- IME composition guard prevents premature send on Enter
- **Immediate rendering** on `response`/`error` events (no waiting for stream close)
- Fallback to `POST /ai/chat-v2` on SSE failure

### Live Streaming UI

During AI processing, the user sees:
1. **Reasoning text** streaming token-by-token in a purple card with a blinking cursor
2. **Tool call cards** appearing as tools are invoked, with live spinners
3. **Transaction cards** rendering from tool results (e.g., CC bill breakdown)

After the response arrives:
1. Live section disappears
2. Bot message renders with markdown
3. **Collapsed ThinkingSection** below: "Thinking . N tools . N steps" -- click to expand
4. **PendingActionCard** if HITL action was created

---

## 12. Security & Safety

### Ownership

Every tool that reads or writes data verifies `userId`. The `userId` is injected by `McpAIChatService`, never provided by the LLM.

### HITL Guard

Execution tools are never called directly. The LLM proposes actions, the backend creates pending records, and the user must explicitly approve.

### Expiration

Pending actions expire after `ai.pending-action-expiry-days` (default: 7). Confirmation after expiry is rejected.

### Error Isolation

Tool execution failures during HITL confirmation transition the action to `failed` status with the error stored. The user is notified.

### Connection Management

- `HttpURLConnection.disconnect()` in `finally` blocks prevents socket leaks
- `sendEvent()` returns false on client disconnect -> tool loop aborts immediately
- SSE emitter has configurable timeout (`ai.sse-timeout-ms`)

---

## 13. File References

### Backend

| File | Purpose |
|---|---|
| `config/AiConfig.java` | Centralized AI configuration |
| `service/documentgraph/AiClient.java` | llama.cpp HTTP client (streaming + non-streaming) |
| `service/documentgraph/mcp/McpAIChatService.java` | AI orchestrator (tool loop, HITL, sessions, SSE) |
| `service/documentgraph/mcp/McpToolClient.java` | Tool registry + execution |
| `service/documentgraph/mcp/tools/DocumentClassificationTools.java` | classify_document |
| `service/documentgraph/mcp/tools/DocumentExtractionTools.java` | 6 extraction tools |
| `service/documentgraph/mcp/tools/EntityResolutionTools.java` | 4 search/resolve tools |
| `service/documentgraph/mcp/tools/ExecutionTools.java` | 6 execution tools (3 stubs) |
| `controller/AIChatController.java` | 10 REST endpoints |
| `model/entity/AiChatSession.java` | Session entity (JSONB) |
| `model/entity/AiPendingAction.java` | HITL pending action entity |
| `repository/AiChatSessionRepository.java` | Session queries |
| `repository/AiPendingActionRepository.java` | Pending action queries |

### Frontend

| File | Purpose |
|---|---|
| `web/src/pages/AIChat.jsx` | Full-page AI chat |
| `web/src/components/FloatingChat.jsx` | Floating widget chat |
| `web/src/api/client.js` | Axios client (baseURL `/api/v1`) |

---

## 14. Decision Log

| Date | Decision | Rationale |
|---|---|---|
| 2026-05-21 | Three-phase approach (Graph -> MCP -> AI Agent) | Fastest path to working feature; incremental improvements |
| 2026-05-21 | MCP tools in same JVM | No network overhead; transactional safety preserved |
| 2026-05-21 | Function calling via llama.cpp | Native support; no separate Python agent needed |
| 2026-05-21 | Qwen 2.5-7B as initial model | Best function calling at 7B size |
| 2026-05-22 | Switched to Gemma 4 E4B | 128K context, native function calling + thinking, better benchmarks |
| 2026-05-22 | Real token streaming for reasoning | Users see AI thinking in real-time like coding agents |
| 2026-05-22 | Centralized AiConfig via @ConfigurationProperties | All AI settings configurable without recompiling |
| 2026-05-22 | Immediate rendering on response event | Don't wait for SSE stream close; eliminates "stuck thinking" bug |
| 2026-05-22 | isNonFinancialQuery respects session history | "yes" after a proposal is a follow-up, not a greeting |
| 2026-05-22 | Tool result truncation at 3000 chars (configurable) | 800 was too aggressive; 3000 fits all 15 holdings in context |
| 2026-05-22 | sendEvent returns boolean for disconnect detection | Prevents wasting AI tokens when client is gone |
