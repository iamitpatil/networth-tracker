-- Let a holding's dividends go with it.
--
-- fk_dividends_holding was ON DELETE SET NULL, which was survivable while holdings were only ever
-- soft-deleted -- the constraint never fired. Now that deleting a holding removes the row, SET NULL would
-- leave its dividends behind carrying a null holding_id: invisible to the owner, attributable to nothing,
-- and removable through no part of the UI.
--
-- Not hypothetical. Cleaning up a single test holding during the previous change left exactly thirteen
-- such rows, which had to be found and deleted by hand.
--
-- A dividend is derived per-holding data -- quantity on the record date times the amount per share -- and
-- has no meaning without the holding it was computed for. The events themselves live in symbol_events,
-- keyed by symbol and shared across users, so nothing about the company's history is lost here.
--
-- tax_records deliberately keeps SET NULL. A filed year's records must outlive the position they came
-- from, even at the cost of losing the link back to it.

ALTER TABLE dividends DROP CONSTRAINT IF EXISTS fk_dividends_holding;

ALTER TABLE dividends
    ADD CONSTRAINT fk_dividends_holding
    FOREIGN KEY (holding_id) REFERENCES holdings(id) ON DELETE CASCADE;

-- Any orphans predating this constraint are unreachable data. There is no owner to show them to, since
-- holding_id was the only route from a dividend back to a user.
DELETE FROM dividends WHERE holding_id IS NULL;
