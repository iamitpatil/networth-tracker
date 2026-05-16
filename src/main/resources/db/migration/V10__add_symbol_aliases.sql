-- Track ticker symbols across different data sources
CREATE TABLE symbol_aliases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol VARCHAR(50) NOT NULL REFERENCES symbols(symbol) ON DELETE CASCADE,
    source VARCHAR(20) NOT NULL,
    alias VARCHAR(50) NOT NULL,
    UNIQUE(symbol, source)
);

CREATE INDEX idx_symbol_aliases_source ON symbol_aliases(source);
CREATE INDEX idx_symbol_aliases_alias ON symbol_aliases(alias);

-- Add source tracking to symbols
ALTER TABLE symbols ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'NSE';

-- Populate initial aliases: for each NSE equity, add an Alpha Vantage alias (BSE)
INSERT INTO symbol_aliases (symbol, source, alias)
SELECT symbol, 'ALPHA_VANTAGE', REPLACE(symbol, '.NS', '.BSE')
FROM symbols WHERE category = 'EQUITY';
