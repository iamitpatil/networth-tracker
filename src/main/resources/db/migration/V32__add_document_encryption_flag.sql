-- Add encrypted flag to documents table for at-rest encryption support
ALTER TABLE documents ADD COLUMN IF NOT EXISTS encrypted BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN documents.encrypted IS 'Whether the file on disk is AES-256-GCM encrypted';
