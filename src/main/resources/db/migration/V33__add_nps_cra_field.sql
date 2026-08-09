-- Add CRA (Central Recordkeeping Agency) field to NPS accounts
ALTER TABLE nps_accounts ADD COLUMN IF NOT EXISTS cra VARCHAR(20);

COMMENT ON COLUMN nps_accounts.cra IS 'CRA: PROTEAN (NSDL), KFINTECH (Karvy), or CAMS';
