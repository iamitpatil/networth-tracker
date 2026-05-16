ALTER TABLE documents ADD COLUMN holding_id UUID REFERENCES holdings(id) ON DELETE CASCADE;
CREATE INDEX idx_documents_holding ON documents(holding_id);
