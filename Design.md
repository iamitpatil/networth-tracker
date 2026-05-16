# Design Document: Indian Investment & Net-Worth Tracker

## 1. System Overview

A unified personal balance sheet platform for Indian investors that aggregates equity, mutual funds, EPF/NPS/PPF, gold, crypto, real estate, bank balances, and loans into a single dashboard with net-worth analytics and Indian tax-awareness.

---

## 2. Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Flutter Mobile App                        │
│  (iOS/Android) - Single Codebase                            │
└───────────────────────┬─────────────────────────────────────┘
                        │ HTTPS
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    API Gateway (AWS ALB)                     │
│  Rate Limiting │ SSL │ Routing │ CORS                        │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                  Spring Boot Services                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   Auth      │  │  Portfolio  │  │ Market Data │         │
│  │  Service    │  │  Service    │  │  Service    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │  Analytics  │  │    Tax      │  │   Import    │         │
│  │  Service    │  │  Service    │  │  Service    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└───────────────────────┬─────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ PostgreSQL   │ │    Redis     │ │   Kafka/     │
│ (Primary DB) │ │   (Cache)    │ │  RabbitMQ    │
└──────────────┘ └──────────────┘ └──────────────┘
```

### 2.2 Technology Stack

| Layer | Technology | Rationale |
|-------|------------|-----------|
| Mobile | Flutter | Single codebase, great charts, Android priority for India |
| Backend | Java Spring Boot | Developer expertise, enterprise-grade |
| Database | PostgreSQL | Relational integrity, complex queries, JSONB support |
| Cache | Redis | Session management, price caching, rate limiting |
| Queue | Kafka/RabbitMQ | Async market data ingestion, scheduled jobs |
| Charts | ECharts | Rich financial charting |
| Auth | JWT + 2FA | Stateless, secure, mobile-friendly |
| Hosting | AWS | Scalability, compliance |

---

## 3. Database Design

### 3.1 Entity Relationship

```
users (1) ──── (N) holdings
users (1) ──── (N) liabilities
holdings (1) ── (N) transactions
holdings (1) ── (N) market_prices
holdings (1) ── (N) dividends
users (1) ──── (N) tax_records
users (1) ──── (N) goals
goals (1) ──── (N) goal_mappings (N) holdings
users (1) ──── (N) import_jobs
```

### 3.2 Core Tables

```sql
-- Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_login TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT TRUE
);

-- Holdings (All asset types unified)
CREATE TABLE holdings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    asset_type VARCHAR(50) NOT NULL, -- equity, mf, epf, ppf, nps, gold, crypto, realestate, fd, bond, cash
    symbol VARCHAR(100) NOT NULL,
    name VARCHAR(255),
    quantity DECIMAL(18, 8) NOT NULL,
    average_buy_price DECIMAL(18, 4) NOT NULL,
    current_price DECIMAL(18, 4),
    current_value DECIMAL(18, 2),
    realized_pnl DECIMAL(18, 2) DEFAULT 0,
    unrealized_pnl DECIMAL(18, 2),
    currency VARCHAR(3) DEFAULT 'INR',
    exchange VARCHAR(20), -- NSE, BSE, etc.
    sector VARCHAR(100),
    isin VARCHAR(12),
    lock_in_date DATE,
    lock_in_until DATE,
    metadata JSONB, -- Extensible for asset-specific fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Transactions (Unified across all assets)
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holding_id UUID REFERENCES holdings(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    transaction_type VARCHAR(20) NOT NULL, -- buy, sell, dividend, interest, sip, transfer
    quantity DECIMAL(18, 8) NOT NULL,
    price DECIMAL(18, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    fees DECIMAL(18, 2) DEFAULT 0,
    taxes DECIMAL(18, 2) DEFAULT 0,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    settlement_date TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    broker VARCHAR(50), -- zerodha, groww, etc.
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Market Prices (Time Series)
CREATE TABLE market_prices (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(100) NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    price DECIMAL(18, 4) NOT NULL,
    price_date DATE NOT NULL,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(symbol, asset_type, price_date)
);

-- Liabilities
CREATE TABLE liabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    liability_type VARCHAR(50) NOT NULL, -- home_loan, car_loan, education_loan, credit_card, personal_loan
    lender VARCHAR(100),
    original_amount DECIMAL(18, 2) NOT NULL,
    outstanding_amount DECIMAL(18, 2) NOT NULL,
    interest_rate DECIMAL(5, 2) NOT NULL,
    monthly_emi DECIMAL(18, 2),
    start_date DATE NOT NULL,
    end_date DATE,
    next_emi_date DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- EMI Schedule
CREATE TABLE emi_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    liability_id UUID REFERENCES liabilities(id) ON DELETE CASCADE,
    emi_number INT NOT NULL,
    due_date DATE NOT NULL,
    principal_component DECIMAL(18, 2),
    interest_component DECIMAL(18, 2),
    total_emi DECIMAL(18, 2),
    is_paid BOOLEAN DEFAULT FALSE,
    paid_date DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Dividends
CREATE TABLE dividends (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holding_id UUID REFERENCES holdings(id),
    symbol VARCHAR(100) NOT NULL,
    dividend_amount DECIMAL(18, 2) NOT NULL,
    dividend_type VARCHAR(20), -- interim, final, special
    record_date DATE,
    ex_date DATE,
    payment_date DATE,
    reinvested BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Tax Records
CREATE TABLE tax_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    financial_year VARCHAR(9) NOT NULL, -- e.g., 2024-2025
    transaction_id UUID REFERENCES transactions(id),
    holding_id UUID REFERENCES holdings(id),
    gain_type VARCHAR(20), -- ltcg, stcg
    gain_amount DECIMAL(18, 2),
    tax_amount DECIMAL(18, 2),
    section VARCHAR(20), -- 80C, 80D, etc.
    is_harvested BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Goals
CREATE TABLE goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    target_amount DECIMAL(18, 2) NOT NULL,
    current_amount DECIMAL(18, 2) DEFAULT 0,
    target_date DATE,
    goal_type VARCHAR(50), -- retirement, house, education, emergency, fire
    risk_profile VARCHAR(20), -- conservative, moderate, aggressive
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Goal Mappings
CREATE TABLE goal_mappings (
    goal_id UUID REFERENCES goals(id) ON DELETE CASCADE,
    holding_id UUID REFERENCES holdings(id) ON DELETE CASCADE,
    allocation_percentage DECIMAL(5, 2),
    PRIMARY KEY (goal_id, holding_id)
);

-- Import Jobs
CREATE TABLE import_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    source_type VARCHAR(50) NOT NULL, -- zerodha, groww, cams, bank_statement
    file_name VARCHAR(255),
    status VARCHAR(20) DEFAULT 'pending', -- pending, processing, completed, failed
    total_records INT,
    processed_records INT DEFAULT 0,
    failed_records INT DEFAULT 0,
    error_log TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Audit Logs
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    old_value JSONB,
    new_value JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### 3.3 Indexes

```sql
CREATE INDEX idx_holdings_user_type ON holdings(user_id, asset_type);
CREATE INDEX idx_holdings_user_symbol ON holdings(user_id, symbol);
CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date DESC);
CREATE INDEX idx_transactions_holding ON transactions(holding_id, transaction_date DESC);
CREATE INDEX idx_market_prices_symbol_date ON market_prices(symbol, price_date DESC);
CREATE INDEX idx_liabilities_user ON liabilities(user_id);
CREATE INDEX idx_tax_user_fy ON tax_records(user_id, financial_year);
CREATE INDEX idx_goals_user ON goals(user_id);
```

---

## 4. API Design

### 4.1 Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register user |
| POST | `/api/v1/auth/login` | Login |
| POST | `/api/v1/auth/refresh` | Refresh token |
| POST | `/api/v1/auth/2fa/enable` | Enable 2FA |
| POST | `/api/v1/auth/logout` | Logout |

### 4.2 Portfolio

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/portfolio` | All holdings |
| POST | `/api/v1/portfolio/holdings` | Add holding |
| GET | `/api/v1/portfolio/holdings/{id}` | Holding details |
| PUT | `/api/v1/portfolio/holdings/{id}` | Update holding |
| DELETE | `/api/v1/portfolio/holdings/{id}` | Delete holding |
| GET | `/api/v1/portfolio/holdings/{id}/transactions` | Holding transactions |
| POST | `/api/v1/portfolio/transactions` | Add transaction |
| GET | `/api/v1/portfolio/summary` | Portfolio summary |
| GET | `/api/v1/portfolio/allocation` | Asset allocation |

### 4.3 Market Data

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/market/prices/{symbol}` | Current price |
| GET | `/api/v1/market/prices/{symbol}/history` | Historical prices |
| GET | `/api/v1/market/nav/{isin}` | MF NAV |
| GET | `/api/v1/market/nav/{isin}/history` | Historical NAV |

### 4.4 Net Worth

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/net-worth` | Current net worth |
| GET | `/api/v1/net-worth/history` | Historical net worth |
| GET | `/api/v1/net-worth/breakdown` | Detailed breakdown |

### 4.5 Liabilities

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/liabilities` | All liabilities |
| POST | `/api/v1/liabilities` | Add liability |
| GET | `/api/v1/liabilities/{id}/emi-schedule` | EMI schedule |
| POST | `/api/v1/liabilities/{id}/emi-pay` | Mark EMI paid |

### 4.6 Tax

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/tax/summary/{fy}` | Tax summary |
| GET | `/api/v1/tax/capital-gains/{fy}` | Capital gains report |
| GET | `/api/v1/tax/harvesting-opportunities` | Harvest suggestions |
| GET | `/api/v1/tax/80c-utilization` | 80C utilization |

### 4.7 Goals

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/goals` | All goals |
| POST | `/api/v1/goals` | Create goal |
| GET | `/api/v1/goals/{id}/progress` | Goal progress |
| POST | `/api/v1/goals/{id}/map-holding` | Map holding to goal |

### 4.8 Imports

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/import` | Upload file |
| GET | `/api/v1/import/jobs` | Import job status |

---

## 5. Service Design

### 5.1 Portfolio Service

**Responsibilities:** Holdings CRUD, transactions, P&L calculation, cost basis tracking (FIFO/LIFO), corporate actions.

**Key Components:**
- `HoldingManager` - Add/update/delete holdings
- `TransactionManager` - Process transactions, bulk import
- `PnLCalculator` - Realized/unrealized P&L
- `CostBasisCalculator` - FIFO, LIFO, weighted average
- `CorporateActionHandler` - Splits, bonuses, dividends

### 5.2 Market Data Service

**Responsibilities:** Price fetching, caching, normalization, scheduled updates, fallback chain.

**Data Source Priority:**
| Asset | Primary | Secondary | Tertiary |
|-------|---------|-----------|----------|
| Equity (NSE/BSE) | Yahoo Finance | Alpha Vantage | Stooq |
| Mutual Funds | AMFI | - | - |
| Gold | MCX/IBJA | Yahoo Gold ETF | - |
| Crypto | CoinGecko | Binance | - |
| Forex | RBI Reference | Yahoo | - |

**Key Components:**
- `PriceFetcher` - NSE, Yahoo, AMFI, CoinGecko fetchers
- `PriceNormalizer` - Standardize across sources
- `PriceCache` - Redis-backed caching
- `ScheduledPriceUpdater` - Periodic price refresh

### 5.3 Analytics Service

**Responsibilities:** XIRR, CAGR, volatility, asset allocation, risk metrics, sector analysis.

**Key Components:**
- `ReturnCalculator` - XIRR (Newton-Raphson), CAGR, absolute returns
- `RiskAnalyzer` - Volatility, Sharpe ratio, max drawdown, VaR
- `AllocationAnalyzer` - Asset/sector/market-cap/geographic breakdown
- `PerformanceAttributor` - Contribution by asset/sector

### 5.4 Net Worth Service

**Responsibilities:** Aggregate assets/liabilities, net worth history, health score, FIRE tracking.

**Key Components:**
- `AssetAggregator` - Sum by category
- `LiabilityAggregator` - Sum all liabilities
- `HealthScoreCalculator` - Emergency fund ratio, debt-to-asset, savings rate
- `FireTracker` - Corpus calculation, progress estimation

### 5.5 Tax Service

**Responsibilities:** Capital gains (LTCG/STCG), tax harvesting, Section 80C, tax reports.

**Indian Tax Rules (FY 2024-25):**

| Asset | STCG | LTCG |
|-------|------|------|
| Equity | 20% (< 12 months) | 12.5% above Rs. 1.25L (> 12 months) |
| Debt MF | Slab rate (< 24 months) | Slab rate (> 24 months) |
| Gold (Physical) | Slab rate (< 36 months) | 20% w/ indexation (> 36 months) |
| SGB | Slab rate | Exempt at maturity (8 years) |
| Real Estate | Slab rate (< 24 months) | 20% w/ indexation (> 24 months) |
| Crypto | 30% flat | 30% flat |

**Key Components:**
- `CapitalGainsCalculator` - Gain computation with indexation
- `TaxHarvester` - Identify harvesting opportunities
- `DeductionTracker` - 80C, 80D tracking
- `TaxReportGenerator` - Capital gains statements

### 5.6 Import Service

**Responsibilities:** CSV/PDF parsing, format mapping, validation, bulk processing.

**Supported Sources:** Zerodha, Groww, ICICI Direct, CAMS, KFintech, CAS (NSDL/CDSL), Bank statements.

**Key Components:**
- `FileParser` - CSV, PDF, Excel parsers
- `SourceMapper` - Map external formats to internal model
- `Validator` - Duplicate detection, validation
- `BulkProcessor` - Async file processing

---

## 6. Caching Strategy

| Data | TTL | Invalidation |
|------|-----|--------------|
| User sessions | 24h | Logout, password change |
| Market prices (intraday) | 15min | Scheduled refresh |
| NAV (daily) | 24h | New day NAV |
| Portfolio summary | 5min | Transaction change |
| Net worth | 15min | Portfolio/liability change |
| Tax calculations | 1h | Transaction change |

**Redis Key Patterns:**
```
session:{userId}
portfolio:{userId}:summary
market:price:{symbol}
market:nav:{isin}
networth:{userId}
tax:{userId}:{fy}
```

---

## 7. Security Design

| Layer | Measure | Implementation |
|-------|---------|----------------|
| Transport | TLS 1.3 | AWS Certificate Manager |
| Auth | JWT + Refresh Tokens | Access: 15min, Refresh: 24h |
| 2FA | TOTP | Time-based one-time password |
| Password | Bcrypt | Cost factor 12 |
| API | Rate Limiting | Redis-backed, per-user/IP |
| Data at Rest | Encryption | AES-256 (AWS KMS) |
| Audit | Action Logging | All mutations logged |
| Session | Device Binding | Device fingerprint stored |

---

## 8. Key Algorithms

### 8.1 XIRR Calculation (Newton-Raphson)

```java
public double calculateXIRR(List<CashFlow> cashFlows) {
    double guess = 0.1;
    int maxIterations = 100;
    double tolerance = 1e-7;
    
    for (int i = 0; i < maxIterations; i++) {
        double npv = 0;
        double derivative = 0;
        long daysSinceFirst = DAYS.between(cashFlows.get(0).date(), cashFlows.get(i).date());
        
        for (CashFlow cf : cashFlows) {
            double years = DAYS.between(cashFlows.get(0).date(), cf.date()) / 365.0;
            double discount = Math.pow(1 + guess, years);
            npv += cf.amount() / discount;
            derivative -= cf.amount() * years / Math.pow(1 + guess, years + 1);
        }
        
        if (Math.abs(npv) < tolerance) return guess;
        if (derivative == 0) break;
        
        guess = guess - npv / derivative;
    }
    
    return guess;
}
```

### 8.2 FIFO Cost Basis

```java
public LotCost calculateFIFO(List<BuyLot> lots, double sellQuantity) {
    double remaining = sellQuantity;
    double totalCost = 0;
    
    for (BuyLot lot : lots) {
        if (remaining <= 0) break;
        double fromLot = Math.min(remaining, lot.quantity());
        totalCost += fromLot * lot.price();
        remaining -= fromLot;
    }
    
    return new LotCost(totalCost / sellQuantity, sellQuantity);
}
```

### 8.3 Tax Harvesting Logic

```
For each holding with unrealized gains:
  1. Check if holding period approaches 12 months (equity LTCG threshold)
  2. Calculate gain if sold today
  3. Check if total LTCG for FY < Rs. 1,25,000 (exemption limit)
  4. If gain fits within exemption:
     → Suggest: Sell to book gain tax-free, immediately rebuy
  5. If gain exceeds exemption:
     → Suggest: Sell only enough to utilize remaining exemption
```

---

## 9. Transaction Model

### 9.1 Unified Transaction Structure

All financial transactions map to:

```
Transaction {
  id: UUID
  userId: UUID
  holdingId: UUID (nullable for cash)
  type: BUY | SELL | DIVIDEND | INTEREST | SIP | TRANSFER_IN | TRANSFER_OUT | FEE | TAX
  amount: Decimal (signed: negative=outflow, positive=inflow)
  quantity: Decimal
  price: Decimal (per unit)
  fees: Decimal
  taxes: Decimal
  date: DateTime
  currency: String (default INR)
  metadata: JSONB
}
```

### 9.2 Asset Type Actions

| Asset Type | Buy | Sell | Income | Corporate Actions |
|------------|-----|------|--------|-------------------|
| Equity | BUY | SELL | DIVIDEND | SPLIT, BONUS, RIGHTS |
| Mutual Fund | SIP/LUMPSUM | REDEEM | DIVIDEND | None |
| Gold (SGB) | BUY | SELL | INTEREST | Maturity |
| Real Estate | BUY | SELL | RENT | None |
| Crypto | BUY | SELL | AIRDROP | FORK, STAKING |
| FD | DEPOSIT | WITHDRAW | INTEREST | Renewal |
| PPF/EPF | CONTRIBUTION | WITHDRAW | INTEREST | None |
| NPS | CONTRIBUTION | WITHDRAW | None | None |

---

## 10. Scheduled Jobs

| Job | Frequency | Purpose |
|-----|-----------|---------|
| Price Refresh (Equity) | Every 15min (market hours) | Update equity prices |
| NAV Update | Daily (11 PM IST) | Fetch AMFI NAV |
| Gold Price Update | Daily | Update gold prices |
| Crypto Price Update | Every 1 hour | Update crypto prices |
| Net Worth Recalculation | Daily | Recompute net worth |
| SIP Reminders | Daily (8 AM IST) | Send SIP due alerts |
| Tax Harvest Check | Weekly | Identify opportunities |
| EMI Due Reminders | Daily | Send EMI alerts |
| Audit Log Cleanup | Monthly | Archive old logs |

---

## 11. MVP Phasing

### Phase 1 (Weeks 1-4)
- User auth (JWT + email/password)
- Manual portfolio entry (equity + MF)
- Market data ingestion (Yahoo + AMFI)
- Basic net worth dashboard
- CSV import (Zerodha, Groww)

### Phase 2 (Weeks 5-8)
- XIRR/CAGR analytics
- Asset allocation charts
- Tax engine (LTCG/STCG)
- SIP calendar
- Goal tracking

### Phase 3 (Weeks 9-12)
- Liabilities module
- Tax harvesting suggestions
- Advanced analytics (volatility, drawdown)
- Family dashboard
- PDF statement parsing

### Phase 4 (Future)
- Broker API integrations
- Account Aggregator (AA) framework
- AI insights
- Rebalancing suggestions
- US stocks support

---

## 12. Error Handling

| Scenario | Strategy |
|----------|----------|
| Market data API failure | Retry with exponential backoff, fallback to next source |
| Duplicate transactions | Unique constraints + hash-based dedup |
| Invalid CSV format | Validation errors returned, partial import allowed |
| Concurrent updates | Optimistic locking (version column) |
| Price unavailable | Use last known price, flag as stale |

---

## 13. Monitoring & Observability

| Tool | Purpose |
|------|---------|
| Prometheus | Metrics collection |
| Grafana | Dashboards |
| ELK Stack | Log aggregation |
| Sentry | Error tracking |
| AWS CloudWatch | Infrastructure monitoring |

**Key Metrics:**
- API latency (p50, p95, p99)
- Error rates by endpoint
- Market data freshness
- Cache hit rates
- Import job success rate
- Active users

---

## 14. File Structure (Spring Boot)

```
src/main/java/com/networth/
├── config/
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── KafkaConfig.java
│   └── SchedulerConfig.java
├── controller/
│   ├── AuthController.java
│   ├── PortfolioController.java
│   ├── MarketDataController.java
│   ├── NetWorthController.java
│   ├── TaxController.java
│   ├── GoalController.java
│   └── ImportController.java
├── service/
│   ├── portfolio/
│   │   ├── HoldingService.java
│   │   ├── TransactionService.java
│   │   ├── PnLService.java
│   │   └── CostBasisService.java
│   ├── market/
│   │   ├── PriceService.java
│   │   ├── YahooPriceFetcher.java
│   │   ├── AmfiNavFetcher.java
│   │   └── PriceCache.java
│   ├── analytics/
│   │   ├── XIRRCalculator.java
│   │   ├── AllocationService.java
│   │   └── RiskService.java
│   ├── tax/
│   │   ├── CapitalGainsService.java
│   │   ├── TaxHarvestService.java
│   │   └── DeductionService.java
│   └── networth/
│       ├── NetWorthService.java
│       └── HealthScoreService.java
├── repository/
│   ├── UserRepository.java
│   ├── HoldingRepository.java
│   ├── TransactionRepository.java
│   ├── MarketPriceRepository.java
│   ├── LiabilityRepository.java
│   └── TaxRecordRepository.java
├── model/
│   ├── entity/
│   │   ├── User.java
│   │   ├── Holding.java
│   │   ├── Transaction.java
│   │   └── ...
│   └── dto/
│       ├── HoldingDTO.java
│       ├── TransactionDTO.java
│       └── ...
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ResourceNotFoundException.java
│   └── ImportException.java
└── scheduler/
    ├── PriceUpdateScheduler.java
    ├── NavUpdateScheduler.java
    └── ReminderScheduler.java
```

---

## 15. Business Model

| Tier | Features | Price |
|------|----------|-------|
| Free | Portfolio tracking, basic net worth, manual entry, CSV import | Rs. 0 |
| Pro | Tax reports, AI insights, advanced analytics, SIP calendar | Rs. 299/month |
| Family | Multi-member dashboard, advisor view, family net worth | Rs. 599/month |
