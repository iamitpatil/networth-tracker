-- Support bonus issues, share splits and demergers.
--
-- Two new columns on transactions, both nullable so every existing row is unaffected:
--
--   acquisition_date   Overrides where the holding period starts for the lot this row creates.
--                      Needed for demergers: under s.2(42A) the resulting company's shares
--                      inherit the period the original shares were held, so shares received
--                      yesterday on a five-year-old holding are long-term immediately. Without
--                      this the lot would be dated at the demerger and taxed short-term.
--
--   adjustment_factor  Scales the per-share cost of lots acquired before this row. A 1:2 split
--                      halves each earlier lot's cost per share while doubling its quantity; a
--                      demerger that apportions 15% of cost away leaves the source at 0.85.
--                      Only meaningful for SPLIT and DEMERGER_OUT.

-- WITH TIME ZONE to match transaction_date on the same table; mixing the two would make
-- comparisons between them depend on the session timezone.
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS acquisition_date  TIMESTAMP WITH TIME ZONE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS adjustment_factor NUMERIC(18, 8);

COMMENT ON COLUMN transactions.acquisition_date IS
    'Holding-period start for the lot this row creates, when it differs from transaction_date (demerged shares inherit the original acquisition date, s.2(42A)).';
COMMENT ON COLUMN transactions.adjustment_factor IS
    'Scales per-share cost of earlier lots. 0.5 for a 1:2 split, 0.85 where a demerger apportions 15% of cost away. Only used by SPLIT and DEMERGER_OUT.';
