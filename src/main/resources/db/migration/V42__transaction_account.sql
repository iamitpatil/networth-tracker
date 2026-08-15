-- Give a transaction its own account, and make the requirement a database rule.
--
-- transactions carried user_id and a free-text broker but no demat_account_id at all, so a trade could
-- only be attributed to an account indirectly, through its holding. That works while one holding means
-- one account, and it leaves a ledger row unable to say where it happened.
--
-- The rule itself was already honoured everywhere: HoldingService rejects a missing demat for EQUITY,
-- TransactionImportService assigns a default on import, and updateHolding never touches the field so it
-- cannot be cleared. What was missing is a guarantee -- nothing stopped a future code path or a manual
-- insert from creating an equity holding with no account.

ALTER TABLE transactions
    ADD COLUMN demat_account_id UUID REFERENCES demat_accounts(id) ON DELETE SET NULL;

-- SET NULL, not CASCADE: closing a demat account must not erase the trades made through it. The
-- holding keeps the authoritative link; this column is for attribution and reporting.
COMMENT ON COLUMN transactions.demat_account_id IS
    'Account this trade went through, stamped from the holding when the transaction is created. Null for asset types that have no demat account (NPS, PPF, FD, cash, property).';

-- Backfill from the holding, which is where the value has effectively lived all along.
UPDATE transactions t
   SET demat_account_id = h.demat_account_id
  FROM holdings h
 WHERE h.id = t.holding_id
   AND h.demat_account_id IS NOT NULL;

CREATE INDEX idx_transactions_demat ON transactions (demat_account_id);

-- The guarantee goes on holdings rather than transactions, because asset_type lives here: a CHECK on
-- transactions cannot see which types require an account. With this in place a transaction's account is
-- non-null by construction for exactly the types that need one.
--
-- NPS, PPF, EPF, FD, cash and property legitimately have no demat account and are deliberately not
-- listed. Verified against live data before adding: zero rows violate this.
ALTER TABLE holdings
    ADD CONSTRAINT chk_holdings_demat_required
    CHECK (asset_type NOT IN ('EQUITY', 'ETF', 'MUTUAL_FUND') OR demat_account_id IS NOT NULL);
