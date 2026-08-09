# Design Document: NetWorth Tracker

## 1. System Overview

A self-hosted personal finance platform for Indian investors. Aggregates equity, mutual funds, EPF/NPS/PPF, gold, crypto, real estate, FDs, bonds, bank balances, salaries, and loans into a single dashboard with net-worth analytics, AI-powered insights, and Indian tax-awareness.

**Platforms:** Web (React) + Mobile (Flutter for iOS, Android & Web)

---

## 2. Architecture

### 2.1 High-Level Architecture

```
┌──────────────────────────────┐    ┌──────────────────────────────┐
│      React Web App           │    │     Flutter Mobile App       │
│  (Vite + React 19 + Tailwind)│    │     (iOS + Android + Web)    │
│        Port :3000            │    │        Port :8081            │
└──────────────┬───────────────┘    └──────────────┬───────────────┘
               │                                    │
               │ HTTP (Vite proxies /api)           │ HTTPS
               └─────────────┬──────────────────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │      Spring Boot API         │
              │    (Java 21, Boot 3.2.4)     │
              │  JWT Auth │ CORS │ REST API  │
              │        Port :8080            │
              └──────────────┬───────────────┘
                             │
        ┌────────────────────┼─────────────────────┐
        ▼                    ▼                     ▼
┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ PostgreSQL   │  │     Redis        │  │  llama.cpp       │
│   :5432      │  │     :6379        │  │   :8082          │
│ (Primary DB) │  │ (Cache+Sessions) │  │ (Local AI Chat)  │
└──────────────┘  └──────────────────┘  └──────────────────┘
                                          
              ┌──────────────────────────────────────┐
              │   Market Data Provider Framework      │
              │   (MarketDataResolver + config chain) │
              └──────────────┬───────────────────────┘
                             │
     ┌───────────┬───────────┼───────────┬───────────┬──────────┐
     ▼           ▼           ▼           ▼           ▼          ▼
┌─────────┐ ┌────────┐ ┌──────────┐ ┌────────┐ ┌───────┐ ┌────────┐
│ Upstox  │ │ Yahoo  │ │  Alpha   │ │  AMFI  │ │  NSE  │ │ Google │
│  V3 API │ │Finance │ │ Vantage  │ │NAVAll  │ │Dividnd│ │  News  │
│ (price) │ │(fallbk)│ │(fallback)│ │(MF NAV)│ │(corp) │ │ (RSS)  │
└─────────┘ └────────┘ └──────────┘ └────────┘ └───────┘ └────────┘
```

### 2.2 Market Data Provider Framework

The system uses a pluggable provider interface (`MarketDataProvider`) with config-driven fallback chains. To add a new data source, implement the interface and add the name to the config chain — no other code changes needed.

```
                  MarketDataResolver
                        │
           reads config chains from
            application.properties
                        │
    ┌───────────────────┼─────────────────────┐
    ▼                   ▼                     ▼
  price chain       mf-nav chain         dividend chain
  ┌─────────┐       ┌──────────┐         ┌─────────┐
  │ upstox  │       │upstox-mf │         │   nse   │
  │  ↓      │       │    ↓     │         │    ↓    │
  │ yahoo   │       │  amfi    │         │  yahoo  │
  │  ↓      │       └──────────┘         └─────────┘
  │alpha-vnc│
  └─────────┘
```

**Configuration (`application.properties`):**
```properties
market.providers.price=upstox,yahoo,alpha-vantage
market.providers.mf-nav=upstox-mf,amfi
market.providers.dividend=nse,yahoo
market.providers.news=google
```

**Provider implementations (8 total):**

| Provider | Class | Data Type | Notes |
|----------|-------|-----------|-------|
| `upstox` | `UpstoxProvider` | Equity/ETF LTP + OHLC | Analytics token, no rate limits |
| `upstox-mf` | `UpstoxMfProvider` | MF NAV | Via Upstox V3 |
| `yahoo` | `YahooProvider` | Global prices, dividends | Aggressive rate limiting (429) |
| `alpha-vantage` | `AlphaVantageProvider` | Global prices | 5 req/min free tier |
| `amfi` | `AmfiProvider` | MF NAV | `amfiindia.com/spages/NAVAll.txt` |
| `nse` | `NseProvider` | Dividend history | Session cookie handling for NSE API |
| `gold` | `GoldProvider` | Gold prices | GOLDBEES ETF proxy (domestic rate) |
| `google` | `GoogleNewsProvider` | News RSS | Per-holding news search |

### 2.3 Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Web Frontend** | React 19 + Vite + Tailwind CSS v4 | Desktop web app |
| | Recharts | Financial charts (area, pie, bar) |
| | Axios + React Router DOM | API client + routing |
| | Sonner | Toast notifications |
| **Mobile Frontend** | Flutter 3 + Dart | iOS + Android + Web |
| | Provider | State management |
| | fl_chart | Charts |
| | http + shared_preferences | API + local storage |
| **Backend** | Java 21 + Spring Boot 3.2.4 | REST API |
| | Spring Security + JWT | Authentication (access + refresh + TOTP 2FA) |
| | Spring Data JPA + Hibernate | ORM |
| | Flyway | Schema migrations (35 versions, latest V35) |
| **Database** | PostgreSQL 16 | Primary database |
| **Cache** | Redis 7 | Sessions, price cache, net worth history |
| **AI** | llama.cpp, OpenAI-compatible API on :8082 | Local inference for chat, insights, document extraction. `docs/ai-orchestrator.md` specifies Gemma 4 E4B (128K context, native function calling); the model actually served is set by `LLAMA_MODEL_REPO` in docker-compose |
| **PDF** | Apache PDFBox 3.0.1 | Salary slip text extraction |
| **Email** | Google Gmail API (OAuth2) | Bank transaction alert parsing |
| **Deployment** | Docker + Docker Compose | Containerization |

---

## 3. Database Design

### 3.1 Entity Relationships

```
users (1) ──── (N) holdings
users (1) ──── (N) liabilities
users (1) ──── (N) goals
users (1) ──── (N) bank_accounts
users (1) ──── (N) demat_accounts
users (1) ──── (N) salaries
users (1) ──── (N) documents
users (1) ──── (N) themes
users (1) ──── (N) broker_connections
users (1) ──── (N) refresh_tokens
users (1) ──── (N) families (via family_members)
holdings (1) ── (N) transactions
holdings (1) ── (N) dividends
holdings (N) ── (1) demat_accounts
holdings (N) ── (1) symbols (via ISIN)
holdings (N) ── (N) goals (via goal_holdings)
symbols (1) ──── (N) stock_price_history
symbols (1) ──── (N) symbol_aliases
families (1) ── (N) family_members
salaries (N) ── (1) documents
salaries (N) ── (1) bank_accounts
```

### 3.2 Core Tables

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `users` | User accounts | email, password, name, two_factor_secret, two_factor_enabled |
| `holdings` | All asset positions | symbol, asset_type, quantity, avg_buy_price, current_price, isin, demat_account_id |
| `transactions` | Buy/sell/SIP/dividend | holding_id, type, quantity, price, date |
| `liabilities` | Loans and EMIs | type, lender, original_amount, interest_rate, tenure_months |
| `goals` | Financial goals | name, target_amount, current_amount, target_date, goal_type, risk_profile |
| `goal_holdings` | Goal-to-holding mapping | goal_id, holding_id, allocation_percentage |
| `tax_records` | Capital gains per FY | holding_id, fiscal_year, gain_type, amount |
| `dividends` | Dividend payments | holding_id, amount, date |
| `bank_accounts` | Bank accounts | bank_name, account_number, account_type, balance |
| `demat_accounts` | Broker accounts | broker_name, account_number (full, masked in API), is_default |
| `symbols` | NSE/BSE + MF master data | symbol, name, isin, category, exchange |
| `symbol_aliases` | Multiple symbol formats | symbol_id, alias (e.g., RELIANCE.NS) |
| `stock_price_history` | Daily OHLCV data | symbol, date, open, high, low, close, volume |
| `market_prices` | Latest cached prices | symbol, price, source, date |
| `salaries` | Income tracking | employer, amount, pay_date, components (JSONB) |
| `documents` | Uploaded files | filename, content_type, path, holding_id, salary_id |
| `email_transactions` | Gmail-detected transactions | amount, balance, bank, account_last4, status |
| `gmail_connections` | Gmail OAuth tokens | access_token, refresh_token (AES-256 encrypted) |
| `families` + `family_members` | Family groups | role, status (pending/accepted) |
| `themes` | UI customization | name, colors (JSON), is_custom |
| `broker_connections` | OAuth broker tokens | broker, access_token, refresh_token, status |
| `refresh_tokens` | JWT refresh tokens | token, user_id, expires_at, revoked |
| `import_jobs` | CSV/PDF import tracking | source, status, records_imported |
| `audit_logs` | Action audit trail | user_id, action, entity, timestamp |

### 3.3 Flyway Migrations (V1–V35)

| Version | Description |
|---------|-------------|
| V1 | Core schema (users, holdings, transactions, market_prices, goals, liabilities) |
| V2 | Performance indexes |
| V3 | Goal mappings, EMI schedules |
| V4 | Demat accounts |
| V5 | Demat-to-holdings FK |
| V6 | Documents |
| V7 | Symbols table |
| V8 | Holding-to-documents |
| V9 | Themes |
| V10 | Symbol aliases |
| V11 | Day change columns (dayChange, dayChangePct on holdings) |
| V12 | Family tables (families, family_members) |
| V13 | Bank accounts, salaries |
| V14 | Salary components (JSONB column) |
| V15 | Salary-to-documents link |
| V16 | Gmail bank monitoring (gmail_connections, email_transactions) |
| V17 | Stock price history (daily OHLCV) |
| V18 | ISIN column on symbols |
| V19 | Refresh tokens table |
| V20 | Two-factor authentication fields |
| V21 | Feature flags infrastructure |
| V22 | Broker connections (OAuth token storage) |
| V23 | Goal-holdings mapping table |

---

## 4. API Design

### 4.1 Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register user (returns JWT access + refresh) |
| POST | `/api/v1/auth/login` | Login (returns JWT tokens, handles 2FA) |
| POST | `/api/v1/auth/refresh` | Refresh access token |
| POST | `/api/v1/auth/2fa/setup` | Setup TOTP 2FA (returns QR code URI) |
| POST | `/api/v1/auth/2fa/verify` | Verify TOTP code |

**Token lifecycle:**
- Access token: 15 min expiry
- Refresh token: 24h expiry, rotation on use (old token revoked)
- Rate limiting on `/auth` endpoints to prevent brute force

### 4.2 Portfolio

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/portfolio/holdings` | All holdings (`?view=f` for family view) |
| POST | `/api/v1/portfolio/holdings` | Create holding (auto-resolves ISIN) |
| GET | `/api/v1/portfolio/holdings/{id}` | Holding details |
| PUT | `/api/v1/portfolio/holdings/{id}` | Update holding |
| DELETE | `/api/v1/portfolio/holdings/{id}` | Soft delete holding |
| GET | `/api/v1/portfolio/holdings/{id}/transactions` | Holding transactions |
| GET | `/api/v1/portfolio/holdings/{id}/price-history` | OHLC price history (public, no ownership check) |
| GET | `/api/v1/portfolio/transactions` | All user transactions |
| POST | `/api/v1/portfolio/transactions` | Add transaction |
| POST | `/api/v1/portfolio/refresh-prices` | Refresh all prices via provider chain |
| GET | `/api/v1/portfolio/summary` | Portfolio summary |
| GET | `/api/v1/portfolio/investment-over-time` | Investment growth time-series |

**Holding creation rules:**
- EQUITY, ETF, MUTUAL_FUND require a linked `dematAccountId`
- ISIN is auto-resolved from the symbols table at creation
- Startup job backfills missing ISINs on existing holdings

### 4.3 Market Data

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/market/backfill-prices` | Backfill OHLC history (all holdings) |
| POST | `/api/v1/market/backfill-holding/{id}` | Incremental backfill for single holding |
| GET | `/api/v1/symbols` | List all symbols |
| POST | `/api/v1/symbols/refresh` | Refresh from NSE CSV + AMFI (category-scoped delete) |

**Incremental backfill:** checks latest existing record in DB, only fetches from `latestDate + 1`. Skips if already up to date.

### 4.4 Net Worth

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/net-worth` | Current net worth (triggers daily snapshot) |
| GET | `/api/v1/net-worth/history` | Historical net worth (Redis-backed) |
| GET | `/api/v1/net-worth/change` | Net worth change over period |
| GET | `/api/v1/net-worth/breakdown` | Asset breakdown by type |
| GET | `/api/v1/net-worth/health-score` | Financial health score (see Section 7.4) |

### 4.5 Goals

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST | `/api/v1/goals` | List/create goals |
| PUT/DELETE | `/api/v1/goals/{id}` | Update/delete goal |
| POST | `/api/v1/goals/{id}/holdings` | Link holding with allocation % |
| DELETE | `/api/v1/goals/{id}/holdings/{holdingId}` | Unlink holding |
| GET | `/api/v1/goals/{id}/holdings` | List linked holdings |

**Auto-tracking:** Goal `currentAmount` auto-computed as sum of linked holdings' current values. Daily refresh at 2 AM via `@Scheduled`.

### 4.6 Tax

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/tax/summary/{fy}` | Tax summary for FY |
| GET | `/api/v1/tax/capital-gains/{fy}` | Detailed capital gains (LTCG/STCG) |
| GET | `/api/v1/tax/harvesting-opportunities` | Tax-loss harvesting suggestions |
| GET | `/api/v1/tax/80c-utilization` | Section 80C usage vs 1.5L limit |
| GET | `/api/v1/tax/regime-comparison` | Old vs New regime side-by-side |

### 4.7 Analytics

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/analytics/xirr` | XIRR (time-weighted return) |
| GET | `/api/v1/analytics/cagr` | CAGR |
| GET | `/api/v1/analytics/risk` | Volatility, Sharpe ratio, max drawdown |
| GET | `/api/v1/analytics/allocation` | Asset allocation breakdown |
| GET | `/api/v1/analytics/allocation/sector` | Sector allocation |
| GET | `/api/v1/analytics/sip-calendar` | Upcoming SIPs |
| GET | `/api/v1/analytics/rebalancing` | Rebalancing suggestions by risk profile |

### 4.8 Banking & Salary

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST | `/api/v1/bank-accounts` | List/create bank accounts |
| PUT/DELETE | `/api/v1/bank-accounts/{id}` | Update/delete |
| GET/POST | `/api/v1/demat-accounts` | List/create demat accounts (masked in response) |
| PUT/DELETE | `/api/v1/demat-accounts/{id}` | Update/delete (blank accountNumber = keep existing) |
| GET/POST | `/api/v1/salaries` | Salary records |
| POST | `/api/v1/salaries/parse-slip` | AI-parse uploaded PDF pay slip |

**Demat account masking:** Full account number stored in DB; API always returns `****XXXX` (last 4 digits). Edit form doesn't prefill to prevent exposure.

### 4.9 AI & News

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/ai/chat` | Portfolio-aware AI chat (news context injected) |
| GET | `/api/v1/ai/insights` | AI-generated portfolio insights |
| GET | `/api/v1/news/search` | Search news by query |
| GET | `/api/v1/news/holdings` | News for user's equity/ETF/MF holdings |

### 4.10 Broker Integration

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/broker/upstox/auth-url` | Get Upstox OAuth URL |
| POST | `/api/v1/broker/upstox/callback` | OAuth code exchange + token storage |
| POST | `/api/v1/broker/upstox/sync` | Sync holdings (auto-creates demat, dedup by userId+symbol+dematId) |
| GET | `/api/v1/broker/upstox/status` | Connection status |
| DELETE | `/api/v1/broker/upstox/disconnect` | Revoke connection |

### 4.11 Family, Documents, Gmail, Themes, Feature Flags

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST | `/api/v1/families` | List/create families |
| POST | `/api/v1/families/{id}/invite` | Invite member by email |
| POST | `/api/v1/families/invitations/{id}/respond` | Accept/decline invite |
| POST | `/api/v1/documents/upload` | Multipart file upload |
| GET | `/api/v1/documents/{id}/download` | Download document |
| GET | `/api/v1/gmail/auth-url` | Gmail OAuth URL |
| POST | `/api/v1/gmail/sync` | Trigger bank alert sync |
| GET | `/api/v1/gmail/transactions` | List parsed transactions |
| POST | `/api/v1/gmail/transactions/{id}/confirm` | Confirm transaction (updates bank balance) |
| GET | `/api/v1/themes` | List themes (built-in + custom) |
| POST | `/api/v1/themes` | Create custom theme |
| GET | `/api/v1/features` | Feature flags (public endpoint) |
| POST | `/api/v1/import` | CSV/PDF import (Zerodha, Groww, CAS, Bank) |

---

## 5. Service Architecture

### 5.1 Backend Services (Spring Boot)

```
src/main/java/com/networth/
├── config/                    # Security, Redis, CORS, Scheduler, WebMvc config
├── controller/                # 30+ REST controllers
├── service/
│   ├── portfolio/             # HoldingService, TransactionService, CostBasisService
│   ├── market/
│   │   ├── provider/          # MarketDataProvider interface + 8 implementations
│   │   ├── MarketDataResolver # Config-driven provider chain resolution
│   │   ├── PriceService       # Price fetch orchestrator (cache → DB → provider)
│   │   ├── UpstoxHistoricalService  # OHLC backfill with incremental fetch
│   │   └── StartupBackfillService   # ISIN resolution + backfill on boot
│   ├── networth/              # NetWorthService, NetWorthHistoryService
│   ├── tax/                   # CapitalGainsCalculator, TaxHarvestService, TaxRegimeCalculator
│   ├── analytics/             # XIRRCalculator, RiskService, AnalyticsService, RebalancingService
│   ├── broker/                # UpstoxBrokerService (OAuth + holdings sync)
│   ├── HealthScoreService     # 6-dimension weighted health score
│   ├── GoalService            # Goal CRUD + holding linking + auto-progress
│   ├── EMIService             # EMI calculation + amortization schedule
│   ├── SymbolService          # NSE/AMFI master data refresh
│   ├── BankAccountService     # Bank account management
│   ├── SalaryService          # Salary CRUD + AI slip parsing
│   ├── FeatureFlagService     # Config-driven feature flags
│   ├── GmailSyncService       # Gmail OAuth + bank alert parsing
│   ├── AIChatService          # legacy llama.cpp integration, still serves POST /ai/chat
│   ├── documentgraph/         # AI orchestrator — see docs/ai-orchestrator.md
│   │   ├── AiClient           # HTTP client to the LLM (streaming + non-streaming)
│   │   ├── mcp/
│   │   │   ├── McpAIChatService   # main orchestrator: tool loop, HITL, sessions, SSE
│   │   │   ├── AgentExecutor      # sub-orchestrator for expert agents
│   │   │   ├── AgentContext       # ThreadLocal SSE bridge for agent events
│   │   │   ├── McpToolClient      # tool registry and execution
│   │   │   └── tools/             # 46 tools across 6 classes
│   │   └── nodes/             # document processing graph (classify, route, extract, execute)
│   ├── tax/rules/             # TaxRuleRegistry + tax-rules.json (FY 2000-01 to 2026-27)
│   ├── NewsService            # Per-holding news via provider framework
│   └── ImportService          # CSV/PDF import (Zerodha, Groww, CAS, Bank)
├── repository/                # Spring Data JPA repositories
├── model/
│   ├── entity/                # JPA entities (Holding, Goal, GoalHolding, etc.)
│   ├── dto/                   # Request/response DTOs (HoldingRequest, etc.)
│   └── enums/                 # AssetType, TransactionType, FamilyRole, etc.
├── security/                  # JwtAuthenticationFilter, JwtService, RateLimitFilter
├── exception/                 # GlobalExceptionHandler with correlation IDs
└── scheduler/                 # Price refresh, NAV update, goal progress, net worth snapshots
```

### 5.2 Web Frontend (React)

```
web/src/
├── api/
│   └── client.js              # Axios instance with JWT interceptor + refresh
├── pages/                     # 18 page components
│   ├── Dashboard.jsx          # Net worth summary, health score, quick stats
│   ├── Holdings.jsx           # Multi-asset holdings with charts, news, backfill
│   ├── NetWorth.jsx           # Net worth history, breakdown, trends
│   ├── Analytics.jsx          # XIRR, CAGR, allocation, risk metrics
│   ├── Tax.jsx                # Capital gains, regime comparison, 80C
│   ├── Goals.jsx              # Goal tracking with holding linker
│   ├── Liabilities.jsx        # Loans, EMI schedule, prepayment calculator
│   ├── AIChat.jsx             # Full-page AI chat interface
│   ├── BankAccounts.jsx       # Bank accounts + Gmail bank alerts
│   ├── Salaries.jsx           # Salary records + AI PDF parsing
│   ├── Transactions.jsx       # All transactions list
│   ├── DematAccounts.jsx      # Broker demat accounts (masked)
│   ├── Family.jsx             # Family members + invites
│   ├── Import.jsx             # CSV/PDF import wizard
│   ├── Documents.jsx          # Document management
│   ├── Profile.jsx            # Profile + theme customization
│   ├── Login.jsx              # Login with 2FA support
│   └── Register.jsx           # Registration
├── components/
│   ├── ui/                    # 9 UI primitives (Button, Card, Input, etc.)
│   ├── Layout.jsx             # App shell with sidebar navigation
│   ├── FloatingChat.jsx       # Floating AI chat bubble
│   ├── GoalHoldingLinker.jsx  # Goal-holding mapping modal
│   ├── UpstoxSync.jsx         # Upstox connect/sync (feature-flagged)
│   ├── NewsPanel.jsx          # Per-holding news panel
│   └── ErrorBoundary.jsx      # Error boundary wrapper
├── context/
│   ├── AuthContext.jsx         # Auth state + JWT management
│   ├── ThemeContext.jsx        # Theme switching
│   ├── FamilyViewContext.jsx   # Individual ↔ Family view toggle
│   └── FeatureFlagContext.jsx  # Feature flag provider + useFeature() hook
├── index.css                   # Tailwind v4 + CSS variables
└── main.jsx                    # Router setup with protected routes
```

### 5.3 Mobile Frontend (Flutter)

```
mobile/lib/
├── main.dart                       # App entry point
├── core/
│   ├── constants/
│   │   ├── app_colors.dart        # Centralized color palette
│   │   └── app_config.dart        # API URL, storage keys
│   ├── services/
│   │   └── api_client.dart        # HTTP client with JWT + refresh
│   ├── theme/
│   │   └── app_theme.dart
│   └── utils/
│       ├── formatters.dart        # Currency, percentage, date, file size
│       └── validators.dart        # Email, password, number validation
├── data/
│   ├── models/
│   │   └── app_models.dart        # Holding, NetWorthData, Goal, etc.
│   └── repositories/
├── providers/
│   └── data_provider.dart         # ChangeNotifier for global state
├── features/                       # Feature-based grouping (19 screens)
│   ├── auth/                      # Login, Register
│   ├── dashboard/                 # Dashboard
│   ├── portfolio/                 # Holdings, Transactions, Analytics
│   ├── accounts/                  # Bank Accounts, Demat, Salaries
│   ├── networth/                  # Net Worth, Goals, Liabilities, Tax
│   ├── personal/                  # Documents, Family, Import
│   ├── ai_chat/                   # AI Chat
│   ├── profile/                   # Profile
│   └── home/                      # Navigation hub
└── widgets/
    ├── charts/                    # Pie chart, price chart
    ├── common/                    # Error, loading, empty state, stat card
    └── dialogs/                   # Add holding, transaction, goal forms
```

**Architecture principles:**
- Feature-based organization (not MVC layers)
- Absolute `package:` imports for clarity
- Provider pattern for state management (lightweight, no boilerplate)
- Single model file (`app_models.dart`) instead of one-file-per-model
- Shared utilities prevent code duplication (formatters used in 12+ screens)

---

## 6. Security Design

| Layer | Measure | Implementation |
|-------|---------|----------------|
| Auth | JWT Access + Refresh | Access: 15min, Refresh: 24h with rotation |
| 2FA | TOTP | Real secret generation, QR URI for authenticator apps |
| Password | BCrypt | Cost factor 12 |
| Rate Limiting | Per-endpoint | Applied on `/auth` endpoints |
| IDOR Protection | Ownership checks | All user-scoped endpoints verify `userId` matches JWT subject |
| Public Endpoints | Explicit skip | Price history and market data endpoints skip ownership (shared data) |
| API | CORS | Whitelist: `localhost:3000`, `localhost:8081` |
| Demat Masking | Response-layer | Full account stored in DB, masked to `****XXXX` in API response |
| Gmail Tokens | AES-256 encryption | `TOKEN_ENCRYPTION_KEY` env var (32 chars) |
| Soft Delete | Holdings | Deleted holdings marked, not physically removed |
| Optimistic Locking | `@Version` | Prevents concurrent update conflicts |
| Audit | Action logging | `audit_logs` table with user, action, entity, timestamp |
| Web Storage | localStorage | JWT token + user info |
| Mobile Storage | SharedPreferences | JWT token + user info |

---

## 7. Key Algorithms

### 7.1 XIRR Calculation (Newton-Raphson)

Used in `XIRRCalculator.java` for time-weighted returns considering irregular cash flows. Iterates until NPV converges within tolerance (1e-7). Frontend clamps display to `>9,999%` for extreme values with `isFinite()` guard.

### 7.2 FIFO Cost Basis

`CostBasisService.java` uses First-In-First-Out lot matching for accurate capital gains calculation on partial sells.

### 7.3 Tax Harvesting Logic

```
For each holding with unrealized gains:
  1. Check holding period vs LTCG threshold (12 months equity)
  2. Calculate gain if sold today
  3. Check total LTCG for FY < 1,25,000 exemption
  4. If gain fits: Suggest sell to book tax-free + rebuy
  5. If exceeds: Suggest partial sell to utilize remaining exemption
```

### 7.4 Health Score Algorithm (6 Dimensions)

Composite score (0-100) with weighted dimensions:

| Dimension | Weight | What it Measures |
|-----------|--------|------------------|
| Emergency Fund | 20% | Liquid assets vs 6 months expenses (EPF/PPF/NPS excluded from liquid) |
| Debt Health | 20% | EMI-to-income ratio (target < 40%) |
| Savings Rate | 15% | Monthly savings as % of income |
| Diversification | 15% | Multi-asset spread + concentration risk (no single holding > 30%) |
| Liquidity | 15% | % of portfolio in liquid assets |
| Goal Progress | 15% | On-track calculation using time elapsed vs amount accumulated |

Output: `totalScore` (0-100), letter grade (A+ to D), per-dimension breakdown, actionable `recommendations` array.

### 7.5 Tax Regime Comparison

`TaxRegimeCalculator` computes tax liability under both Old and New regimes for the same income, accounting for available deductions (80C, 80D, HRA, etc.) and recommends the lower-tax option.

### 7.6 Price Fetch Flow

```
PriceService.getCurrentPrice(symbol, assetType)
  → Redis cache hit (15 min TTL)? return
  → market_prices DB hit (today's row)? return
  → MarketDataResolver.resolve(assetType)
    → Reads provider chain from config
    → Tries each provider in order until success
    → First successful result wins
  → Cache in Redis + persist to market_prices DB
  → Update holding.currentPrice, dayChange, dayChangePct
```

### 7.7 ISIN Resolution

```
HoldingService.createHolding()
  → Look up ISIN from symbols table by symbol name
  → If found, set holding.isin
  → On startup: backfillMissingIsins() fixes existing holdings
  → Fallback: UpstoxHistoricalService.resolveAndPersistIsin()
```

---

## 8. Indian Tax Rules (FY 2024-25)

| Asset | STCG | LTCG |
|-------|------|------|
| Equity | 20% (< 12 months) | 12.5% above 1.25L (> 12 months) |
| Debt MF | Slab rate | Slab rate (indexation removed) |
| Gold (Physical) | Slab rate (< 36 months) | 20% w/ indexation (> 36 months) |
| SGB | Slab rate | Exempt at maturity (8 years) |
| Real Estate | Slab rate (< 24 months) | 20% w/ indexation (> 24 months) |
| Crypto/NFT | 30% flat + 4% cess | 30% flat + 4% cess |

**Deductions:**
- Section 80C: 1.5L (ELSS, PPF, EPF, NPS, life insurance, home loan principal)
- Section 80CCD(1B): Additional 50K (NPS)
- Section 80D: Health insurance premiums

---

## 9. Scheduled Jobs

| Job | Frequency | Service | Notes |
|-----|-----------|---------|-------|
| Equity/ETF Price Refresh | Every 15min (market hours) | `PriceUpdateScheduler` | Via provider chain |
| MF NAV Update | Daily 8 PM | `NavUpdateScheduler` | AMFI publishes ~6-7 PM |
| Net Worth Snapshot | Daily | `NetWorthHistoryService` | Stored in Redis (365 day TTL) |
| Goal Progress Refresh | Daily 2 AM | `GoalService` | Recomputes from linked holdings |
| ISIN Backfill | On startup | `StartupBackfillService` | Fixes holdings missing ISIN |
| Symbol Refresh | On demand | `SymbolService` | Also triggers ISIN backfill |
| Gmail Bank Sync | Every 5 min | `GmailSyncService` | If Gmail is connected |

---

## 10. Feature Flags

Config-driven in `application.properties` (not database):

```properties
features.upstox-import=${FEATURE_UPSTOX_IMPORT:false}
```

**Backend:** `FeatureFlagService` reads `features.*` properties. Public endpoint `GET /api/v1/features` returns all flags.

**Frontend:** `FeatureFlagContext` + `useFeature()` hook. Components conditionally render based on flags:
```jsx
const upstoxEnabled = useFeature('upstox-import');
{upstoxEnabled && <UpstoxSync />}
```

---

## 11. Deployment

### 11.1 Local Development

```bash
# Backend (Spring Boot, port 8080)
source ~/.zshrc && export UPSTOX_ANALYTICS_TOKEN  # Required for forked JVM
mvn spring-boot:run

# Web (React + Vite HMR, port 3000)
cd web && npm run dev

# Mobile (Flutter)
cd mobile && flutter run              # Device/emulator
cd mobile && flutter run -d chrome    # Web (port 8081)

# AI server (llama.cpp, port 8082)
llama-server -m models/Llama-3.2-3B-Instruct-Q4_K_M.gguf \
  --host 127.0.0.1 --port 8082 --ctx-size 4096 --temp 0.3

# Or start everything:
bash start.sh
```

### 11.2 Docker Deployment

```bash
docker-compose up -d                                  # Development
docker-compose -f docker-compose.prod.yml up -d       # Production
```

Services: `app` (:8080), `postgres` (:5432), `redis` (:6379)

### 11.3 Production Build

```bash
mvn package -DskipTests          # Backend JAR
cd web && npm run build           # Frontend static files
java -jar target/networth-tracker-*.jar
```

---

## 12. Project Structure

```
app/
├── src/main/java/com/networth/
│   ├── controller/               # 30+ REST controllers
│   ├── model/
│   │   ├── dto/                  # Request/response DTOs
│   │   ├── entity/               # JPA entities
│   │   └── enums/                # AssetType, TransactionType, etc.
│   ├── repository/               # Spring Data JPA repos
│   ├── security/                 # JWT filter, auth config, rate limiter
│   └── service/
│       ├── market/
│       │   └── provider/         # MarketDataProvider interface + 8 impls
│       ├── portfolio/            # Holding, Transaction, CostBasis
│       ├── broker/               # Upstox OAuth + sync
│       └── ...                   # Tax, AI, Gmail, Goals, Health, etc.
├── src/main/resources/
│   ├── application.properties    # All config (DB, Redis, providers, features)
│   └── db/migration/             # V1-V23 Flyway migrations
├── web/                          # React frontend
│   └── src/
│       ├── pages/                # 18 page components
│       ├── components/           # UI primitives + feature components
│       ├── context/              # Auth, Theme, FeatureFlag, FamilyView
│       └── main.jsx              # Router setup
├── mobile/                       # Flutter app
│   └── lib/
│       ├── core/                 # API client, auth, theme
│       ├── data/                 # Models, repositories
│       ├── features/             # 19 feature screens
│       └── widgets/              # Shared widgets
├── docs/
│   ├── demo.gif                  # Animated UI demo
│   └── screenshots/              # 16 UI screenshots
├── start.sh                      # Start all services
├── llama.sh                      # Manage llama.cpp server
├── docker-compose.yml            # Dev Docker setup
├── docker-compose.prod.yml       # Production Docker setup
├── Dockerfile                    # Backend image
├── pom.xml                       # Maven build
├── Design.md                     # This document
└── README.md                     # User-facing README
```

---

## 13. Caching Strategy

| Data | TTL | Key Pattern | Store |
|------|-----|-------------|-------|
| Equity prices | 15 min | `market:price:{symbol}` | Redis |
| MF NAV | 24h | `market:nav:{scheme}` | Redis |
| Portfolio summary | 5 min | `portfolio:{userId}:summary` | Redis |
| Net worth snapshots | 365 days | `networth:history:{userId}:{date}` | Redis |
| Tax calculations | 1h | `tax:{userId}:{fy}` | Redis |
| Feature flags | Startup | In-memory (`FeatureFlagService`) | JVM |
| Provider chain config | Startup | In-memory (`MarketDataResolver`) | JVM |
