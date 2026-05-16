ALTER TABLE symbols ADD COLUMN isin VARCHAR(12);
CREATE INDEX idx_symbols_isin ON symbols(isin);
