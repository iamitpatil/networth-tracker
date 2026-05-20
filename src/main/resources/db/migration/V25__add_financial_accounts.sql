-- Credit Cards as first-class accounts (not just liability type)
CREATE TABLE credit_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    card_issuer VARCHAR(100) NOT NULL,
    card_name VARCHAR(100), -- e.g. "Regalia", "Simply Click", "Amazon Pay"
    card_network VARCHAR(20), -- VISA, Mastercard, RuPay, AMEX
    card_last_four VARCHAR(4),
    credit_limit DECIMAL(18,2),
    billing_cycle_day INT, -- day of month (1-28)
    payment_due_day INT, -- day of month
    reward_type VARCHAR(50), -- CASHBACK, POINTS, MILES
    annual_fee DECIMAL(10,2),
    is_active BOOLEAN DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- NPS / PRAN accounts
CREATE TABLE nps_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    pran_number VARCHAR(12) NOT NULL, -- 12-digit PRAN
    fund_manager VARCHAR(100), -- SBI, LIC, HDFC, UTI, Kotak, Birla, ICICI
    scheme_preference VARCHAR(20), -- ACTIVE, AUTO
    tier VARCHAR(10) NOT NULL DEFAULT 'TIER1', -- TIER1, TIER2
    asset_class VARCHAR(5), -- E, C, G, A (Equity, Corporate Bond, Govt Securities, Alternate)
    opening_date DATE,
    employer_name VARCHAR(200),
    current_value DECIMAL(18,2),
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- PPF accounts
CREATE TABLE ppf_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    account_number VARCHAR(20) NOT NULL,
    bank_or_post_office VARCHAR(100) NOT NULL,
    branch VARCHAR(100),
    opening_date DATE,
    maturity_date DATE, -- 15 years from opening
    nominee VARCHAR(100),
    current_balance DECIMAL(18,2),
    current_fy_deposit DECIMAL(18,2) DEFAULT 0, -- max 1.5L per FY
    interest_rate DECIMAL(5,2) DEFAULT 7.1, -- govt set quarterly
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- EPF accounts
CREATE TABLE epf_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    uan_number VARCHAR(12), -- 12-digit Universal Account Number
    pf_number VARCHAR(30), -- Regional PF number
    employer_name VARCHAR(200),
    date_of_joining DATE,
    employee_contribution_rate DECIMAL(5,2) DEFAULT 12.0,
    employer_contribution_rate DECIMAL(5,2) DEFAULT 12.0,
    current_balance DECIMAL(18,2),
    basic_salary DECIMAL(18,2), -- for contribution calculation
    is_active BOOLEAN DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_credit_cards_user ON credit_cards(user_id);
CREATE INDEX idx_nps_accounts_user ON nps_accounts(user_id);
CREATE INDEX idx_ppf_accounts_user ON ppf_accounts(user_id);
CREATE INDEX idx_epf_accounts_user ON epf_accounts(user_id);
