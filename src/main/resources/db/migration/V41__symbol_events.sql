-- Store corporate-action events per symbol, so they are fetched once rather than on every read.
--
-- Dividends were computed by calling the provider chain once per holding, inside a loop, through
-- MarketDataResolver.firstResult -- which *skips* a rate-limited provider instead of waiting. That is
-- right for the live price path, where falling through to the next provider beats stalling an HTTP
-- thread, and wrong for a batch: nse allows 1/s and 10/min, yahoo 5/s, so a 24-symbol loop drained
-- both per-second buckets in its first 165ms and roughly eighteen holdings had no request issued for
-- them at all. The skip is logged at debug, so the endpoint reported success and wrote nothing.
--
-- One NSE corporate-actions call returns a symbol's entire dividend history -- eighteen to twenty
-- events going back a decade -- so there was never a reason to ask per calculation. Fetched once into
-- this table, dividend calculation becomes a local join with no provider call and no rate limit, and
-- the slow part happens once in a background job step.

CREATE TABLE symbol_events (
    id               BIGSERIAL     PRIMARY KEY,
    symbol           VARCHAR(20)   NOT NULL,
    event_type       VARCHAR(20)   NOT NULL,
    -- Interim / Final / Special. NOT NULL with an empty default rather than nullable, because it is
    -- part of the unique key below and PostgreSQL treats NULLs as distinct: two nullable-subtype rows
    -- for the same symbol and date would both be admitted and ON CONFLICT would never fire, which is
    -- exactly the silent duplication the key exists to prevent.
    event_subtype    VARCHAR(20)   NOT NULL DEFAULT '',
    amount_per_share NUMERIC(18,4),
    -- Bonus or split factor. Null for dividends, which carry an amount instead.
    ratio            NUMERIC(18,8),
    -- Business dates, not instants: an ex-date is a calendar day on the exchange, and V38 established
    -- that such columns are DATE so they cannot shift under a session timezone.
    ex_date          DATE          NOT NULL,
    record_date      DATE,
    payment_date     DATE,
    description      VARCHAR(500),
    source           VARCHAR(20)   NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Subtype is in the key because a single season legitimately carries more than one payout:
    -- HDFCBANK lists 'Dividend - Rs 22 Per Share' and 'Special Dividend - Rs 5 Per Share' days apart,
    -- and ITC pays an interim in February and a final in May. Keying on (symbol, ex_date) alone would
    -- discard one of each pair and understate the year.
    CONSTRAINT uq_symbol_events UNIQUE (symbol, ex_date, event_type, event_subtype)
);

-- Deliberately no foreign key to symbols. A delisted ticker's dividend history has to outlive its
-- removal from the reference list, and symbol_aliases_symbol_fkey already cost a whole refresh batch
-- this month when a JPA-deferred parent flush raced an immediate JdbcTemplate child insert. The index
-- below serves every lookup this table has; the constraint would only add a way to fail.
CREATE INDEX idx_symbol_events_symbol_date ON symbol_events (symbol, ex_date DESC);

COMMENT ON TABLE symbol_events IS
    'Corporate-action events per symbol (dividends, bonuses, splits), fetched once from the provider and reused. Payouts per holding are computed from these into the dividends table.';
COMMENT ON COLUMN symbol_events.event_subtype IS
    'Interim / Final / Special, or empty where the event type has no subtype. Never null: it is part of uq_symbol_events, and a null would defeat the constraint.';
COMMENT ON COLUMN symbol_events.ratio IS
    'Bonus or split factor. Null for dividends, which use amount_per_share.';

-- The fetch-once watermark. Null means never synced, which is what makes the job's work list
-- "symbols that still need it" rather than "all of them" -- the same shape SymbolBootstrapService
-- already uses when it compares a category's max(updated_at) against app.symbols.max-age.
ALTER TABLE symbols ADD COLUMN IF NOT EXISTS events_synced_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN symbols.events_synced_at IS
    'When this symbol''s corporate-action events were last fetched. Null means never; a value older than app.events.max-age is re-synced to pick up newly announced events.';
