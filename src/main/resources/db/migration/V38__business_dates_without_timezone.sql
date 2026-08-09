-- Store business dates as plain local dates, not as instants.
--
-- transaction_date, settlement_date and acquisition_date are calendar dates on the Indian market.
-- "I bought on 15 January" is a date; it has no timezone, and it does not change because the person
-- reading it is in another one. They were TIMESTAMP WITH TIME ZONE, which forced a zone conversion
-- on every read and write, so the stored instant depended on whoever wrote it:
--
--   a transaction submitted as 2025-01-15T00:00 was stored as 2025-01-14 18:30+00
--
-- The application read it back correctly only because it happened to read in the same zone it wrote
-- in. Anything else -- psql, a report, a BI tool, a second service on UTC -- saw 14 January for a
-- 15 January purchase, and 14 January is in a different quarter.
--
-- TIMESTAMP WITHOUT TIME ZONE stores the value verbatim: no conversion, no dependence on the
-- reader's zone, and no dependence on the server's.
--
-- Instant columns (created_at, updated_at, last_sync_at and the rest) are deliberately untouched.
-- Those genuinely are points in time, they are already correct -- verified as sub-second accurate
-- against the database clock -- and TIMESTAMP WITH TIME ZONE is the right type for them.
--
-- Conversion preserves the wall-clock the application currently reads, which is IST. Nothing the
-- user sees moves and no financial figure changes, because every calculation buckets on the date
-- part. Note the time-of-day on rows written before the server timezone was pinned is not
-- meaningful: they read as 05:30 because a 00:00 date was stored as 00:00 UTC. The dates are
-- correct; the times on legacy rows are an artefact and are preserved rather than guessed at.

ALTER TABLE transactions
    ALTER COLUMN transaction_date TYPE TIMESTAMP WITHOUT TIME ZONE
        USING transaction_date AT TIME ZONE 'Asia/Kolkata';

ALTER TABLE transactions
    ALTER COLUMN settlement_date TYPE TIMESTAMP WITHOUT TIME ZONE
        USING settlement_date AT TIME ZONE 'Asia/Kolkata';

ALTER TABLE transactions
    ALTER COLUMN acquisition_date TYPE TIMESTAMP WITHOUT TIME ZONE
        USING acquisition_date AT TIME ZONE 'Asia/Kolkata';

COMMENT ON COLUMN transactions.transaction_date IS
    'Calendar date of the trade on the Indian market. Local, deliberately without a time zone: a date is not an instant and must not shift with the reader''s zone.';
COMMENT ON COLUMN transactions.acquisition_date IS
    'Local date the holding period starts, when it differs from transaction_date (demerged shares inherit it under s.2(42A)). Without a time zone, as for transaction_date.';
COMMENT ON COLUMN transactions.settlement_date IS
    'Local settlement date. Without a time zone, as for transaction_date.';
