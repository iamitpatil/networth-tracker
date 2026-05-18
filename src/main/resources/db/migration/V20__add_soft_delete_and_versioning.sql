-- V20: Add optimistic locking version columns and soft delete columns
-- This migration adds:
-- 1. version column for @Version optimistic locking on financial entities
-- 2. deleted_at column for soft delete on entities that must be preserved for audit

-- Soft delete on financial entities (replace cascading deletes)
ALTER TABLE holdings ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE holdings ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_holdings_deleted_at ON holdings(deleted_at);

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_transactions_deleted_at ON transactions(deleted_at);

ALTER TABLE liabilities ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE liabilities ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_liabilities_deleted_at ON liabilities(deleted_at);

ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_bank_accounts_deleted_at ON bank_accounts(deleted_at);

ALTER TABLE goals ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE goals ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_goals_deleted_at ON goals(deleted_at);

ALTER TABLE demat_accounts ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE demat_accounts ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_demat_accounts_deleted_at ON demat_accounts(deleted_at);

-- Add updated_at to entities that are missing it
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
ALTER TABLE documents ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
ALTER TABLE goals ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
ALTER TABLE dividends ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
ALTER TABLE tax_records ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
