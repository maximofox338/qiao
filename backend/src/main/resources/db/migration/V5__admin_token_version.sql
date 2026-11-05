-- Admin sessions: every admin token carries the token_version it was issued with (JWT claim "tv") and is accepted
-- only while it matches. Changing the password or disabling the admin increments it, which ends every existing
-- session at once (tokens issued before this migration have no claim and count as version 0).
-- Expand-only change: the previous application version ignores the column, so a rollback of the image keeps working.
ALTER TABLE admin_user
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER totp_last_step;
