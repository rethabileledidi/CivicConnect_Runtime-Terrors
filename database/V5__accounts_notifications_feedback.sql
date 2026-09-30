-- =============================================================================
-- CivicConnect | V5 - Accounts, notifications, simulated messaging, feedback
-- Owner: Person 2 (Backend: security, request lifecycle, notifications)
-- Reviewer: Person 3 (Database) - schema changes need the database owner's approval
-- Run as civic_owner AFTER V1..V3:
--   psql -h localhost -U civic_owner -d civicconnect -f V5__accounts_notifications_feedback.sql
--
-- Design notes (PED v2.0: ADR-05 security, ADR-06 notifications, CR-03):
--  * Login lockout state lives on app_user so it survives a server restart.
--  * Notifications and outbox rows are written AFTER the status-change transaction
--    commits (Observer). They never block or undo a status change (ADR-P3-02).
--  * SMS / WhatsApp are SIMULATED: the outbox is "sent" by a simulated channel.
--    A real provider later only replaces the channel class, not this table.
--  * The app role (civic_app) inherits SELECT/INSERT/UPDATE on these tables from the
--    default privileges set in V0, and still has no DELETE.
-- =============================================================================

SET search_path = civic;

-- -----------------------------------------------------------------------------
-- 1. Account security columns (NIST SP 800-63B-4 / OWASP authentication guidance)
-- -----------------------------------------------------------------------------
ALTER TABLE app_user
    ADD COLUMN municipality       VARCHAR(100),
    ADD COLUMN failed_login_count SMALLINT    NOT NULL DEFAULT 0
        CONSTRAINT ck_user_failed_logins CHECK (failed_login_count >= 0),
    ADD COLUMN locked_until       TIMESTAMPTZ,
    ADD COLUMN last_login_at      TIMESTAMPTZ;

COMMENT ON COLUMN app_user.password_hash IS
    'PBKDF2-HMAC-SHA256 in the form pbkdf2_sha256$<iterations>$<salt b64>$<hash b64>. NULL = cannot sign in.';

-- -----------------------------------------------------------------------------
-- 2. Contact number captured on the request form (used for simulated SMS/WhatsApp)
-- -----------------------------------------------------------------------------
ALTER TABLE service_request
    ADD COLUMN contact_phone VARCHAR(20)
        CONSTRAINT ck_request_contact_phone CHECK (contact_phone IS NULL OR contact_phone ~ '^0[0-9]{9}$');

-- -----------------------------------------------------------------------------
-- 3. In-app notifications (FR-13)
-- -----------------------------------------------------------------------------
CREATE TABLE notification (
    notification_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_user (user_id),
    request_id      BIGINT       REFERENCES service_request (request_id),
    title           VARCHAR(150) NOT NULL,
    body            VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at         TIMESTAMPTZ
);
CREATE INDEX ix_notification_user_time ON notification (user_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- 4. Outbox for simulated SMS / WhatsApp (CR-03)
-- -----------------------------------------------------------------------------
CREATE TABLE outbox_message (
    message_id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id  BIGINT       NOT NULL REFERENCES service_request (request_id),
    channel     VARCHAR(10)  NOT NULL CONSTRAINT ck_outbox_channel CHECK (channel IN ('SMS', 'WHATSAPP')),
    recipient   VARCHAR(20)  NOT NULL,
    body        VARCHAR(500) NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts    SMALLINT     NOT NULL DEFAULT 0 CONSTRAINT ck_outbox_attempts CHECK (attempts BETWEEN 0 AND 3),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ,
    last_error  VARCHAR(255),
    CONSTRAINT ck_outbox_sent_time CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
);
CREATE INDEX ix_outbox_pending ON outbox_message (created_at) WHERE status = 'PENDING';
CREATE INDEX ix_outbox_request ON outbox_message (request_id, created_at);

-- -----------------------------------------------------------------------------
-- 5. Requester feedback on a resolved request (one rating per request)
-- -----------------------------------------------------------------------------
CREATE TABLE request_feedback (
    request_id   BIGINT        PRIMARY KEY REFERENCES service_request (request_id),
    rating       SMALLINT      NOT NULL CONSTRAINT ck_feedback_rating CHECK (rating BETWEEN 1 AND 5),
    comment      VARCHAR(1000),
    submitted_by BIGINT        NOT NULL REFERENCES app_user (user_id),
    submitted_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

COMMENT ON TABLE notification     IS 'In-app notifications, written by an Observer after a status change commits.';
COMMENT ON TABLE outbox_message   IS 'Simulated SMS/WhatsApp messages (CR-03). Never sent to a real provider in M2-M3.';
COMMENT ON TABLE request_feedback IS 'Requester rating (1-5) after resolution.';
