-- Make deleting a user possible.
--
-- Eight foreign keys to users were created with the default NO ACTION, so any user who had
-- ever used AI chat, held a credit card, or been recorded in a retirement account could not
-- be deleted at all: the DELETE failed on the first referencing row. That blocks account
-- closure and any right-to-erasure request.
--
-- Ownership columns cascade, matching the convention already used by holdings, goals,
-- transactions and the rest. The one exception is family_members.invited_by, which records
-- who sent an invitation rather than who owns the row -- cascading there would delete a
-- family member because the person who invited them closed their account, so it is set to
-- NULL instead. The column is already nullable.

-- ── owned data: cascade ──────────────────────────────────────────────
ALTER TABLE ai_chat_sessions   DROP CONSTRAINT IF EXISTS ai_chat_sessions_user_id_fkey;
ALTER TABLE ai_chat_sessions   ADD CONSTRAINT ai_chat_sessions_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE ai_pending_actions DROP CONSTRAINT IF EXISTS ai_pending_actions_user_id_fkey;
ALTER TABLE ai_pending_actions ADD CONSTRAINT ai_pending_actions_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE cc_spend_reports   DROP CONSTRAINT IF EXISTS cc_spend_reports_user_id_fkey;
ALTER TABLE cc_spend_reports   ADD CONSTRAINT cc_spend_reports_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE credit_cards       DROP CONSTRAINT IF EXISTS credit_cards_user_id_fkey;
ALTER TABLE credit_cards       ADD CONSTRAINT credit_cards_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE epf_accounts       DROP CONSTRAINT IF EXISTS epf_accounts_user_id_fkey;
ALTER TABLE epf_accounts       ADD CONSTRAINT epf_accounts_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE nps_accounts       DROP CONSTRAINT IF EXISTS nps_accounts_user_id_fkey;
ALTER TABLE nps_accounts       ADD CONSTRAINT nps_accounts_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE ppf_accounts       DROP CONSTRAINT IF EXISTS ppf_accounts_user_id_fkey;
ALTER TABLE ppf_accounts       ADD CONSTRAINT ppf_accounts_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- ── a reference to another person, not ownership: null it ─────────────
ALTER TABLE family_members     DROP CONSTRAINT IF EXISTS family_members_invited_by_fkey;
ALTER TABLE family_members     ADD CONSTRAINT family_members_invited_by_fkey
    FOREIGN KEY (invited_by) REFERENCES users (id) ON DELETE SET NULL;
