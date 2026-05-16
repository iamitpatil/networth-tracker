CREATE TABLE stock_price_history (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(100) NOT NULL,
    price_date DATE NOT NULL,
    open DECIMAL(18, 4),
    high DECIMAL(18, 4),
    low DECIMAL(18, 4),
    close DECIMAL(18, 4) NOT NULL,
    volume BIGINT,
    source VARCHAR(50) NOT NULL DEFAULT 'UPSTOX',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_stock_price_date UNIQUE (symbol, price_date)
);

CREATE INDEX idx_stock_price_history_symbol_date ON stock_price_history(symbol, price_date DESC);
CREATE INDEX idx_stock_price_history_date ON stock_price_history(price_date);
