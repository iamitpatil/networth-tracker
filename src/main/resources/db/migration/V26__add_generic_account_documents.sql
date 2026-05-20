-- Generic account-to-document linking (replaces per-entity FK columns approach)
-- Allows any account type to have supporting documents
ALTER TABLE documents ADD COLUMN account_type VARCHAR(50);
ALTER TABLE documents ADD COLUMN account_id UUID;

CREATE INDEX idx_documents_account ON documents(account_type, account_id);

-- Backfill existing FK relationships into the generic columns
UPDATE documents SET account_type = 'DEMAT', account_id = demat_account_id WHERE demat_account_id IS NOT NULL;
UPDATE documents SET account_type = 'HOLDING', account_id = holding_id WHERE holding_id IS NOT NULL AND account_type IS NULL;
UPDATE documents SET account_type = 'SALARY', account_id = salary_id WHERE salary_id IS NOT NULL AND account_type IS NULL;
UPDATE documents SET account_type = 'BANK', account_id = bank_account_id WHERE bank_account_id IS NOT NULL AND account_type IS NULL;
