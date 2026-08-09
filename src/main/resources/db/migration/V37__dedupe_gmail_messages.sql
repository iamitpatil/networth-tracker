-- Make it impossible to record the same Gmail message twice.
--
-- GmailSyncService did guard against re-processing, but by loading the user's entire
-- email_transactions table once per message and scanning it in Java. That is O(messages x rows)
-- and, being application-side, it also could not stop two concurrent syncs -- a scheduled run and
-- a manual "Sync now" -- from both deciding a message was new.
--
-- Scoped to the user rather than global: each Gmail connection syncs its own mailbox, and message
-- IDs are only unique within one. A global constraint would make one user's message block another's.

CREATE UNIQUE INDEX IF NOT EXISTS uq_email_transactions_user_message
    ON email_transactions (user_id, gmail_message_id);

-- The old non-unique index is redundant now: this one covers lookups by user_id + message, and
-- idx_email_transactions_user still covers user_id alone.
DROP INDEX IF EXISTS idx_email_transactions_msg;

COMMENT ON INDEX uq_email_transactions_user_message IS
    'Stops a Gmail message being ingested twice for the same user, which would apply its balance change twice on confirmation.';
