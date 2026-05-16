ALTER TABLE symbols ADD COLUMN scheme_code VARCHAR(10);
CREATE INDEX idx_symbols_scheme_code ON symbols(scheme_code);
