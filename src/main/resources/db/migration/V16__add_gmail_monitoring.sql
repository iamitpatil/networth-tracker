CREATE TABLE gmail_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gmail_address VARCHAR(255) NOT NULL,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    token_expiry TIMESTAMP,
    sync_enabled BOOLEAN NOT NULL DEFAULT true,
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_gmail_connections_user ON gmail_connections(user_id);

CREATE TABLE email_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bank_account_id UUID REFERENCES bank_accounts(id) ON DELETE SET NULL,
    gmail_message_id VARCHAR(255) NOT NULL,
    sender VARCHAR(255) NOT NULL,
    subject TEXT,
    body_preview TEXT,
    amount NUMERIC(18,2),
    balance NUMERIC(18,2),
    transaction_type VARCHAR(20),
    transaction_date TIMESTAMP,
    raw_json JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_transactions_user ON email_transactions(user_id);
CREATE INDEX idx_email_transactions_bank ON email_transactions(bank_account_id);
CREATE INDEX idx_email_transactions_status ON email_transactions(status);
CREATE INDEX idx_email_transactions_msg ON email_transactions(gmail_message_id);
