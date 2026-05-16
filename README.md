# Indian Investment & Net-Worth Tracker

A unified personal balance sheet platform for Indian investors. Aggregates equity, mutual funds, EPF/NPS/PPF, gold, crypto, real estate, bank balances, salaries, and loans into a single dashboard with net-worth analytics, AI-powered insights, and Indian tax-awareness.

## Features

- **Portfolio Tracking** — Equity, MF, EPF, PPF, NPS, Gold, Crypto, Real Estate, FD, Bonds, Cash, Bank Accounts
- **Market Data** — Upstox (primary for Indian equities), Yahoo Finance (fallback), Alpha Vantage (secondary fallback), AMFI NAV (mutual funds), Gold price API
- **Price History** — Upstox historical candle API backfills daily OHLC data into `stock_price_history` table; auto-snapshot on first visit; daily scheduled refresh at 3 AM
- **Stock Charts** — Click any stock name in Holdings to view an interactive price history chart (1M/3M/6M/1Y periods with O/H/L/C/V tooltip)
- **Net Worth Engine** — Real-time aggregation, daily history snapshots (Redis), health scoring (A+ to D), multi-asset allocation charts
- **Investment Growth Chart** — Area chart comparing cumulative invested vs portfolio value over time; filterable by asset type
- **Tax Engine** — LTCG/STCG calculation, tax harvesting suggestions, Section 80C tracking
- **Analytics** — XIRR, CAGR, volatility, Sharpe ratio, asset/sector allocation
- **Liabilities** — EMI schedule generation, loan payoff tracking, prepayment calculator
- **SIP Calendar** — Upcoming/missed SIP tracking, total monthly SIP commitment
- **Goal Tracking** — Financial goals with progress tracking, holding mapping
- **CSV/PDF Import** — Zerodha, Groww, CAS statements, bank statement parsing
- **Broker Integration** — Zerodha Kite API sync (holdings, positions, orders)
- **Salary Slip AI Parsing** — Upload PDF pay slips; PDFBox extracts text; llama.cpp AI parses employer, pay date, gross/net pay, and component breakdown; parsed data auto-fills salary form; slip stored as linked document
- **Gmail Bank Monitoring** — OAuth2 Gmail integration; fetches SMS/email alerts from 18+ Indian banks; regex-based parser extracts amount, balance, account last-4; AI fallback via llama.cpp; matches transactions to bank accounts; auto-updates balances on confirmation
- **Custom Themes** — 10 built-in themes + user-uploadable custom themes via color picker; theme cards with Edit/Delete
- **AI Chat** — Local llama.cpp server (Llama-3.2-3B-Instruct-Q4_K_M) for portfolio Q&A, net worth questions
- **AI Insights** — Concentration risk, diversification, tax harvesting alerts, rebalancing suggestions
- **Dividend Tracker** — Passive income estimation, yield calculation
- **Rebalancing** — Suggestions by risk profile, allocation drift detection
- **Family Dashboard** — Multi-member net worth, health score, holdings aggregation
- **Multi-Asset Allocation** — Pie chart + investment growth AreaChart side-by-side on Holdings page

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot 3.2.4 |
| Frontend | React 19, Vite, Tailwind v4, Recharts |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Build | Maven (backend), Vite (frontend) |
| Migrations | Flyway |
| Auth | JWT + Bcrypt (custom filter) |
| AI | llama.cpp (local, port 8081) |
| PDF | Apache PDFBox 3.0.1 |
| OAuth | Google API Client (Gmail API) |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Browser (React)                       │
│  Vite Dev Server :3000  →  Proxy /api → :8080           │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP / WebSocket
                           ▼
┌──────────────────────────────────────────────────────────┐
│                 Spring Boot Backend :8080                  │
│                                                           │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐  ┌─────────┐ │
│  │JWT Filter│  │Controllers│  │ Services  │  │Scheduled│ │
│  └──────────┘  └──────────┘  └─────┬─────┘  │ Tasks   │ │
│                                     │         └─────────┘ │
│  ┌──────────────────────────────────┴──────────────┐      │
│  │            Data Layer                            │      │
│  │  ┌──────────┐  ┌──────────┐  ┌───────────────┐  │      │
│  │  │PostgreSQL│  │  Redis   │  │ llama.cpp AI  │  │      │
│  │  │  :5432   │  │  :6379   │  │    :8081      │  │      │
│  │  └──────────┘  └──────────┘  └───────────────┘  │      │
│  └──────────────────────────────────────────────────┘      │
│                                                           │
│  ┌──────────────────────────────────────────────────┐      │
│  │         External APIs (Price Sources)             │      │
│  │  Upstox V3 (primary)  → api.upstox.com/v3        │      │
│  │  Yahoo Finance        → query1.finance.yahoo.com  │      │
│  │  Alpha Vantage        → alphavantage.co           │      │
│  │  AMFI NAV             → amfiindia.com             │      │
│  │  Gold API             → gold-api.com              │      │
│  │  Gmail API            → gmail.googleapis.com      │      │
│  └──────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────┘
```

### Price Fetch Fallback Chain

```
EQUITY / ETF:   Upstox V3 LTP → Yahoo Finance → Alpha Vantage
MUTUAL_FUND:    AMFI NAV (NAVAll.txt)
GOLD / SGB:     Gold API → Alpha Vantage GOLDETF → Yahoo GC=F
```

Upstox uses the **Analytics Token** (no rate limits). ISIN resolution: `Symbol` table (refreshed from NSE CSV) → `Holding` table (user-level override).

## Quick Start

### Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+
- Node.js 20+
- Redis 7
- PostgreSQL 16

### 1. Setup Dependencies

```bash
# PostgreSQL (via homebrew)
brew install postgresql@16 && brew services start postgresql@16
createdb networth

# Redis
brew install redis && brew services start redis

# Frontend
cd web && npm install
```

### 2. Environment Variables

```bash
export DB_URL=jdbc:postgresql://localhost:5432/networth
export DB_USERNAME=$(whoami)
export UPSTOX_ANALYTICS_TOKEN=your_upstox_analytics_token
export ALPHA_VANTAGE_API_KEY=your_key  # optional fallback
```

### 3. Run

```bash
# Backend
mvn spring-boot:run   # starts on :8080

# Frontend (separate terminal)
cd web && npm run dev  # starts on :3000, proxies /api → :8080

# AI Server (optional)
bash llama.sh start  # starts llama.cpp on :8081
```

Or use `bash start.sh` to start all services (Redis, llama.cpp, backend, frontend).

## Configuration

| Env Variable | Required | Description |
|-------------|----------|-------------|
| `DB_URL` | Yes | PostgreSQL JDBC URL |
| `DB_USERNAME` | Yes | DB user |
| `DB_PASSWORD` | No | DB password |
| `REDIS_URL` | No | Redis URL (default: `redis://localhost:6379`) |
| `JWT_SECRET` | No | JWT signing key |
| `UPSTOX_ANALYTICS_TOKEN` | No | Upstox token for market data (no rate limits) |
| `ALPHA_VANTAGE_API_KEY` | No | Alpha Vantage key (5 calls/min free tier) |
| `GOOGLE_CLIENT_ID` | No | Gmail OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | No | Gmail OAuth client secret |
| `TOKEN_ENCRYPTION_KEY` | No | AES-256 key (32 chars) for Gmail token encryption |
| `OPENAI_API_KEY` | No | OpenAI key (alternative to local llama.cpp) |

## API Reference

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register user |
| POST | `/api/v1/auth/login` | Login |
| POST | `/api/v1/auth/refresh` | Refresh JWT token |

### Portfolio

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/portfolio/holdings` | All holdings (supports `?view=f` for family) |
| POST | `/api/v1/portfolio/holdings` | Add holding (includes `isin` field) |
| GET | `/api/v1/portfolio/holdings/{id}` | Holding details |
| PUT | `/api/v1/portfolio/holdings/{id}` | Update holding |
| DELETE | `/api/v1/portfolio/holdings/{id}` | Delete holding |
| GET | `/api/v1/portfolio/holdings/{id}/transactions` | Holding transactions |
| GET | `/api/v1/portfolio/holdings/{id}/price-history?days=90` | OHLC price history |
| GET | `/api/v1/portfolio/transactions` | All user transactions |
| POST | `/api/v1/portfolio/transactions` | Add transaction |
| GET | `/api/v1/portfolio/summary` | Portfolio summary |
| POST | `/api/v1/portfolio/refresh-prices` | Refresh all prices |
| GET | `/api/v1/portfolio/investment-over-time?days=365&assetType=` | Investment growth time-series |

### Market Data

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/market/backfill-prices?fromDate=&toDate=` | Backfill OHLC history from Upstox |
| GET | `/api/v1/symbols` | List all symbols |
| POST | `/api/v1/symbols/refresh` | Refresh symbols from NSE CSV + AMFI |

### Net Worth

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/net-worth` | Current net worth (triggers snapshot) |
| GET | `/api/v1/net-worth/history?days=30` | Historical net worth |
| GET | `/api/v1/net-worth/change?days=30` | Change over period |
| GET | `/api/v1/net-worth/breakdown` | Asset breakdown |
| GET | `/api/v1/net-worth/health-score` | Financial health score |

### Salary Slips

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST | `/api/v1/salaries` | List/create salary records |
| PUT/DELETE | `/api/v1/salaries/{id}` | Update/delete salary |
| POST | `/api/v1/salaries/parse-slip` | Upload PDF → AI-parsed salary components |
| GET | `/api/v1/salaries/{id}/document` | Download linked pay slip |

### Gmail Bank Monitoring

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/gmail/auth-url` | Get Gmail OAuth URL |
| GET | `/api/v1/gmail/callback?code=` | OAuth callback |
| GET | `/api/v1/gmail/status` | Connection status |
| POST | `/api/v1/gmail/sync` | Trigger manual sync |
| DELETE | `/api/v1/gmail/disconnect` | Disconnect Gmail |
| GET | `/api/v1/gmail/transactions` | List email transactions |
| POST | `/api/v1/gmail/transactions/{id}/confirm` | Confirm transaction |
| POST | `/api/v1/gmail/transactions/{id}/ignore` | Ignore transaction |

### Bank Accounts

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET/POST | `/api/v1/bank-accounts` | List/create bank accounts |
| PUT/DELETE | `/api/v1/bank-accounts/{id}` | Update/delete account |

### Themes

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/themes` | List themes |
| POST | `/api/v1/themes` | Create custom theme |
| PUT | `/api/v1/themes/{id}` | Update custom theme |
| DELETE | `/api/v1/themes/{id}` | Delete theme |

### Documents

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/documents/upload` | Upload document |
| GET | `/api/v1/documents/holding/{holdingId}` | List holding docs |
| GET | `/api/v1/documents/{id}/download` | Download document |
| DELETE | `/api/v1/documents/{id}` | Delete document |

### AI Chat

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/ai/chat` | Chat with AI (portfolio-aware) |
| GET | `/api/v1/ai/insights` | AI-generated insights |

### Analytics / Tax / Liabilities / Goals / Dividends

See source controllers for full endpoint documentation.

## Database Migrations

Managed by Flyway (`src/main/resources/db/migration/`):

| Version | Description |
|---------|-------------|
| V1 | Core schema (users, holdings, transactions, market_prices) |
| V2 | Performance indexes |
| V3 | Goal mappings, EMI schedule |
| V4 | Demat accounts |
| V5 | Demat → holdings FK |
| V6 | Documents |
| V7 | Symbols |
| V8 | Holding → documents |
| V9 | Themes |
| V10 | Symbol aliases |
| V11 | Day change columns |
| V12 | Family tables |
| V13 | Bank accounts, salaries |
| V14 | Salary components (JSONB) |
| V15 | Salary → documents |
| V16 | Gmail monitoring |
| V17 | Stock price history (OHLC) |
| V18 | ISIN on symbols |

## Key Data Flow

### Price Refresh Cycle

```
User clicks "Refresh Prices" or holding created
  → PriceService.getCurrentPrice(symbol, EQUITY)
    → Redis cache hit? return
    → market_prices DB hit? return
    → UpstoxPriceFetcher.fetchPriceData(symbol)
      → SymbolRepository resolves ISIN
      → GET /v3/market-quote/ltp?instrument_key=NSE_EQ|{ISIN}
      → returns { last_price, cp (prev close) }
    → fallback: YahooFinance → AlphaVantage
  → Redis cache (15 min TTL for EQUITY)
  → market_prices DB (daily row, source=UPSTOX)
  → Update holding currentPrice, dayChange, dayChangePct
```

### Net Worth Snapshot

```
GET /api/v1/net-worth (or first investment-over-time call)
  → NetWorthService.calculateNetWorth(userId)
    → Sum all assets by type (equityValue, debtValue, goldValue)
    → Sum all liabilities
    → Net worth = assets - liabilities
  → NetWorthHistoryService.snapshotNetWorth(userId)
    → Store daily entry in Redis hash: networth:history:{userId}:{date}
    → TTL: 365 days
  → Investment growth chart reads this for "Value" line
```

### Gmail Bank Monitoring

```
Scheduled every 5 min (or manual trigger)
  → GmailSyncService scans 18+ Indian bank email patterns
  → Fetches recent messages via Gmail API
  → EmailParserService extracts amount/balance/account-last4/date
  → Regex for known formats (HDFC, ICICI, SBI, Axis, etc.)
  → llama.cpp AI fallback for unknown formats
  → EmailTransactionService matches to bank accounts (by last-4)
  → Pending transactions shown in Bank Alerts tab user confirms/ignores
  → Confirmed → auto-update bank account balance
```

## Building

```bash
# Backend
mvn compile
mvn test
mvn package -DskipTests

# Frontend
cd web && npm run build

# Full stack
bash start.sh
```

## License

MIT
