-- Add scheme code and units to nps_accounts for live NAV integration via npsnav.in
ALTER TABLE nps_accounts ADD COLUMN IF NOT EXISTS scheme_code VARCHAR(20);
ALTER TABLE nps_accounts ADD COLUMN IF NOT EXISTS units DECIMAL(18,4);
ALTER TABLE nps_accounts ADD COLUMN IF NOT EXISTS nav DECIMAL(18,4);
ALTER TABLE nps_accounts ADD COLUMN IF NOT EXISTS nav_date DATE;

COMMENT ON COLUMN nps_accounts.scheme_code IS 'NPS scheme code from npsnav.in (e.g. SM001003)';
COMMENT ON COLUMN nps_accounts.units IS 'Total units held in this NPS scheme';
COMMENT ON COLUMN nps_accounts.nav IS 'Latest NAV per unit fetched from npsnav.in';
COMMENT ON COLUMN nps_accounts.nav_date IS 'Date of the latest NAV';
