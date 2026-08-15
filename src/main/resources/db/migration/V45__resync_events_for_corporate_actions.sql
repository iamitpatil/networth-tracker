-- Re-open every symbol for an event sync, because the previous one could only see dividends.
--
-- NseProvider's corporate-actions URL carried &subject=Dividend, so bonuses, splits and demergers were
-- discarded from a response that already contained them. Dropping that filter is only half a fix:
-- symbols.events_synced_at makes the sync skip anything already fetched, so every symbol synced under the
-- old URL would keep its dividends and never gain a bonus. The store would look complete and quietly
-- lack exactly the events the reclassifier needs.
--
-- Clearing the watermark is the whole upgrade step. Nothing already stored is lost -- the sync upserts on
-- (symbol, ex_date, event_type, event_subtype) -- so a re-fetch rewrites the dividends it already has and
-- adds the bonuses and splits it did not.
--
-- The cost is one NSE request per symbol again, paced at ten a minute, in a cancellable background step
-- that puts held symbols first. That is the same cost as the first run and it buys the event types without
-- which twelve transactions stay recorded as purchases at zero rupees.

UPDATE symbols
   SET events_synced_at = NULL
 WHERE events_synced_at IS NOT NULL;
