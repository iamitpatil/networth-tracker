# Design Document: Indian Investment & Net-Worth Tracker

## 1. System Overview

A unified personal balance sheet platform for Indian investors that aggregates equity, mutual funds, EPF/NPS/PPF, gold, crypto, real estate, bank balances, and loans into a single dashboard with net-worth analytics and Indian tax-awareness.

**Platforms:** Web (React) + Mobile (Flutter for iOS & Android)

---

## 2. Architecture

### 2.1 High-Level Architecture

```
┌──────────────────────────────┐    ┌──────────────────────────────┐
│      React Web App           │    │     Flutter Mobile App       │
│      (Vite + React 19)       │    │     (iOS + Android + Web)    │
└──────────────┬───────────────┘    └──────────────┬───────────────┘
               │                                    │
               │ HTTPS                              │ HTTPS
               └─────────────┬──────────────────────┘
                             │
                             ▼
              ┌──────────────────────────────┐
              │      Spring Boot API         │
              │    (Java 21, Boot 3.2.4)     │
              │  JWT Auth │ CORS │ REST API  │
              └──────────────┬───────────────┘
                             │
        ┌────────────────────┼─────────────────────┐
        ▼                    ▼                     ▼
┌──────────────┐  ┌──────────────────┐  ┌──────────────────┐
│ PostgreSQL   │  │     Redis        │  │  Market Data     │
│ (Primary DB) │  │ (Cache+Sessions) │  │  External APIs   │
└──────────────┘  └──────────────────┘  └──────────────────┘
                                          │
                       ┌──────────────────┼──────────────────┐
                       ▼                  ▼                  ▼
              ┌────────────────┐ ┌──────────────┐ ┌────────────────┐
              │ Yahoo Finance  │ │   AMFI       │ │ Upstox/Zerodha │
              │  (Equity)      │ │  (MF NAV)    │ │   (Broker API) │
              └────────────────┘ └──────────────┘ └────────────────┘
```

### 2.2 Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Web Frontend** | React 19 + Vite + Tailwind CSS | Desktop web app |
| | recharts + lightweight-charts | Financial charts |
| | axios + react-router-dom | API + Routing |
| **Mobile Frontend** | Flutter 3.24 + Dart | iOS + Android + Web |
| | provider | State management |
| | fl_chart | Charts |
| | http + shared_preferences | API + Local storage |
| **Backend** | Java 21 + Spring Boot 3.2 | REST API |
| | Spring Security + JWT | Authentication |
| | Spring Data JPA + Hibernate | ORM |
| **Database** | PostgreSQL 16 | Primary database |
| **Cache** | Redis | Sessions, price cache |
| **Market Data** | Yahoo Finance, AMFI, Upstox | Real-time prices |
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
users (1) ──── (N) families (via family_members)
users (1) ──── (N) themes
holdings (1) ── (N) transactions
holdings (1) ── (N) dividends
holdings (N) ── (1) demat_accounts
holdings (N) ── (1) symbols
symbols (1) ──── (N) stock_price_history
families (1) ── (N) family_members
```

### 3.2 Core Tables (Implemented)

| Table | Purpose |
|-------|---------|
| `users` | User accounts with email/password, 2FA |
| `holdings` | All assets: equity, MF, gold, FD, PPF, EPF, NPS, real estate, crypto |
| `transactions` | Buy/sell/SIP/dividend transactions |
| `liabilities` | Loans, EMIs, credit cards |
| `goals` | Financial goals with target/current amounts |
| `tax_records` | Capital gains tracking per FY |
| `dividends` | Dividend payments |
| `bank_accounts` | Savings/current/FD/NRE/NRO accounts |
| `demat_accounts` | Broker accounts (Zerodha, Groww, etc.) |
| `symbols` | NSE/BSE symbols + MF schemes master data |
| `symbol_aliases` | Multiple symbol formats (RELIANCE.NS, etc.) |
| `stock_price_history` | Daily OHLCV price data |
| `market_prices` | Latest cached prices |
| `salaries` | Income tracking with payslips |
| `documents` | Uploaded files (invoices, statements, ID proofs) |
| `email_transactions` | Gmail-detected bank transactions |
| `gmail_connections` | Gmail OAuth tokens for bank parsing |
| `families` + `family_members` | Family dashboard with invites |
| `themes` | User customization themes |
| `import_jobs` | CSV/PDF import tracking |
| `audit_logs` | Action audit trail |

---

## 4. API Design

### 4.1 Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register user |
| POST | `/api/v1/auth/login` | Login (returns JWT + refresh) |
| POST | `/api/v1/auth/refresh` | Refresh access token |

### 4.2 Portfolio

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/portfolio/holdings` | All holdings |
| POST | `/api/v1/portfolio/holdings` | Add holding |
| PUT/DELETE | `/api/v1/portfolio/holdings/{id}` | Update/delete holding |
| GET | `/api/v1/portfolio/holdings/{id}/price-history?days=N` | Price chart data (OHLCV) |
| GET | `/api/v1/portfolio/transactions` | All transactions |
| POST | `/api/v1/portfolio/transactions` | Add transaction |
| POST | `/api/v1/portfolio/refresh-prices` | Force price refresh |
| GET | `/api/v1/portfolio/summary` | Portfolio summary |
| GET | `/api/v1/portfolio/investment-over-time` | Historical investment growth |

### 4.3 Net Worth

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/net-worth/breakdown` | Detailed asset/liability breakdown |
| GET | `/api/v1/net-worth/health-score` | Financial health score (0-100) |
| GET | `/api/v1/net-worth/history` | Historical net worth |

### 4.4 Goals & Liabilities

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST | `/api/v1/goals` | List/create goals |
| PUT/DELETE | `/api/v1/goals/{id}` | Update/delete |
| GET/POST | `/api/v1/liabilities` | List/create liabilities |
| PUT/DELETE | `/api/v1/liabilities/{id}` | Update/delete |

### 4.5 Tax (FY-based)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/tax/summary/{fy}` | Tax summary for FY (e.g., 2024-2025) |
| GET | `/api/v1/tax/capital-gains/{fy}` | Detailed capital gains report |
| GET | `/api/v1/tax/harvesting-opportunities` | Tax harvesting suggestions |
| GET | `/api/v1/tax/80c-utilization` | Section 80C deduction breakdown |

### 4.6 Analytics

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/analytics/xirr` | XIRR (time-weighted return) |
| GET | `/api/v1/analytics/cagr` | CAGR |
| GET | `/api/v1/analytics/risk` | Volatility, Sharpe, Max Drawdown |
| GET | `/api/v1/analytics/allocation` | Asset allocation |
| GET | `/api/v1/analytics/allocation/sector` | Sector allocation |
| GET | `/api/v1/analytics/sip-calendar` | Upcoming SIPs |

### 4.7 Banking & Accounts

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST | `/api/v1/bank-accounts` | List/create bank accounts |
| GET/POST | `/api/v1/demat-accounts` | List/create demat accounts |
| GET/POST | `/api/v1/salaries` | Salary records with payslip parsing |
| POST | `/api/v1/salaries/parse-slip` | Auto-parse uploaded payslip |

### 4.8 Documents

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/documents` | List all documents |
| POST | `/api/v1/documents/upload` | Multipart file upload |
| GET | `/api/v1/documents/{id}/view` | View inline (browser preview) |
| GET | `/api/v1/documents/{id}/download` | Download as attachment |
| DELETE | `/api/v1/documents/{id}` | Delete document |

### 4.9 Family

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST | `/api/v1/families` | List/create families |
| POST | `/api/v1/families/{id}/invite` | Invite member by email |
| GET | `/api/v1/families/invitations/pending` | Pending invitations |
| POST | `/api/v1/families/invitations/{id}/respond` | Accept/decline invite |

### 4.10 Import & AI

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/import` | Import CSV/PDF (Zerodha, Groww, CAS, Bank) |
| POST | `/api/v1/ai/chat` | AI assistant chat |
| POST | `/api/v1/symbols/refresh` | Refresh NSE + MF master data |
| GET | `/api/v1/gmail/status` | Gmail connection status |
| POST | `/api/v1/gmail/sync` | Sync bank alerts from Gmail |

---

## 5. Service Architecture

### 5.1 Backend Services (Spring Boot)

```
src/main/java/com/networth/
├── config/                 # Security, Redis, CORS, Scheduler config
├── controller/             # REST endpoints
├── service/
│   ├── portfolio/         # HoldingService, TransactionService, CostBasisService
│   ├── market/            # YahooPriceFetcher, AmfiNavFetcher, UpstoxPriceFetcher, GoldPriceFetcher
│   ├── networth/          # NetWorthService
│   ├── tax/               # CapitalGainsCalculator, TaxHarvestService, DeductionService
│   ├── analytics/         # XIRRCalculator, RiskService, AnalyticsService
│   ├── importservice/     # ImportService, PDFStatementParser
│   └── broker/            # ZerodhaIntegrationService, AccountAggregatorService
├── repository/             # Spring Data JPA repositories
├── model/
│   ├── entity/            # JPA entities
│   ├── dto/               # Request/response DTOs
│   └── enums/             # AssetType, TransactionType, FamilyRole, etc.
├── security/               # JwtAuthenticationFilter, JwtService
├── exception/              # GlobalExceptionHandler
└── scheduler/              # Price update, NAV update, EMI reminders
```

### 5.2 Market Data Sources

| Asset | Primary | Fallback |
|-------|---------|----------|
| Equity (NSE/BSE) | Upstox API | Yahoo Finance |
| Mutual Funds | AMFI (NAV) | - |
| Gold | MCX/Gold ETF prices | - |
| Crypto | (TBD) | CoinGecko |

### 5.3 Web Frontend (React)

```
web/src/
├── api/
│   └── client.js          # Axios instance with JWT interceptor
├── pages/                 # 18 pages: Dashboard, Holdings, NetWorth, Goals,
│                          # Liabilities, Tax, Analytics, Transactions,
│                          # BankAccounts, DematAccounts, Documents, Family,
│                          # Salaries, Import, Profile, AIChat, Login, Register
├── components/
│   ├── Layout.jsx
│   ├── FloatingChat.jsx
│   └── ErrorBoundary.jsx
├── context/
│   ├── AuthContext.jsx
│   └── ThemeContext.jsx
├── App.jsx
└── main.jsx
```

### 5.4 Mobile Frontend (Flutter) - Feature-based Architecture

```
mobile/lib/
├── main.dart                       # App entry point
├── core/                           # Shared infrastructure
│   ├── constants/
│   │   ├── app_colors.dart        # Centralized color palette
│   │   └── app_config.dart        # API URL, storage keys
│   ├── services/
│   │   └── api_client.dart        # HTTP client with JWT
│   ├── theme/
│   │   └── app_theme.dart
│   └── utils/
│       ├── formatters.dart        # currency, percentage, date, fileSize
│       └── validators.dart        # email, password, number validation
├── data/
│   ├── models/
│   │   └── app_models.dart        # Holding, NetWorthData, Goal, etc.
│   └── repositories/              # (Future: API call abstractions)
├── providers/
│   └── data_provider.dart         # ChangeNotifier for global state
├── features/                       # Feature-based grouping
│   ├── auth/
│   │   ├── login_screen.dart
│   │   └── register_screen.dart
│   ├── dashboard/
│   │   └── dashboard_screen.dart
│   ├── portfolio/
│   │   ├── holdings_screen.dart
│   │   ├── transactions_screen.dart
│   │   └── analytics_screen.dart
│   ├── accounts/
│   │   ├── bank_accounts_screen.dart
│   │   ├── demat_accounts_screen.dart
│   │   └── salaries_screen.dart
│   ├── networth/
│   │   ├── networth_screen.dart
│   │   ├── goals_screen.dart
│   │   ├── liabilities_screen.dart
│   │   └── tax_screen.dart
│   ├── personal/
│   │   ├── documents_screen.dart
│   │   ├── family_screen.dart
│   │   └── import_screen.dart
│   ├── ai_chat/
│   │   └── ai_chat_screen.dart
│   ├── profile/
│   │   └── profile_screen.dart
│   └── home/
│       └── more_screen.dart       # Main navigation hub
└── widgets/
    ├── charts/
    │   ├── asset_allocation_chart.dart   # Pie chart for asset breakdown
    │   └── holding_price_chart.dart      # Interactive price chart with periods
    ├── common/
    │   ├── error_view.dart              # Reusable error display
    │   ├── loading_view.dart            # Loading indicator
    │   ├── empty_state.dart             # Empty state with action
    │   └── stat_card.dart               # Reusable stat card
    └── dialogs/
        ├── add_holding_dialog.dart      # Multi-asset type form
        ├── add_transaction_dialog.dart  # Buy/sell with date picker
        └── add_goal_dialog.dart         # Goal creation with type selector
```

**Architecture principles:**
- **Feature-based** organization (not technical/MVC layers)
- **Absolute `package:` imports** for clarity
- **Provider pattern** for state management (lightweight, no boilerplate)
- **Single model file** (`app_models.dart`) instead of one-file-per-model
- **Shared utilities** prevent code duplication (formatters used in 12+ screens)

---

## 6. Security Design

| Layer | Measure | Implementation |
|-------|---------|----------------|
| Transport | TLS 1.3 | (Production: TBD) |
| Auth | JWT + Refresh Tokens | Access: 15min, Refresh: 24h |
| Password | BCrypt | Cost factor 12 |
| API | CORS | Whitelist origins (`localhost:3000`, `localhost:8081`) |
| Mobile Storage | SharedPreferences | JWT token + user info |
| Web Storage | localStorage | JWT token + user info |
| Audit | Action Logging | `audit_logs` table |

---

## 7. Key Algorithms

### 7.1 XIRR Calculation (Newton-Raphson)

Used in `XIRRCalculator.java` for time-weighted returns considering cash flows. Iterates until NPV converges within tolerance (1e-7).

### 7.2 FIFO Cost Basis

`CostBasisService.java` uses First-In-First-Out lot matching for accurate capital gains calculation on partial sells.

### 7.3 Tax Harvesting Logic

```
For each holding with unrealized gains:
  1. Check holding period vs LTCG threshold (12 months equity)
  2. Calculate gain if sold today
  3. Check total LTCG for FY < ₹1,25,000 exemption
  4. If gain fits: Suggest sell to book tax-free + rebuy
  5. If exceeds: Suggest partial sell to utilize remaining exemption
```

### 7.4 Health Score Algorithm

Composite score (0-100) based on:
- Emergency fund ratio (target: 6 months expenses)
- Debt-to-asset ratio (lower is better)
- Investment diversification
- Goals progress
- Insurance coverage

---

## 8. Indian Tax Rules (FY 2024-25)

| Asset | STCG | LTCG |
|-------|------|------|
| Equity | 20% (< 12 months) | 12.5% above ₹1.25L (> 12 months) |
| Debt MF | Slab rate | Slab rate (indexation removed) |
| Gold (Physical) | Slab rate (< 36 months) | 20% w/ indexation (> 36 months) |
| SGB | Slab rate | Exempt at maturity (8 years) |
| Real Estate | Slab rate (< 24 months) | 20% w/ indexation (> 24 months) |
| Crypto/NFT | 30% flat + 4% cess | 30% flat + 4% cess |

**Deductions:**
- Section 80C: ₹1.5L (ELSS, PPF, EPF, NPS, life insurance, home loan principal)
- Section 80CCD(1B): Additional ₹50K (NPS)
- Section 80D: Health insurance premiums

---

## 9. Scheduled Jobs

Implemented in `scheduler/`:

| Job | Frequency | Service |
|-----|-----------|---------|
| Equity Price Refresh | Every 15min (market hours) | `PriceUpdateScheduler` |
| MF NAV Update | Daily | `NavUpdateScheduler` |
| Gold Price Update | Daily | - |
| Net Worth Recalculation | Daily | - |
| EMI Due Reminders | Daily | `ReminderScheduler` |

---

## 10. Deployment

### 10.1 Local Development

```bash
# Backend (Spring Boot)
mvn spring-boot:run                       # http://localhost:8080

# Web (React + Vite)
cd web && npm run dev                     # http://localhost:3000

# Mobile (Flutter)
cd mobile && flutter run                  # iOS/Android emulator
cd mobile && flutter build web            # http://localhost:8081
```

### 10.2 Docker Deployment

```bash
docker-compose up -d        # Dev mode (PostgreSQL + Redis + Backend)
docker-compose -f docker-compose.prod.yml up -d
```

Services:
- `app` (Spring Boot) - Port 8080
- `postgres` - Port 5432
- `redis` - Port 6379

---

## 11. Implementation Status

### ✅ Completed Features

**Backend:**
- JWT authentication + refresh tokens
- All 10 controllers (Auth, Portfolio, NetWorth, Tax, Goals, Liabilities, Bank, Demat, Family, AI, Import, Salary, Documents, Themes, Analytics)
- Multi-source price fetching (Yahoo, AMFI, Upstox, Gold)
- Tax engine (LTCG/STCG with 80C tracking)
- Gmail OAuth integration for bank alert parsing
- CSV/PDF import (Zerodha, Groww, CAS, Bank Statements)
- AI chat service
- Family dashboard with invitations
- Document storage with view/download endpoints

**Web App (React):**
- 18 pages covering all features
- Interactive holdings chart (recharts + lightweight-charts)
- Theme customization
- Gmail bank sync UI
- Family management UI
- Tax planning with FY selector

**Mobile App (Flutter):**
- 19 screens across all features
- Provider-based state management
- Interactive price charts (fl_chart)
- Asset allocation pie charts
- Forms: Add holding, transaction, goal, bank account, demat account, salary
- Filter tabs for holdings (Equity/MF/Gold/FD)
- Document in-app viewer (images, text, PDF info)
- Holding detail modal with stats

### 🚧 Future Roadmap

- Broker API real-time integration (Zerodha Kite Connect)
- Account Aggregator (AA) framework integration
- US stocks support
- Advanced AI insights (portfolio recommendations)
- Rebalancing suggestions
- Push notifications
- Biometric authentication (mobile)
- Production deployment to AWS

---

## 12. Project Structure

```
app/
├── src/                          # Spring Boot backend
│   └── main/java/com/networth/
├── web/                          # React web app
│   └── src/
├── mobile/                       # Flutter mobile app (iOS/Android/Web)
│   ├── lib/
│   │   ├── core/                # Shared infrastructure
│   │   ├── data/                # Models and repositories
│   │   ├── features/            # Feature-based screens
│   │   ├── providers/           # State management
│   │   └── widgets/             # Reusable UI components
│   ├── pubspec.yaml
│   └── web/                     # Flutter web config
├── pom.xml                       # Maven build
├── docker-compose.yml            # Dev environment
├── docker-compose.prod.yml       # Production environment
├── Dockerfile                    # Backend image
└── Design.md                     # This document
```

---

## 13. Caching Strategy

| Data | TTL | Key Pattern |
|------|-----|-------------|
| User sessions | 24h | `session:{userId}` |
| Equity prices (intraday) | 15min | `market:price:{symbol}` |
| MF NAV (daily) | 24h | `market:nav:{isin}` |
| Portfolio summary | 5min | `portfolio:{userId}:summary` |
| Net worth | 15min | `networth:{userId}` |
| Tax calculations | 1h | `tax:{userId}:{fy}` |

---

## 14. Business Model

| Tier | Features | Price |
|------|----------|-------|
| **Free** | Portfolio tracking, basic net worth, manual entry, CSV import | ₹0 |
| **Pro** | Tax reports, AI insights, advanced analytics, SIP calendar, Gmail sync | ₹299/month |
| **Family** | Multi-member dashboard, advisor view, family net worth | ₹599/month |
