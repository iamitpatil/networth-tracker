# Document Processing Graph — Design Document

> **Status:** Draft | **Last updated:** 2026-05-21
> **Goal:** An AI-powered document processing graph that can parse any financial document and orchestrate any operation in the product — built in 3 phases.

---

## Table of Contents

1. [Vision](#1-vision)
2. [Phase 1 — Java Graph Engine](#2-phase-1--java-graph-engine)
3. [Phase 2 — MCP Tool Layer](#3-phase-2--mcp-tool-layer)
4. [Phase 3 — AI-Driven Orchestrator](#4-phase-3--ai-driven-orchestrator)
5. [Security & Transactional Safety](#5-security--transactional-safety)
6. [Appendix](#6-appendix)

---

## 1. Vision

The AI chat becomes a **universal orchestrator** — upload any document (or just describe an intent in natural language) and the system:

1. **Understands** what it's looking at (classification)
2. **Extracts** structured financial data (transactions, entities, metadata)
3. **Resolves** entities against existing portfolio (holdings, accounts, cards)
4. **Decides** what actions to take (create transactions, update balances, link documents)
5. **Executes** them — or asks for confirmation first

Long-term, this replaces every siloed parser, import flow, and manual data entry form with a single AI-powered graph that the model itself can navigate.

---

## 2. Phase 1 — Java Graph Engine

**Goal:** Build the core graph engine with hardcoded node ordering. Ships fast, works reliably. The AI is used as a text processor (classification + extraction), not an agent.

### 2.1 Package Structure

```
com.networth.service.documentgraph/
├── DocumentProcessingOrchestrator.java    # Graph runner
├── GraphState.java                        # Mutable state object
├── ProcessingNode.java                    # Node interface
├── DocumentType.java                      # Enum
├── ActionType.java                        # Enum
├── Confidence.java                        # Enum
├── ProposedAction.java                    # Proposed action DTO
├── ExecutedAction.java                    # Executed action result
├── MatchedEntity.java                     # Entity match DTO
├── DocumentProcessingConfig.java          # Spring @Configuration
├── DocumentProcessingController.java      # REST endpoints
├── AiClient.java                          # Shared llama.cpp HTTP client
└── nodes/
    ├── PdfTextExtractionNode.java
    ├── ImageOcrNode.java                  # Stub for Phase 3
    ├── ClassificationNode.java
    ├── ExtractionNodeFactory.java
    ├── CreditCardExtractionNode.java
    ├── SalaryExtractionNode.java
    ├── BankStatementExtractionNode.java
    ├── CasExtractionNode.java
    ├── Form16ExtractionNode.java
    ├── GenericExtractionNode.java
    ├── EntityResolutionNode.java
    ├── RoutingNode.java
    └── ExecutionNode.java
```

### 2.2 GraphState

```java
@Data
@Builder
public class GraphState {
    // Identity
    private UUID userId;
    private UUID documentId;
    private UUID correlationId;

    // Input
    private String originalFilename;
    private String contentType;
    private byte[] fileContent;
    private String password;                     // For encrypted PDFs
    private boolean autoExecute;                 // Skip human review?

    // Processing
    private String rawText;
    private DocumentType classifiedAs;
    private double classificationConfidence;
    private String classificationReasoning;
    private Map<String, Object> extractedData;

    // Entity Resolution
    private List<MatchedEntity> matchedEntities;

    // Routing & Execution
    private List<ProposedAction> proposedActions;
    private List<ExecutedAction> executedActions;

    // Flow control
    private String currentNodeName;
    private boolean completed;
    private String error;
    private boolean requiresHumanReview;
}
```

### 2.3 Node Interface

```java
public interface ProcessingNode {
    String getName();
    void process(GraphState state);
}
```

### 2.4 Graph Flow

```
START ──► PDF_TEXT_EXTRACT ──► CLASSIFY ──► EXTRACT ──► RESOLVE ──► ROUTE ──► EXECUTE ──► RETURN
                                │              │
                                ▼              ▼
                          [if image]      [or use OCR node]
                          IMAGE_OCR

Conditional edges from EXTRACT:
  CREDIT_CARD_BILL → CreditCardExtractionNode
  SALARY_SLIP      → SalaryExtractionNode
  BANK_STATEMENT   → BankStatementExtractionNode
  CAS              → CasExtractionNode
  FORM_16          → Form16ExtractionNode
  GENERIC/UNKNOWN  → GenericExtractionNode (AI fallback)
  INVOICE/BROKER_CSV → GenericExtractionNode

Conditional edges from ROUTE:
  If LOW confidence on any proposed action → PAUSE_FOR_REVIEW
  If all HIGH confidence → AUTO_EXECUTE (only if autoExecute=true)
```

### 2.5 Node Implementations

#### 2.5.1 PdfTextExtractionNode

- Uses Apache PDFBox to extract text from `state.fileContent`
- Handles encrypted PDFs with optional `state.password`
- Catches `PasswordRequiredException` → sets `state.requiresHumanReview = true`
- Sets `state.rawText` on success, `state.error` on failure

#### 2.5.2 ClassificationNode (AI)

**System prompt:**
```
You are a financial document classifier. Given the text extracted from a document,
determine which category it belongs to.

Key heuristics by type:
- CREDIT_CARD_BILL: Contains "card" + "due date" + "minimum amount" + transaction table
- SALARY_SLIP: Contains "employee name" + "PAN" + "gross pay" + "net pay" + deductions
- BANK_STATEMENT: Contains bank name + account number + opening/closing balance + transactions
- CAS: Contains "Consolidated Account Statement" + multiple folios/holdings
- FORM_16: Contains "Form 16" + "TDS" + "PAN" + assessment year + salary breakdown
- BROKER_CSV: CSV with headers like Symbol, Quantity, Price
- INVOICE: Contains "invoice" + "bill to" + line items + total

Respond ONLY with a JSON object:
{
  "documentType": "CREDIT_CARD_BILL|SALARY_SLIP|BANK_STATEMENT|CAS|FORM_16|BROKER_CSV|INVOICE|GENERIC_FINANCIAL|UNKNOWN",
  "confidence": 0.0-1.0,
  "reasoning": "Brief explanation"
}
```

**DocumentType enum:**
```java
public enum DocumentType {
    CREDIT_CARD_BILL,
    SALARY_SLIP,
    BANK_STATEMENT,
    CAS,
    FORM_16,
    BROKER_CSV,
    INVOICE,
    GENERIC_FINANCIAL,
    UNKNOWN
}
```

#### 2.5.3 Extraction Nodes

| Node | DocumentType | Delegates To |
|---|---|---|
| `CreditCardExtractionNode` | `CREDIT_CARD_BILL` | Existing `CreditCardBillParser` |
| `SalaryExtractionNode` | `SALARY_SLIP` | Existing `SalarySlipParser` |
| `BankStatementExtractionNode` | `BANK_STATEMENT` | Existing `PDFStatementParser.parseBankStatement()` |
| `CasExtractionNode` | `CAS` | Existing `PDFStatementParser.parseCAS()` |
| `Form16ExtractionNode` | `FORM_16` | Existing `Form16Parser` |
| `GenericExtractionNode` | `GENERIC_FINANCIAL`, `INVOICE`, `BROKER_CSV` | AI prompt (see below) |

**ExtractionNodeFactory:**
```java
@Component
public class ExtractionNodeFactory {
    private final Map<DocumentType, ProcessingNode> extractionNodes;

    public ProcessingNode nodeFor(DocumentType type) {
        return extractionNodes.getOrDefault(type, genericExtractionNode);
    }
}
```

**GenericExtractionNode AI prompt:**
```
You are a financial data extractor. Extract all financial information from this document text.
Look for transactions, account details, card details, employer details, investment details.

Return a JSON object:
{
  "documentType": "best guess of document type",
  "entities": [
    { "type": "HOLDING|ACCOUNT|CARD|EMPLOYER|OTHER", "name": "...", "identifier": "..." }
  ],
  "transactions": [
    { "date": "YYYY-MM-DD", "description": "...", "amount": 0, "type": "CREDIT|DEBIT", "category": "..." }
  ],
  "financialSummary": {
    "totalCredits": 0, "totalDebits": 0,
    "startBalance": null, "endBalance": null
  },
  "notes": "Any other relevant information found"
}
```

#### 2.5.4 EntityResolutionNode

Matches extracted entities against user's existing data:

| Extracted Hint | Repository | Match Criteria |
|---|---|---|
| ISIN | `HoldingRepository.findByUserAndIsin()` | Exact match |
| Symbol (e.g., TCS) | `HoldingRepository.findByUserAndSymbol()` | Case-insensitive contains |
| Card Issuer + Last 4 | `CreditCardRepository.findByUser()` | Issuer contains + last4 match |
| Bank Name + Acct Last 4 | `BankAccountRepository.findByUser()` | Bank name contains + acct ends with |
| Employer Name | `SalaryRepository.findByUser()` | Employer name contains |
| PAN | `UserRepository` | Matches user's PAN |
| Broker Name | `DematAccountRepository.findByUser()` | Broker name contains |

Unmatched → `matchConfidence = 0.0`, `entityId = null` — flagged for manual linking.

**MatchedEntity:**
```java
@Data
@Builder
public class MatchedEntity {
    private String entityType;        // HOLDING, CREDIT_CARD, BANK_ACCOUNT, SALARY, DEMAT_ACCOUNT, USER
    private UUID entityId;
    private String entityLabel;       // Display name
    private double matchConfidence;   // 0.0 - 1.0
}
```

#### 2.5.5 RoutingNode

Produces `ProposedAction` list based on extracted data + matched entities:

| Document Type | Actions Proposed |
|---|---|
| CREDIT_CARD_BILL | `UPDATE_CC_SPEND` (upsert by userId + cardLastFour + statementMonth) |
| SALARY_SLIP | `UPDATE_SALARY` (match by employer + pay date proximity) |
| BANK_STATEMENT | `UPDATE_ACCOUNT_BALANCE` + `CREATE_TRANSACTION` per entry |
| CAS | `UPDATE_HOLDING` (current value) + `CREATE_TRANSACTION` per buy/sell |
| FORM_16 | `UPDATE_USER_TAX_INFO` (income + deductions) |
| INVOICE / GENERIC | `CREATE_TRANSACTION` if recognizable + matched account |

**Confidence rules:**
- **HIGH**: Entity matched exactly + data well-structured + familiar format
- **MEDIUM**: Entity fuzzy-matched or data has minor gaps
- **LOW**: No entity match or unexpected data format

**Human review trigger:** `state.requiresHumanReview = proposedActions.stream().anyMatch(a -> a.getConfidence() == LOW)`

**ProposedAction:**
```java
@Data
@Builder
public class ProposedAction {
    private ActionType type;
    private String entityType;
    private UUID entityId;
    private Map<String, Object> data;
    private Confidence confidence;
}

public enum ActionType {
    CREATE_TRANSACTION,
    UPDATE_HOLDING,
    UPDATE_CC_SPEND,
    UPDATE_SALARY,
    UPDATE_ACCOUNT_BALANCE,
    UPDATE_LIABILITY,
    CREATE_ACCOUNT,
    LINK_DOCUMENT,
    UPDATE_USER_TAX_INFO,
    NOTHING
}

public enum Confidence { HIGH, MEDIUM, LOW }
```

#### 2.5.6 ExecutionNode

| ActionType | Service Method |
|---|---|
| CREATE_TRANSACTION | `TransactionService.addTransaction(userId, request)` |
| UPDATE_HOLDING | `PortfolioController.updateHolding(...)` or direct service |
| UPDATE_CC_SPEND | `SpendAnalyticsService.saveReport(userId, parsedBill)` |
| UPDATE_SALARY | `SalaryService.createSalary(userId, slipData)` |
| UPDATE_ACCOUNT_BALANCE | `BankAccountService` / `AccountsHubService` |
| LINK_DOCUMENT | `DocumentService.linkDocumentTo...(docId, entityId)` |

**ExecutedAction:**
```java
@Data
@Builder
public class ExecutedAction {
    private ProposedAction action;
    private boolean success;
    private Object result;
    private String errorMessage;
}
```

### 2.6 Orchestrator Logic

```java
@Component
public class DocumentProcessingOrchestrator {

    private final Map<String, ProcessingNode> nodeMap;

    public GraphState execute(GraphState state) {
        run("pdfTextExtract", state);
        if (state.getError() != null) return state;

        run("classify", state);
        if (state.getClassifiedAs() == DocumentType.UNKNOWN) {
            state.setCompleted(true);
            return state;
        }

        String extractionNode = extractionNodeFor(state.getClassifiedAs());
        run(extractionNode, state);
        if (state.getError() != null) return state;

        run("entityResolve", state);
        run("route", state);

        if (state.isAutoExecute() && !state.isRequiresHumanReview()) {
            run("execute", state);
        }

        state.setCompleted(true);
        return state;
    }

    private void run(String nodeName, GraphState state) {
        state.setCurrentNodeName(nodeName);
        nodeMap.get(nodeName).process(state);
    }

    private String extractionNodeFor(DocumentType type) {
        return switch (type) {
            case CREDIT_CARD_BILL -> "extractCCBill";
            case SALARY_SLIP      -> "extractSalary";
            case BANK_STATEMENT   -> "extractBankStmt";
            case CAS              -> "extractCas";
            case FORM_16          -> "extractForm16";
            default               -> "extractGeneric";
        };
    }
}
```

### 2.7 REST API

#### POST /api/v1/documents/process

| Field | Type | Required | Description |
|---|---|---|---|
| file | File | Yes | Document to process |
| password | String | No | For encrypted PDFs |
| context | String (JSON) | No | Hints like `{"expectedType":"SALARY_SLIP"}` |
| autoExecute | Boolean | No | Default false |

**Response (202 Accepted):**
```json
{
  "correlationId": "uuid",
  "status": "PROCESSING"
}
```

#### GET /api/v1/documents/process/{correlationId}/status

**Response (200):**
```json
{
  "correlationId": "uuid",
  "status": "PROCESSING|COMPLETED|AWAITING_REVIEW|ERROR",
  "currentNode": "classify",
  "result": { ... }
}
```

#### POST /api/v1/documents/process/{correlationId}/confirm

**Request:**
```json
{
  "actions": ["action-index-0", "action-index-2"],
  "overrides": {
    "action-index-0": { "entityId": "different-uuid" }
  }
}
```

**Response (200):**
```json
{
  "correlationId": "uuid",
  "status": "COMPLETED",
  "result": { ... }
}
```

### 2.8 Frontend: SmartImport.jsx

New page at route `/smart-import`:

- Drag-and-drop zone → upload → polls status
- Visual graph display with step-by-step progress:
  ```
  📄 Extracting text...        ✅ Done
  🔍 Classifying document...   ✅ Credit Card Bill
  📊 Extracting data...        ⏳ Processing
  🔗 Matching entities...      ⏳ Pending
  🎯 Routing actions...        ⏳ Pending
  ```
- On completion with `requiresHumanReview`:
  - Summary card: document type + confidence badge
  - Matched entities section (clickable links, unmatched get dropdown search)
  - Proposed actions table with toggle per action + manual entity override
  - Confirm button → `POST /confirm`
- On auto-execute: green success summary with executed actions

### 2.9 Phase 1 Deliverables

| Item | Description |
|---|---|
| Backend | All nodes + orchestrator + controller (~15 Java files) |
| Frontend | `SmartImport.jsx` with drag-drop + poll + review UI |
| Integration | Reuse existing parsers, services, AI client |
| Tests | Unit tests per node + integration test for full graph |
| Compile | `mvn compile` passes |

---

## 3. Phase 2 — MCP Tool Layer

**Goal:** Extract all node capabilities into self-describing MCP tools within the same Spring Boot process. The Java orchestrator can call tools via MCP client, and tools can be independently discovered and invoked.

### 3.1 Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                   Spring Boot (port 8080)                        │
│                                                                  │
│  ┌──────────────────────┐      ┌──────────────────────────────┐ │
│  │ DocumentProcessing   │      │  MCP Server                  │ │
│  │ Orchestrator         │──────│  (spring-ai-mcp)             │ │
│  │ (Java graph runner)  │ MCP  │                              │ │
│  └──────────────────────┘ call  │  /mcp/tools/list             │ │
│                                 │  /mcp/tools/call             │ │
│                                 └──────────────────────────────┘ │
│                                           │                      │
│                                           ▼                      │
│                                 ┌──────────────────────────────┐ │
│                                 │  @Tool Beans                 │ │
│                                 │                              │ │
│                                 │  ● classify_document         │ │
│                                 │  ● extract_credit_card_bill  │ │
│                                 │  ● extract_salary_slip       │ │
│                                 │  ● extract_bank_statement    │ │
│                                 │  ● extract_cas               │ │
│                                 │  ● extract_form16            │ │
│                                 │  ● extract_generic           │ │
│                                 │  ● resolve_entity            │ │
│                                 │  ● create_transaction        │ │
│                                 │  ● update_cc_spend           │ │
│                                 │  ● update_holding            │ │
│                                 │  ● update_salary             │ │
│                                 │  ● update_account_balance    │ │
│                                 │  ● link_document             │ │
│                                 │  ● search_holdings           │ │
│                                 │  ● search_accounts           │ │
│                                 │  ● search_credit_cards       │ │
│                                 └──────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 MCP Dependency

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>
```

### 3.3 Tool Definitions

Each tool is a Spring `@Service` method annotated with `@Tool`:

```java
@Component
public class DocumentClassificationTools {

    @Tool(name = "classify_document",
          description = "Classify a financial document by its text content")
    public DocumentClassificationResult classifyDocument(
            @ToolParam(description = "Extracted text from the document") String text) {
        // AI call → return DocumentType + confidence
    }
}
```

**Full tool list:**

| Tool Name | Input | Output |
|---|---|---|
| `classify_document` | `text: string` | `{ documentType, confidence, reasoning }` |
| `extract_credit_card_bill` | `text: string` | Structured CC bill data |
| `extract_salary_slip` | `text: string` | Structured salary data |
| `extract_bank_statement` | `text: string` | Transaction list + balances |
| `extract_cas` | `text: string` | Holdings + transactions |
| `extract_form16` | `text: string` | Income + deductions |
| `extract_generic` | `text: string` | Best-effort extraction |
| `resolve_entity` | `{ entityType, hints }` | `{ matchedEntities: [] }` |
| `create_transaction` | `{ holdingId, qty, price, date, type }` | `{ transactionId }` |
| `update_cc_spend` | `{ cardId, statementMonth, ... }` | `{ reportId }` |
| `update_holding` | `{ holdingId, data }` | `{ holdingId }` |
| `update_salary` | `{ salaryId, data }` | `{ salaryId }` |
| `update_account_balance` | `{ accountId, balance }` | `{ accountId }` |
| `link_document` | `{ documentId, entityType, entityId }` | `{ success }` |
| `search_holdings` | `{ query, limit }` | `[{ id, symbol, name, isin }]` |
| `search_accounts` | `{ query, accountType, limit }` | `[{ id, type, label }]` |
| `search_credit_cards` | `{ query, limit }` | `[{ id, issuer, lastFour }]` |

### 3.4 Orchestrator Uses MCP

The `DocumentProcessingOrchestrator` calls tools through the MCP client:

```java
@Component
public class McpDocumentProcessingOrchestrator {

    @Autowired
    private McpClient mcpClient;

    public GraphState execute(GraphState state) {
        // Extract text (direct call, no MCP needed)
        runDirect("pdfTextExtract", state);

        // Classify via MCP
        Map<String, Object> classifyResult = callMcp("classify_document",
            Map.of("text", state.getRawText()));
        state.setClassifiedAs(parseType(classifyResult));

        // Extract via MCP (select tool by type)
        String extractTool = extractionToolFor(state.getClassifiedAs());
        state.setExtractedData(callMcp(extractTool,
            Map.of("text", state.getRawText())));

        // Resolve via MCP
        state.setMatchedEntities(callMcp("resolve_entity",
            state.getExtractedData()));

        // Route (direct logic, same as Phase 1)
        runDirect("route", state);

        // Execute tool calls for each proposed action
        for (ProposedAction action : state.getProposedActions()) {
            try {
                Object result = callMcp(mcpToolFor(action), action.getData());
                // record success
            } catch (Exception e) {
                // record failure
            }
        }
    }
}
```

### 3.5 Frontend MCP Awareness

The frontend can call `GET /mcp/tools/list` to render available commands in the AI Chat sidebar as quick-action buttons:

```
Quick Actions:
  📄 Classify Document
  💳 Import CC Bill
  💰 Import Salary Slip
  📊 Import Bank Statement
  🔍 Search Holdings
```

### 3.6 Phase 2 Deliverables

| Item | Description |
|---|---|
| MCP Server | `spring-ai-mcp-server` auto-config + tool registration |
| @Tool beans | 17 tool definitions across tool classes |
| Orchestrator refactor | Uses MCP client to call tools instead of direct service calls |
| Frontend update | Tool list as quick-actions in AI Chat sidebar |
| Tests | Tool contract tests via MCP protocol |

---

## 4. Phase 3 — AI-Driven Orchestrator

**Goal:** The AI model itself drives the graph. Instead of a Java orchestrator deciding which node runs next, the AI receives the tool list + a system prompt describing the graph, and decides which tools to call and in what order. The Java layer becomes a thin executor that translates tool calls to service invocations.

### 4.1 Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                    Spring Boot (port 8080)                       │
│                                                                  │
│  ┌────────────────────┐      ┌──────────────────────────────┐   │
│  │ MCP Server         │      │  AI Chat Controller          │   │
│  │                    │      │  POST /ai/chat (with MCP)    │   │
│  │ tools/list         │◄─────│                              │   │
│  │ tools/call         │      │  System prompt describes     │   │
│  └─────────┬──────────┘      │  the graph + all tools       │   │
│            │                 └──────────┬───────────────────┘   │
│            │                            │                       │
│            │                            ▼                       │
│            │                 ┌──────────────────────┐           │
│            │                 │  MCP Client          │           │
│            │                 │  (handles tool       │           │
│            └─────────────────│   call routing)      │           │
│                              └──────────────────────┘           │
│                                        │                       │
│                                        ▼                       │
│                              ┌──────────────────────┐          │
│                              │  llama.cpp            │          │
│                              │  (port 8082)          │          │
│                              │  Function Calling     │          │
│                              └──────────────────────┘          │
└──────────────────────────────────────────────────────────────────┘
```

### 4.2 Key Change: Function Calling

llama.cpp supports OpenAI-compatible **function calling** / **tool use** mode. The chat endpoint sends:

```
POST /v1/chat/completions
{
  "model": "llama",
  "messages": [
    { "role": "system", "content": "SYSTEM_PROMPT" },
    { "role": "user", "content": "Here's my credit card bill PDF text..." }
  ],
  "tools": [ ...MCP_TOOL_DEFINITIONS... ],
  "tool_choice": "auto"
}
```

The model responds either with text or with a `tool_calls` array:

```json
{
  "choices": [{
    "finish_reason": "tool_calls",
    "message": {
      "role": "assistant",
      "tool_calls": [{
        "id": "call_xxx",
        "function": {
          "name": "classify_document",
          "arguments": "{ \"text\": \"...\" }"
        }
      }]
    }
  }]
}
```

The MCP client executes the tool, sends the result back as a `tool` role message, and the model decides the next step — until it produces a final text response.

### 4.3 System Prompt

```
You are the NetWorth Tracker AI — a financial document processing agent.
You have access to the following tools:

== DOCUMENT ANALYSIS ==
{classify_document, extract_credit_card_bill, extract_salary_slip,
 extract_bank_statement, extract_cas, extract_form16, extract_generic}

== ENTITY SEARCH ==
{search_holdings, search_accounts, search_credit_cards, resolve_entity}

== EXECUTION ==
{create_transaction, update_holding, update_cc_spend, update_salary,
 update_account_balance, link_document}

== GRAPH FLOW ==
Always follow this sequence:
1. CLASSIFY: Call classify_document(text) to identify the document type.
2. EXTRACT: Call the appropriate extraction tool based on the type.
   If type is UNKNOWN, call extract_generic(text) as fallback.
3. RESOLVE: Call search_* or resolve_entity to match extracted data
   against the user's existing portfolio.
4. ROUTE: Based on extracted data and matched entities, decide what actions
   to propose. Present them to the user and ask for confirmation.
5. EXECUTE: Once the user confirms, call the appropriate execution tools.

== IMPORTANT RULES ==
- Never execute actions without user confirmation for LOW or MEDIUM confidence.
- For HIGH confidence matches, you may suggest auto-execution.
- If a tool fails, explain the error and suggest next steps.
- Always explain what you found before asking to execute.
```

### 4.4 MCP Client Integration

```java
@Component
public class McpAIChatService {

    private final McpClient mcpClient;
    private final RestTemplate llamaRestTemplate;

    public ChatResponse chat(String userMessage, List<Message> history) {
        // 1. Get all tool definitions from MCP server
        List<ToolDefinition> tools = mcpClient.listTools();

        // 2. Build request with tools list (function calling mode)
        LlamaRequest request = LlamaRequest.builder()
            .model("llama")
            .messages(buildMessages(userMessage, history))
            .tools(tools)
            .toolChoice("auto")
            .temperature(0.1)
            .build();

        // 3. Call llama.cpp
        LlamaResponse response = callLlama(request);

        // 4. Handle tool calls
        while (response.hasToolCalls()) {
            for (ToolCall toolCall : response.getToolCalls()) {
                Object result = mcpClient.callTool(
                    toolCall.getFunction().getName(),
                    toolCall.getFunction().getArguments());
                // Add result as tool message
                history.add(ToolMessage.builder()
                    .toolCallId(toolCall.getId())
                    .content(result)
                    .build());
            }
            // Call again with tool results
            request.setMessages(history);
            response = callLlama(request);
        }

        // 5. Return final text response
        return ChatResponse.builder()
            .message(response.getContent())
            .toolCallsTrace(buildToolTrace(toolCallsExecuted))
            .build();
    }
}
```

### 4.5 Graph Visualization in Frontend

The frontend can progress the graph visualization based on tool calls as they stream in:

```
User: "Here's my HDFC credit card bill for April"

[System processing...]

🔍 classify_document(text)
   → CREDIT_CARD_BILL (0.97)

📊 extract_credit_card_bill(text)
   → 14 transactions, ₹25,430 total due

🔗 search_credit_cards(query: "HDFC 1234")
   → HDFC Regalia (1234) confidence: 1.0

🤖 "I found an HDFC Regalia credit card bill for April 2026 with 14 transactions.
    Total due: ₹25,430. Categories: Food (₹5,200), Shopping (₹8,100)...
    Shall I import this as a spend report for your HDFC Regalia card?"
```

### 4.6 Phase 3 Deliverables

| Item | Description |
|---|---|
| Function calling support | llama.cpp config for `tool_choice: auto` |
| MCP client in AI chat | `McpAIChatService` that routes tool calls |
| System prompt | Graph flow definition as model instructions |
| Frontend streaming | Live tool call visualization in chat |
| Graceful fallback | If model doesn't support function calling → Phase 1 mode |

---

## 5. Security & Transactional Safety

### 5.1 Ownership Verification

Every tool that reads or writes data must verify `userId` matches. This is already enforced by existing services (`TransactionService.verifyHoldingOwnership()`, etc.) — the graph must pass `userId` through all tool calls.

### 5.2 Execution Guard

- Proposed actions are never auto-executed unless `autoExecute=true` AND all actions are HIGH confidence
- Execution is wrapped in `@Transactional` — if any action in a batch fails, the entire batch rolls back
- Each executed action has its own try/catch so partial success is reported per-action

### 5.3 Rate Limiting

AI calls (classification + extraction) go through the existing `RateLimitService` — maximum 10 AI calls per minute per user.

### 5.4 Audit Trail

All graph runs are logged:
- `correlationId`, `userId`, `documentType`, `numActionsProposed`, `numActionsExecuted`
- Tool call timing and results
- Any errors or overrides

---

## 6. Appendix

### 6.1 File References

| File | Purpose |
|---|---|
| `src/main/java/com/networth/service/documentgraph/DocumentProcessingOrchestrator.java` | Graph runner |
| `src/main/java/com/networth/service/documentgraph/GraphState.java` | State object |
| `src/main/java/com/networth/service/documentgraph/ProcessingNode.java` | Node interface |
| `src/main/java/com/networth/service/documentgraph/DocumentType.java` | Document type enum |
| `src/main/java/com/networth/service/documentgraph/ActionType.java` | Action type enum |
| `src/main/java/com/networth/service/documentgraph/Confidence.java` | Confidence enum |
| `src/main/java/com/networth/service/documentgraph/ProposedAction.java` | Proposed action |
| `src/main/java/com/networth/service/documentgraph/ExecutedAction.java` | Executed action |
| `src/main/java/com/networth/service/documentgraph/MatchedEntity.java` | Entity match |
| `src/main/java/com/networth/service/documentgraph/DocumentProcessingConfig.java` | Spring config |
| `src/main/java/com/networth/service/documentgraph/DocumentProcessingController.java` | REST API |
| `src/main/java/com/networth/service/documentgraph/AiClient.java` | llama.cpp HTTP client |
| `src/main/java/com/networth/service/documentgraph/nodes/*.java` | All node implementations |
| `web/src/pages/SmartImport.jsx` | Frontend UI |
| `src/main/java/com/networth/service/documentgraph/tools/*.java` | Phase 2 @Tool beans |
| `src/main/java/com/networth/service/McpAiChatService.java` | Phase 3 AI-driven chat |

### 6.2 Existing Code Reuse

| What to Reuse | Source | Used In |
|---|---|---|
| Apache PDFBox text extraction | `CreditCardBillParser`, `SalarySlipParser` | `PdfTextExtractionNode` |
| CC bill AI prompt | `CreditCardBillParser.PROMPT` | `CreditCardExtractionNode` |
| Salary slip AI prompt | `SalarySlipParser` | `SalaryExtractionNode` |
| CAS regex patterns | `PDFStatementParser` | `CasExtractionNode` |
| Bank statement regex | `PDFStatementParser` | `BankStatementExtractionNode` |
| Form 16 regex parser | `Form16Parser` | `Form16ExtractionNode` |
| llama.cpp HTTP client | `AIChatService` | `AiClient` utility |
| Transaction creation | `TransactionService.addTransaction()` | `ExecutionNode` |
| CC spend upsert | `SpendAnalyticsService.saveReport()` | `ExecutionNode` |
| All 6 account repositories | `AccountsHubService` | `EntityResolutionNode` |

### 6.3 Migration & Backward Compatibility

- Existing endpoints (`POST /liabilities/parse-cc-bill`, `POST /salaries/parse-slip`, etc.) remain unchanged
- The graph is additive — new capability, not a replacement
- Each extraction node falls back to direct AI extraction if the dedicated parser isn't available for a format variant

### 6.4 Future Enhancements

| Feature | Description |
|---|---|
| **Image OCR** | Support phone-captured document photos |
| **Email forwarding** | Accept `.eml` files, extract attachments + body |
| **Batch processing** | Upload multiple documents → process as batch graph |
| **Retry with correction** | User corrects entity matching and re-runs execution |
| **Scheduled auto-processing** | Gmail watch → auto-download attachments → auto-process |
| **Learning** | Store successful entity matches → improve future resolution |
| **Multi-language** | Support Hindi/regional language documents via AI |

### 6.5 Design Decision Log

| Date | Decision | Rationale |
|---|---|---|
| 2026-05-21 | Three-phase approach | Fastest path to working feature; MCP and AI-driven graph are incremental improvements |
| 2026-05-21 | MCP in same JVM (Phase 2) | Avoids network overhead of external MCP server; keeps transactional safety |
| 2026-05-21 | Function calling for Phase 3 | llama.cpp supports tool calls natively; no separate Python agent needed |
| 2026-05-21 | Existing parsers reused in Phase 1 | Proven extraction logic; graph is a routing layer, not a rewrite |
