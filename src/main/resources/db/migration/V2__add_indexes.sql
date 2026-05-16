CREATE INDEX idx_holdings_user_type ON holdings(user_id, asset_type);
CREATE INDEX idx_holdings_user_symbol ON holdings(user_id, symbol);
CREATE INDEX idx_transactions_user_date ON transactions(user_id, transaction_date DESC);
CREATE INDEX idx_transactions_holding ON transactions(holding_id, transaction_date DESC);
CREATE INDEX idx_market_prices_symbol_date ON market_prices(symbol, price_date DESC);
CREATE INDEX idx_liabilities_user ON liabilities(user_id);
CREATE INDEX idx_tax_user_fy ON tax_records(user_id, financial_year);
CREATE INDEX idx_goals_user ON goals(user_id);
CREATE INDEX idx_import_jobs_user ON import_jobs(user_id, created_at DESC);
