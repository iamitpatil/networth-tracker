ALTER TABLE holdings ADD COLUMN demat_account_id UUID REFERENCES demat_accounts(id) ON DELETE SET NULL;
CREATE INDEX idx_holdings_demat_account ON holdings(demat_account_id);
