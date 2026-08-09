-- Give every instant column a time zone, so an instant is an instant.
--
-- Companion to V38, which took the zone OFF the three business-date columns. This is the other half
-- of the same idea: dates are local and have no zone; instants are absolute and must carry one.
--
-- 25 columns across 14 tables recorded "when did this happen" as TIMESTAMP WITHOUT TIME ZONE, which
-- cannot answer that question on its own -- 09:43 is only meaningful once you know whose 09:43. The
-- application wrote them from a JVM running in UTC, so the values are UTC wall-clock, and they are
-- reinterpreted as such here. Verified before writing this: every affected row predates the brief
-- window in which the JVM ran in IST, so there is no mixed-zone data to disentangle.
--
-- With this applied and the Java fields changed from LocalDateTime to Instant, these values
-- serialise as "2026-08-09T09:43:00Z" instead of a bare "2026-08-09T09:43:00", and a browser
-- renders them in the viewer's own zone. Previously a naive string was read as local time, so a user
-- outside India saw every timestamp shifted by their offset from the server.
--
-- Business dates are deliberately excluded and stay without a zone:
--   transactions.transaction_date, settlement_date, acquisition_date
--   email_transactions.transaction_date


-- ai_chat_sessions
ALTER TABLE ai_chat_sessions
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';
ALTER TABLE ai_chat_sessions
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE
        USING updated_at AT TIME ZONE 'UTC';

-- ai_pending_actions
ALTER TABLE ai_pending_actions
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';
ALTER TABLE ai_pending_actions
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE
        USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE ai_pending_actions
    ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE
        USING expires_at AT TIME ZONE 'UTC';

-- cc_spend_reports
ALTER TABLE cc_spend_reports
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';

-- credit_cards
ALTER TABLE credit_cards
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';
ALTER TABLE credit_cards
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE
        USING updated_at AT TIME ZONE 'UTC';

-- demat_accounts
ALTER TABLE demat_accounts
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';
ALTER TABLE demat_accounts
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE
        USING updated_at AT TIME ZONE 'UTC';

-- documents
ALTER TABLE documents
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';

-- email_transactions
ALTER TABLE email_transactions
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';

-- epf_accounts
ALTER TABLE epf_accounts
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';
ALTER TABLE epf_accounts
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE
        USING updated_at AT TIME ZONE 'UTC';

-- gmail_connections
ALTER TABLE gmail_connections
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';
ALTER TABLE gmail_connections
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE
        USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE gmail_connections
    ALTER COLUMN last_sync_at TYPE TIMESTAMP WITH TIME ZONE
        USING last_sync_at AT TIME ZONE 'UTC';
ALTER TABLE gmail_connections
    ALTER COLUMN token_expiry TYPE TIMESTAMP WITH TIME ZONE
        USING token_expiry AT TIME ZONE 'UTC';

-- nps_accounts
ALTER TABLE nps_accounts
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';
ALTER TABLE nps_accounts
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE
        USING updated_at AT TIME ZONE 'UTC';

-- ppf_accounts
ALTER TABLE ppf_accounts
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';
ALTER TABLE ppf_accounts
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE
        USING updated_at AT TIME ZONE 'UTC';

-- reference_data
ALTER TABLE reference_data
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';

-- symbols
ALTER TABLE symbols
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE
        USING updated_at AT TIME ZONE 'UTC';

-- themes
ALTER TABLE themes
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE
        USING created_at AT TIME ZONE 'UTC';

COMMENT ON COLUMN gmail_connections.token_expiry IS
    'When the stored OAuth token expires. An instant, so it carries a zone; comparing it to now() must not depend on the reader''s.';
