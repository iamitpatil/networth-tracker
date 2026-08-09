-- Record when a price was last confirmed, not only when its row first appeared.
--
-- market_prices holds one row per (symbol, asset_type, price_date) on purpose: the daily series is
-- what the charts read, so an intraday refresh replaces the day's row rather than adding to it. The
-- consequence is that created_at answers "when did we first see a price for this day", which is the
-- opening quote, and it is mapped updatable = false so it cannot answer anything else.
--
-- Nothing therefore recorded when a number was last confirmed by a provider, and "is this worth
-- re-fetching?" was unanswerable. So nobody asked: the scheduler re-fetched every symbol every 15
-- minutes including at 03:00 when the exchange is shut, while the read path served a row of any age
-- as today's price. PriceFreshnessPolicy needs this column to replace both behaviours with one
-- decision.
--
-- TIMESTAMP WITH TIME ZONE, like created_at has been since V1 and like every instant since V39: this
-- is a moment in time, not a business date.

ALTER TABLE market_prices
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

-- Existing rows were last confirmed no later than their creation. Treating them as confirmed then is
-- the conservative reading -- it can only make a price look older than it is, which prompts one
-- harmless re-fetch rather than serving something stale as current.
UPDATE market_prices
SET updated_at = COALESCE(created_at, NOW());

ALTER TABLE market_prices
    ALTER COLUMN updated_at SET DEFAULT NOW();

-- A price with no confirmation time cannot be reasoned about, and the freshness policy would treat it
-- as never confirmed and re-fetch it on every pass. Better to make it impossible.
ALTER TABLE market_prices
    ALTER COLUMN updated_at SET NOT NULL;
