-- =============================================================================
-- CivicConnect | V7 - Staff comments (FR-09) and restricted categories (FR-12)
-- Owner: Person 2 (Backend). Reviewer: Person 3 (Database) - schema changes need the
-- database owner's approval before merge (PR template).
-- Run as civic_owner AFTER V1..V6:
--   psql -h localhost -U civic_owner -d civicconnect -f V7__comments_and_restricted_categories.sql
--
-- Design notes (PED v4.0, CR-11):
--  * FR-09: a comment is a separate, append-only row. It is attributed (author_id) and
--    timestamped by the database (clock_timestamp()), and the same trigger that protects
--    request_status_history refuses UPDATE and DELETE, so author and time cannot be altered.
--  * FR-12: "restricted" is an attribute of the category, not of each request, so the
--    rule is set once per category and cannot be forgotten on submission.
--  * TD-02: the service already caps these lengths; the CHECKs make the database agree,
--    so a direct SQL client cannot store more than the application accepts.
--  * Additive only: the M3 build still runs on this schema (rollback = redeploy old WAR).
--  * civic_app inherits SELECT/INSERT/UPDATE on the new table from V0 default privileges
--    and still has no DELETE.
-- =============================================================================

SET search_path = civic;

-- -----------------------------------------------------------------------------
-- 1. FR-12: restricted categories
-- -----------------------------------------------------------------------------
ALTER TABLE request_category
    ADD COLUMN is_restricted BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN request_category.is_restricted IS
    'FR-12: requests in this category are visible only to the requester, the assignee, MANAGER and ADMIN.';

INSERT INTO department (name, contact_email)
VALUES ('Community Safety', 'safety@civicconnect.example');

INSERT INTO request_category (code, name, description, department_id, sla_hours, is_restricted)
SELECT 'SAFETY', 'Safety or Security Concern',
       'Unsafe sites, vandalism, security threats; handled by authorised staff only',
       department_id, 24, TRUE
  FROM department
 WHERE name = 'Community Safety';

-- -----------------------------------------------------------------------------
-- 2. FR-09: staff comments (append-only)
-- -----------------------------------------------------------------------------
CREATE TABLE request_comment (
    comment_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id BIGINT        NOT NULL REFERENCES service_request (request_id) ON DELETE RESTRICT,
    author_id  BIGINT        NOT NULL REFERENCES app_user (user_id),
    body       VARCHAR(1000) NOT NULL CONSTRAINT ck_comment_body_not_blank CHECK (btrim(body) <> ''),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX ix_comment_request_time ON request_comment (request_id, created_at, comment_id);

-- Same rule as V1's history trigger; search_path pinned as in the DF-01 fix.
CREATE FUNCTION trg_comment_append_only() RETURNS trigger
LANGUAGE plpgsql
SET search_path = civic
AS $$
BEGIN
    RAISE EXCEPTION 'request_comment is append-only (% not allowed)', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER comment_append_only
    BEFORE UPDATE OR DELETE ON request_comment
    FOR EACH ROW EXECUTE FUNCTION trg_comment_append_only();

-- -----------------------------------------------------------------------------
-- 3. TD-02: database length caps that mirror the service-layer validator
-- -----------------------------------------------------------------------------
ALTER TABLE service_request
    ADD CONSTRAINT ck_request_description_max CHECK (char_length(description) <= 2000),
    ADD CONSTRAINT ck_request_resolution_notes_max
        CHECK (resolution_notes IS NULL OR char_length(resolution_notes) <= 2000);

ALTER TABLE request_status_history
    ADD CONSTRAINT ck_history_note_max CHECK (note IS NULL OR char_length(note) <= 1000);
