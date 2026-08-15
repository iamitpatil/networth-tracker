-- Mark the transactions of holdings that were already deleted.
--
-- Deleting a holding used to soft-delete only the holding row. Its transactions were left untouched and
-- fully visible, because transactions.deleted_at -- a column with its own index since V20 -- had no
-- writer and, just as importantly, no reader: TransactionRepository never referenced it and
-- getUserTransactions called a bare findByUserId. So 1,638 of 1,894 rows belonged to positions the owner
-- had already removed, mostly NPS schemes imported several times over, and every one still showed in the
-- ledger as if it were live.
--
-- Holdings deleted from now on are removed physically and their transactions go with them through
-- fk_transactions_holding's CASCADE. This migration only settles the rows that predate that change: the
-- holdings stay soft-deleted, and their transactions are marked so they stop appearing.
--
-- Stamped with the holding's own deleted_at rather than now(), so the ledger records when the position
-- actually went away instead of when this migration happened to run.

UPDATE transactions t
   SET deleted_at = h.deleted_at
  FROM holdings h
 WHERE h.id = t.holding_id
   AND h.deleted_at IS NOT NULL
   AND t.deleted_at IS NULL;
