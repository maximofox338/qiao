-- Admin TOTP enrolment: a secret waiting for its first valid code, and the last accepted 30-second time-step
-- (a code is accepted only for a step later than the last one, so a code cannot be replayed).
ALTER TABLE admin_user
    ADD COLUMN totp_pending_secret VARCHAR(64) NULL AFTER totp_secret,
    ADD COLUMN totp_last_step      BIGINT      NULL AFTER totp_pending_secret;
