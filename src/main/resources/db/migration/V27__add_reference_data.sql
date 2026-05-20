-- Reference data for dropdowns — single table with category + value + metadata
-- Categories: BANK, BROKER, CARD_ISSUER, CARD_NETWORK, NPS_FUND_MANAGER, LOAN_LENDER,
--             BANK_ACCOUNT_TYPE, DEMAT_ACCOUNT_TYPE, PAYMENT_MODE, NPS_TIER, NPS_ASSET_CLASS, etc.
CREATE TABLE reference_data (
    id SERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    value VARCHAR(200) NOT NULL,
    label VARCHAR(200), -- display label (if different from value)
    metadata JSONB, -- extra info like {type: "PUBLIC", rbi_category: "Nationalised"}
    sort_order INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(category, value)
);

CREATE INDEX idx_ref_data_category ON reference_data(category, is_active);

-- ══════════════════════════════════════════════════════════════
-- BANKS (from RBI list: Public Sector + Private Sector + SFBs + Payment Banks + Major Foreign)
-- ══════════════════════════════════════════════════════════════

-- Public Sector Banks (12)
INSERT INTO reference_data (category, value, label, metadata, sort_order) VALUES
('BANK', 'State Bank of India', NULL, '{"type":"PUBLIC"}', 1),
('BANK', 'Bank of Baroda', NULL, '{"type":"PUBLIC"}', 2),
('BANK', 'Bank of India', NULL, '{"type":"PUBLIC"}', 3),
('BANK', 'Bank of Maharashtra', NULL, '{"type":"PUBLIC"}', 4),
('BANK', 'Canara Bank', NULL, '{"type":"PUBLIC"}', 5),
('BANK', 'Central Bank of India', NULL, '{"type":"PUBLIC"}', 6),
('BANK', 'Indian Bank', NULL, '{"type":"PUBLIC"}', 7),
('BANK', 'Indian Overseas Bank', NULL, '{"type":"PUBLIC"}', 8),
('BANK', 'Punjab & Sind Bank', NULL, '{"type":"PUBLIC"}', 9),
('BANK', 'Punjab National Bank', NULL, '{"type":"PUBLIC"}', 10),
('BANK', 'UCO Bank', NULL, '{"type":"PUBLIC"}', 11),
('BANK', 'Union Bank of India', NULL, '{"type":"PUBLIC"}', 12);

-- Private Sector Banks (21)
INSERT INTO reference_data (category, value, label, metadata, sort_order) VALUES
('BANK', 'Axis Bank', NULL, '{"type":"PRIVATE"}', 20),
('BANK', 'Bandhan Bank', NULL, '{"type":"PRIVATE"}', 21),
('BANK', 'CSB Bank', NULL, '{"type":"PRIVATE"}', 22),
('BANK', 'City Union Bank', NULL, '{"type":"PRIVATE"}', 23),
('BANK', 'DCB Bank', NULL, '{"type":"PRIVATE"}', 24),
('BANK', 'Dhanlaxmi Bank', NULL, '{"type":"PRIVATE"}', 25),
('BANK', 'Federal Bank', NULL, '{"type":"PRIVATE"}', 26),
('BANK', 'HDFC Bank', NULL, '{"type":"PRIVATE"}', 27),
('BANK', 'ICICI Bank', NULL, '{"type":"PRIVATE"}', 28),
('BANK', 'IndusInd Bank', NULL, '{"type":"PRIVATE"}', 29),
('BANK', 'IDFC First Bank', NULL, '{"type":"PRIVATE"}', 30),
('BANK', 'Jammu & Kashmir Bank', NULL, '{"type":"PRIVATE"}', 31),
('BANK', 'Karnataka Bank', NULL, '{"type":"PRIVATE"}', 32),
('BANK', 'Karur Vysya Bank', NULL, '{"type":"PRIVATE"}', 33),
('BANK', 'Kotak Mahindra Bank', NULL, '{"type":"PRIVATE"}', 34),
('BANK', 'Nainital Bank', NULL, '{"type":"PRIVATE"}', 35),
('BANK', 'RBL Bank', NULL, '{"type":"PRIVATE"}', 36),
('BANK', 'South Indian Bank', NULL, '{"type":"PRIVATE"}', 37),
('BANK', 'Tamilnad Mercantile Bank', NULL, '{"type":"PRIVATE"}', 38),
('BANK', 'Yes Bank', NULL, '{"type":"PRIVATE"}', 39),
('BANK', 'IDBI Bank', NULL, '{"type":"PRIVATE"}', 40);

-- Small Finance Banks (11)
INSERT INTO reference_data (category, value, label, metadata, sort_order) VALUES
('BANK', 'AU Small Finance Bank', NULL, '{"type":"SFB"}', 50),
('BANK', 'Capital Small Finance Bank', NULL, '{"type":"SFB"}', 51),
('BANK', 'Equitas Small Finance Bank', NULL, '{"type":"SFB"}', 52),
('BANK', 'ESAF Small Finance Bank', NULL, '{"type":"SFB"}', 53),
('BANK', 'Suryoday Small Finance Bank', NULL, '{"type":"SFB"}', 54),
('BANK', 'Ujjivan Small Finance Bank', NULL, '{"type":"SFB"}', 55),
('BANK', 'Utkarsh Small Finance Bank', NULL, '{"type":"SFB"}', 56),
('BANK', 'Slice Small Finance Bank', NULL, '{"type":"SFB"}', 57),
('BANK', 'Jana Small Finance Bank', NULL, '{"type":"SFB"}', 58),
('BANK', 'Shivalik Small Finance Bank', NULL, '{"type":"SFB"}', 59),
('BANK', 'Unity Small Finance Bank', NULL, '{"type":"SFB"}', 60);

-- Payments Banks (6)
INSERT INTO reference_data (category, value, label, metadata, sort_order) VALUES
('BANK', 'Airtel Payments Bank', NULL, '{"type":"PB"}', 70),
('BANK', 'India Post Payments Bank', NULL, '{"type":"PB"}', 71),
('BANK', 'Fino Payments Bank', NULL, '{"type":"PB"}', 72),
('BANK', 'Paytm Payments Bank', NULL, '{"type":"PB"}', 73),
('BANK', 'Jio Payments Bank', NULL, '{"type":"PB"}', 74),
('BANK', 'NSDL Payments Bank', NULL, '{"type":"PB"}', 75);

-- Major Foreign Banks in India
INSERT INTO reference_data (category, value, label, metadata, sort_order) VALUES
('BANK', 'Standard Chartered Bank', NULL, '{"type":"FOREIGN"}', 80),
('BANK', 'HSBC', 'Hong Kong and Shanghai Banking Corporation', '{"type":"FOREIGN"}', 81),
('BANK', 'Citibank', NULL, '{"type":"FOREIGN"}', 82),
('BANK', 'Deutsche Bank', NULL, '{"type":"FOREIGN"}', 83),
('BANK', 'DBS Bank India', NULL, '{"type":"FOREIGN"}', 84),
('BANK', 'BNP Paribas', NULL, '{"type":"FOREIGN"}', 85),
('BANK', 'Barclays Bank', NULL, '{"type":"FOREIGN"}', 86),
('BANK', 'Bank of America', NULL, '{"type":"FOREIGN"}', 87),
('BANK', 'J.P. Morgan Chase Bank', NULL, '{"type":"FOREIGN"}', 88),
('BANK', 'SBM Bank India', NULL, '{"type":"FOREIGN"}', 89);

-- India Post (for PPF)
INSERT INTO reference_data (category, value, label, metadata, sort_order) VALUES
('BANK', 'India Post', 'India Post (Post Office)', '{"type":"POST_OFFICE"}', 95);

-- ══════════════════════════════════════════════════════════════
-- BROKERS (SEBI registered stockbrokers)
-- ══════════════════════════════════════════════════════════════
INSERT INTO reference_data (category, value, sort_order) VALUES
('BROKER', 'Zerodha', 1),
('BROKER', 'Groww', 2),
('BROKER', 'Angel One', 3),
('BROKER', 'Upstox', 4),
('BROKER', 'ICICI Direct', 5),
('BROKER', 'HDFC Securities', 6),
('BROKER', 'Kotak Securities', 7),
('BROKER', 'Axis Direct', 8),
('BROKER', '5Paisa', 9),
('BROKER', 'Motilal Oswal', 10),
('BROKER', 'Sharekhan', 11),
('BROKER', 'Paytm Money', 12),
('BROKER', 'Dhan', 13),
('BROKER', 'INDmoney', 14),
('BROKER', 'IIFL Securities', 15),
('BROKER', 'SBI Securities', 16),
('BROKER', 'Geojit', 17),
('BROKER', 'Edelweiss', 18),
('BROKER', 'Nirmal Bang', 19),
('BROKER', 'Religare', 20);

-- ══════════════════════════════════════════════════════════════
-- CREDIT CARD ISSUERS
-- ══════════════════════════════════════════════════════════════
INSERT INTO reference_data (category, value, sort_order) VALUES
('CARD_ISSUER', 'HDFC Bank', 1),
('CARD_ISSUER', 'ICICI Bank', 2),
('CARD_ISSUER', 'SBI Card', 3),
('CARD_ISSUER', 'Axis Bank', 4),
('CARD_ISSUER', 'Kotak Mahindra Bank', 5),
('CARD_ISSUER', 'IDFC First Bank', 6),
('CARD_ISSUER', 'IndusInd Bank', 7),
('CARD_ISSUER', 'RBL Bank', 8),
('CARD_ISSUER', 'Yes Bank', 9),
('CARD_ISSUER', 'American Express', 10),
('CARD_ISSUER', 'Citibank', 11),
('CARD_ISSUER', 'Standard Chartered', 12),
('CARD_ISSUER', 'HSBC', 13),
('CARD_ISSUER', 'AU Small Finance Bank', 14),
('CARD_ISSUER', 'Federal Bank', 15),
('CARD_ISSUER', 'Bank of Baroda', 16);

-- ══════════════════════════════════════════════════════════════
-- CARD NETWORKS
-- ══════════════════════════════════════════════════════════════
INSERT INTO reference_data (category, value, sort_order) VALUES
('CARD_NETWORK', 'VISA', 1),
('CARD_NETWORK', 'Mastercard', 2),
('CARD_NETWORK', 'RuPay', 3),
('CARD_NETWORK', 'AMEX', 4),
('CARD_NETWORK', 'Diners Club', 5);

-- ══════════════════════════════════════════════════════════════
-- NPS FUND MANAGERS (PFRDA registered)
-- ══════════════════════════════════════════════════════════════
INSERT INTO reference_data (category, value, sort_order) VALUES
('NPS_FUND_MANAGER', 'SBI Pension Fund', 1),
('NPS_FUND_MANAGER', 'LIC Pension Fund', 2),
('NPS_FUND_MANAGER', 'HDFC Pension Management', 3),
('NPS_FUND_MANAGER', 'UTI Retirement Solutions', 4),
('NPS_FUND_MANAGER', 'Kotak Mahindra Pension Fund', 5),
('NPS_FUND_MANAGER', 'Aditya Birla Sun Life Pension', 6),
('NPS_FUND_MANAGER', 'ICICI Prudential Pension Fund', 7),
('NPS_FUND_MANAGER', 'Tata Pension Management', 8),
('NPS_FUND_MANAGER', 'Max Life Pension Fund', 9),
('NPS_FUND_MANAGER', 'DSP Pension Fund', 10);

-- ══════════════════════════════════════════════════════════════
-- LOAN LENDERS (banks + NBFCs)
-- ══════════════════════════════════════════════════════════════
INSERT INTO reference_data (category, value, metadata, sort_order) VALUES
('LOAN_LENDER', 'State Bank of India', '{"type":"BANK"}', 1),
('LOAN_LENDER', 'HDFC Ltd', '{"type":"NBFC"}', 2),
('LOAN_LENDER', 'HDFC Bank', '{"type":"BANK"}', 3),
('LOAN_LENDER', 'ICICI Bank', '{"type":"BANK"}', 4),
('LOAN_LENDER', 'Axis Bank', '{"type":"BANK"}', 5),
('LOAN_LENDER', 'Bank of Baroda', '{"type":"BANK"}', 6),
('LOAN_LENDER', 'Punjab National Bank', '{"type":"BANK"}', 7),
('LOAN_LENDER', 'Kotak Mahindra Bank', '{"type":"BANK"}', 8),
('LOAN_LENDER', 'LIC Housing Finance', '{"type":"NBFC"}', 9),
('LOAN_LENDER', 'Bajaj Finserv', '{"type":"NBFC"}', 10),
('LOAN_LENDER', 'Tata Capital', '{"type":"NBFC"}', 11),
('LOAN_LENDER', 'Muthoot Finance', '{"type":"NBFC"}', 12),
('LOAN_LENDER', 'Manappuram Finance', '{"type":"NBFC"}', 13),
('LOAN_LENDER', 'L&T Finance', '{"type":"NBFC"}', 14),
('LOAN_LENDER', 'Piramal Capital', '{"type":"NBFC"}', 15),
('LOAN_LENDER', 'IIFL Finance', '{"type":"NBFC"}', 16),
('LOAN_LENDER', 'Mahindra Finance', '{"type":"NBFC"}', 17),
('LOAN_LENDER', 'Shriram Finance', '{"type":"NBFC"}', 18),
('LOAN_LENDER', 'Fullerton India', '{"type":"NBFC"}', 19),
('LOAN_LENDER', 'Home First Finance', '{"type":"NBFC"}', 20);

-- ══════════════════════════════════════════════════════════════
-- ENUM-LIKE CATEGORIES
-- ══════════════════════════════════════════════════════════════

-- Bank Account Types
INSERT INTO reference_data (category, value, label, sort_order) VALUES
('BANK_ACCOUNT_TYPE', 'SAVINGS', 'Savings', 1),
('BANK_ACCOUNT_TYPE', 'CURRENT', 'Current', 2),
('BANK_ACCOUNT_TYPE', 'SALARY', 'Salary Account', 3),
('BANK_ACCOUNT_TYPE', 'FD', 'Fixed Deposit', 4),
('BANK_ACCOUNT_TYPE', 'RD', 'Recurring Deposit', 5),
('BANK_ACCOUNT_TYPE', 'NRE', 'NRE (Non-Resident)', 6),
('BANK_ACCOUNT_TYPE', 'NRO', 'NRO (Non-Resident Ordinary)', 7);

-- Demat Account Types
INSERT INTO reference_data (category, value, label, sort_order) VALUES
('DEMAT_ACCOUNT_TYPE', 'Equity', 'Equity', 1),
('DEMAT_ACCOUNT_TYPE', 'Commodity', 'Commodity', 2),
('DEMAT_ACCOUNT_TYPE', 'Derivatives', 'Derivatives', 3),
('DEMAT_ACCOUNT_TYPE', 'Mutual Funds', 'Mutual Funds', 4);

-- Card Reward Types
INSERT INTO reference_data (category, value, label, sort_order) VALUES
('CARD_REWARD_TYPE', 'CASHBACK', 'Cashback', 1),
('CARD_REWARD_TYPE', 'POINTS', 'Reward Points', 2),
('CARD_REWARD_TYPE', 'MILES', 'Air Miles', 3),
('CARD_REWARD_TYPE', 'NONE', 'No Rewards', 4);

-- NPS Tiers
INSERT INTO reference_data (category, value, label, sort_order) VALUES
('NPS_TIER', 'TIER1', 'Tier I (Retirement)', 1),
('NPS_TIER', 'TIER2', 'Tier II (Savings)', 2);

-- NPS Asset Classes
INSERT INTO reference_data (category, value, label, sort_order) VALUES
('NPS_ASSET_CLASS', 'E', 'E — Equity (up to 75%)', 1),
('NPS_ASSET_CLASS', 'C', 'C — Corporate Bonds', 2),
('NPS_ASSET_CLASS', 'G', 'G — Government Securities', 3),
('NPS_ASSET_CLASS', 'A', 'A — Alternate Assets', 4);

-- NPS Scheme Preferences
INSERT INTO reference_data (category, value, label, sort_order) VALUES
('NPS_SCHEME', 'ACTIVE', 'Active Choice (self-select)', 1),
('NPS_SCHEME', 'AUTO', 'Auto Choice (lifecycle-based)', 2);

-- Payment Modes
INSERT INTO reference_data (category, value, label, sort_order) VALUES
('PAYMENT_MODE', 'UPI', 'UPI', 1),
('PAYMENT_MODE', 'NEFT', 'NEFT', 2),
('PAYMENT_MODE', 'RTGS', 'RTGS', 3),
('PAYMENT_MODE', 'IMPS', 'IMPS', 4),
('PAYMENT_MODE', 'AUTO_DEBIT', 'Auto Debit / ECS / NACH', 5),
('PAYMENT_MODE', 'CHEQUE', 'Cheque', 6),
('PAYMENT_MODE', 'ONLINE', 'Net Banking', 7),
('PAYMENT_MODE', 'CASH', 'Cash', 8);

-- Liability Types
INSERT INTO reference_data (category, value, label, sort_order) VALUES
('LIABILITY_TYPE', 'home_loan', 'Home Loan', 1),
('LIABILITY_TYPE', 'car_loan', 'Car Loan', 2),
('LIABILITY_TYPE', 'education_loan', 'Education Loan', 3),
('LIABILITY_TYPE', 'personal_loan', 'Personal Loan', 4),
('LIABILITY_TYPE', 'credit_card', 'Credit Card', 5),
('LIABILITY_TYPE', 'gold_loan', 'Gold Loan', 6),
('LIABILITY_TYPE', 'business_loan', 'Business Loan', 7),
('LIABILITY_TYPE', 'overdraft', 'Overdraft', 8);
