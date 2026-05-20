-- Credit card spend reports - stores parsed bill data for monthly tracking
CREATE TABLE cc_spend_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    card_issuer VARCHAR(100),
    card_last_four VARCHAR(4),
    card_type VARCHAR(20),
    statement_month VARCHAR(7) NOT NULL, -- YYYY-MM format
    statement_date DATE,
    due_date DATE,
    total_amount_due DECIMAL(18,2),
    minimum_amount_due DECIMAL(18,2),
    previous_balance DECIMAL(18,2),
    payments_received DECIMAL(18,2),
    new_charges DECIMAL(18,2),
    spend_summary JSONB, -- {"FOOD": 5200, "SHOPPING": 12000, ...}
    transactions JSONB, -- [{date, description, amount, category}, ...]
    document_id UUID,
    -- Payment tracking
    paid BOOLEAN DEFAULT FALSE,
    paid_amount DECIMAL(18,2),
    paid_date DATE,
    payment_mode VARCHAR(50), -- UPI, NEFT, AUTO_DEBIT, ONLINE, OTHER
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, card_last_four, statement_month)
);

CREATE INDEX idx_cc_spend_user ON cc_spend_reports(user_id);
CREATE INDEX idx_cc_spend_month ON cc_spend_reports(user_id, statement_month);
