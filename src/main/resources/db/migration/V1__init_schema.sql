CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    two_factor_secret VARCHAR(255),
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_login TIMESTAMP WITH TIME ZONE
);

CREATE TABLE holdings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    symbol VARCHAR(100) NOT NULL,
    name VARCHAR(255),
    quantity DECIMAL(18, 8) NOT NULL,
    average_buy_price DECIMAL(18, 4) NOT NULL,
    current_price DECIMAL(18, 4),
    current_value DECIMAL(18, 2),
    realized_pnl DECIMAL(18, 2) DEFAULT 0,
    unrealized_pnl DECIMAL(18, 2),
    currency VARCHAR(3) DEFAULT 'INR',
    exchange VARCHAR(20),
    sector VARCHAR(100),
    isin VARCHAR(12),
    lock_in_date DATE,
    lock_in_until DATE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_holdings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holding_id UUID NOT NULL,
    user_id UUID NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    quantity DECIMAL(18, 8) NOT NULL,
    price DECIMAL(18, 4) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    fees DECIMAL(18, 2) DEFAULT 0,
    taxes DECIMAL(18, 2) DEFAULT 0,
    transaction_date TIMESTAMP WITH TIME ZONE NOT NULL,
    settlement_date TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    broker VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_transactions_holding FOREIGN KEY (holding_id) REFERENCES holdings(id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE market_prices (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(100) NOT NULL,
    asset_type VARCHAR(50) NOT NULL,
    price DECIMAL(18, 4) NOT NULL,
    price_date DATE NOT NULL,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_market_prices UNIQUE (symbol, asset_type, price_date)
);

CREATE TABLE liabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    liability_type VARCHAR(50) NOT NULL,
    lender VARCHAR(100),
    original_amount DECIMAL(18, 2) NOT NULL,
    outstanding_amount DECIMAL(18, 2) NOT NULL,
    interest_rate DECIMAL(5, 2) NOT NULL,
    monthly_emi DECIMAL(18, 2),
    start_date DATE NOT NULL,
    end_date DATE,
    next_emi_date DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_liabilities_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE dividends (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holding_id UUID,
    symbol VARCHAR(100) NOT NULL,
    dividend_amount DECIMAL(18, 2) NOT NULL,
    dividend_type VARCHAR(20),
    record_date DATE,
    ex_date DATE,
    payment_date DATE,
    reinvested BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_dividends_holding FOREIGN KEY (holding_id) REFERENCES holdings(id) ON DELETE SET NULL
);

CREATE TABLE tax_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    financial_year VARCHAR(9) NOT NULL,
    transaction_id UUID,
    holding_id UUID,
    gain_type VARCHAR(20),
    gain_amount DECIMAL(18, 2),
    tax_amount DECIMAL(18, 2),
    section VARCHAR(20),
    is_harvested BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_tax_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_tax_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE SET NULL,
    CONSTRAINT fk_tax_holding FOREIGN KEY (holding_id) REFERENCES holdings(id) ON DELETE SET NULL
);

CREATE TABLE goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    target_amount DECIMAL(18, 2) NOT NULL,
    current_amount DECIMAL(18, 2) DEFAULT 0,
    target_date DATE,
    goal_type VARCHAR(50),
    risk_profile VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE import_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(255),
    status VARCHAR(20) DEFAULT 'pending',
    total_records INT,
    processed_records INT DEFAULT 0,
    failed_records INT DEFAULT 0,
    error_log TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_import_jobs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    old_value JSONB,
    new_value JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);
