-- =============================================================================
-- CivicConnect | V1 - Core schema (PostgreSQL 16+)
-- Owner: Person 3 (Database & Management Reporting Module)
-- Run as civic_owner:  psql -U civic_owner -d civicconnect -f V1__schema.sql
--
-- Design decisions (see docs/adr/ADR-P3-01 and ADR-P3-02):
--  * Relational model: requests, categories, users and status history are strongly
--    related, and management reports need joins and aggregates over them.
--  * Status values are a lookup table (not free text), so reports can group by a
--    single, controlled "lifecycle_group" (OPEN / RESOLVED / CLOSED).
--  * The status change and its history row are one correctness boundary. A deferred
--    constraint trigger rejects any COMMIT where a request's status has no matching
--    latest history row (A2 Task 2: atomic service transaction + DB backstop).
--  * Optimistic concurrency: service_request.version is checked on every update
--    (A2 Task 2, section 1.4).
--  * Status history is append-only (M1 accountability requirement).
-- =============================================================================

SET search_path = civic;

-- -----------------------------------------------------------------------------
-- 1. Reference / lookup tables
-- -----------------------------------------------------------------------------

CREATE TABLE role (
    role_code    VARCHAR(20)  PRIMARY KEY,
    display_name VARCHAR(60)  NOT NULL,
    description  VARCHAR(255)
);

CREATE TABLE department (
    department_id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(100) NOT NULL UNIQUE,
    contact_email VARCHAR(254),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE request_status (
    status_code     VARCHAR(20) PRIMARY KEY,
    display_name    VARCHAR(40) NOT NULL UNIQUE,
    -- Single definition used by EVERY report: which bucket a status belongs to.
    lifecycle_group VARCHAR(10) NOT NULL
        CONSTRAINT ck_status_lifecycle_group CHECK (lifecycle_group IN ('OPEN', 'RESOLVED', 'CLOSED')),
    sort_order      SMALLINT    NOT NULL UNIQUE,
    is_terminal     BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE TABLE request_category (
    category_id   SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(20)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL UNIQUE,
    description   VARCHAR(255),
    department_id SMALLINT     NOT NULL REFERENCES department (department_id),
    -- Service-level target used to calculate due_at, and therefore "overdue".
    sla_hours     INTEGER      NOT NULL CONSTRAINT ck_category_sla_positive CHECK (sla_hours > 0),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE
);

-- -----------------------------------------------------------------------------
-- 2. Users
--    Authentication itself belongs to the account/security module; this table only
--    holds what persistence and reporting need (identity, role, department).
-- -----------------------------------------------------------------------------

CREATE TABLE app_user (
    user_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    full_name     VARCHAR(120) NOT NULL CONSTRAINT ck_user_name_not_blank CHECK (btrim(full_name) <> ''),
    phone         VARCHAR(20),
    role_code     VARCHAR(20)  NOT NULL REFERENCES role (role_code),
    department_id SMALLINT     REFERENCES department (department_id),
    password_hash VARCHAR(255),              -- hash only; managed by the auth module
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Case-insensitive unique email
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));

-- -----------------------------------------------------------------------------
-- 3. Service requests (the core aggregate)
-- -----------------------------------------------------------------------------

CREATE TABLE service_request (
    request_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference_no     VARCHAR(20)  GENERATED ALWAYS AS ('CC-' || lpad(request_id::text, 6, '0')) STORED UNIQUE,
    title            VARCHAR(150) NOT NULL CONSTRAINT ck_request_title_not_blank CHECK (btrim(title) <> ''),
    description      TEXT         NOT NULL CONSTRAINT ck_request_description_not_blank CHECK (btrim(description) <> ''),
    category_id      SMALLINT     NOT NULL REFERENCES request_category (category_id),
    status_code      VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED' REFERENCES request_status (status_code),
    priority         VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM'
        CONSTRAINT ck_request_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    requester_id     BIGINT       NOT NULL REFERENCES app_user (user_id),
    assignee_id      BIGINT       REFERENCES app_user (user_id),
    location_text    VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    due_at           TIMESTAMPTZ  NOT NULL,   -- set from category SLA by trigger if not supplied
    resolved_at      TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    resolution_notes TEXT,
    version          INTEGER      NOT NULL DEFAULT 0,   -- optimistic concurrency token

    CONSTRAINT ck_request_due_after_created      CHECK (due_at >= created_at),
    CONSTRAINT ck_request_resolved_after_created CHECK (resolved_at IS NULL OR resolved_at >= created_at),
    CONSTRAINT ck_request_closed_after_created   CHECK (closed_at   IS NULL OR closed_at   >= created_at),
    -- Transition obligations that are expressible as data (A2 Task 1, P1 / Task 2, 1.3):
    CONSTRAINT ck_request_assignee_when_assigned
        CHECK (status_code NOT IN ('ASSIGNED', 'IN_PROGRESS') OR assignee_id IS NOT NULL),
    CONSTRAINT ck_request_resolution_recorded
        CHECK (status_code <> 'RESOLVED' OR (resolved_at IS NOT NULL AND resolution_notes IS NOT NULL)),
    CONSTRAINT ck_request_closed_timestamp
        CHECK (status_code NOT IN ('CLOSED', 'REJECTED') OR closed_at IS NOT NULL)
);

-- Indexes chosen from the reporting access patterns (status, category, due date, recency)
CREATE INDEX ix_request_status            ON service_request (status_code);
CREATE INDEX ix_request_category_status   ON service_request (category_id, status_code);
CREATE INDEX ix_request_created_at        ON service_request (created_at DESC);
CREATE INDEX ix_request_assignee          ON service_request (assignee_id) WHERE assignee_id IS NOT NULL;
CREATE INDEX ix_request_requester         ON service_request (requester_id);
-- Overdue checks only ever look at open requests, so a partial index keeps it small.
CREATE INDEX ix_request_open_due_at       ON service_request (due_at)
    WHERE status_code IN ('SUBMITTED', 'ASSIGNED', 'IN_PROGRESS', 'REOPENED');

-- -----------------------------------------------------------------------------
-- 4. Status history (audit trail) - append-only
-- -----------------------------------------------------------------------------

CREATE TABLE request_status_history (
    history_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id   BIGINT      NOT NULL REFERENCES service_request (request_id) ON DELETE RESTRICT,
    from_status  VARCHAR(20) REFERENCES request_status (status_code),   -- NULL = request created
    to_status    VARCHAR(20) NOT NULL REFERENCES request_status (status_code),
    changed_by   BIGINT      NOT NULL REFERENCES app_user (user_id),
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    assignee_id  BIGINT      REFERENCES app_user (user_id),             -- assignee at the time of change
    note         TEXT,
    CONSTRAINT ck_history_status_changes CHECK (from_status IS NULL OR from_status <> to_status)
);
CREATE INDEX ix_history_request_time ON request_status_history (request_id, changed_at, history_id);
CREATE INDEX ix_history_changed_at   ON request_status_history (changed_at);

-- -----------------------------------------------------------------------------
-- 5. Triggers
-- -----------------------------------------------------------------------------

-- 5.1 Default due_at from the category SLA; keep updated_at current.
CREATE FUNCTION trg_request_defaults() RETURNS trigger
LANGUAGE plpgsql
SET search_path = civic
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.due_at IS NULL THEN
            SELECT NEW.created_at + make_interval(hours => c.sla_hours)
              INTO NEW.due_at
              FROM request_category c
             WHERE c.category_id = NEW.category_id;
        END IF;
    ELSE
        NEW.updated_at := now();
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER request_defaults
    BEFORE INSERT OR UPDATE ON service_request
    FOR EACH ROW EXECUTE FUNCTION trg_request_defaults();

-- 5.2 History is immutable: no UPDATE or DELETE, even by accident or by an admin tool.
CREATE FUNCTION trg_history_append_only() RETURNS trigger
LANGUAGE plpgsql
SET search_path = civic
AS $$
BEGIN
    RAISE EXCEPTION 'request_status_history is append-only (% not allowed)', TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER history_append_only
    BEFORE UPDATE OR DELETE ON request_status_history
    FOR EACH ROW EXECUTE FUNCTION trg_history_append_only();

-- 5.3 Status/history consistency, checked at COMMIT (deferred).
--     If a transaction creates a request or changes its status without writing the
--     matching history row, the whole transaction is rolled back. This makes it
--     impossible for reports to count a status that the audit trail cannot explain.
CREATE FUNCTION trg_request_status_has_history() RETURNS trigger
LANGUAGE plpgsql
SET search_path = civic
AS $$
DECLARE
    latest_status  VARCHAR(20);
    current_status VARCHAR(20);
BEGIN
    -- Re-read the row as it is at commit time (it may have changed again later in the tx).
    SELECT status_code INTO current_status FROM service_request WHERE request_id = NEW.request_id;

    SELECT h.to_status INTO latest_status
      FROM request_status_history h
     WHERE h.request_id = NEW.request_id
     ORDER BY h.changed_at DESC, h.history_id DESC
     LIMIT 1;

    IF latest_status IS DISTINCT FROM current_status THEN
        RAISE EXCEPTION 'Request % has status % but its latest history entry is %; status changes must be written together with history',
            NEW.request_id, current_status, coalesce(latest_status, '<none>')
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER request_status_has_history
    AFTER INSERT OR UPDATE OF status_code ON service_request
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION trg_request_status_has_history();

COMMENT ON TABLE  service_request IS 'CivicConnect service request (core aggregate). Status changes must be accompanied by a request_status_history row in the same transaction.';
COMMENT ON COLUMN service_request.version IS 'Optimistic concurrency token. UPDATE ... WHERE version = :expected, then version + 1.';
COMMENT ON COLUMN request_status.lifecycle_group IS 'Reporting bucket. OPEN, RESOLVED and CLOSED reports all derive from this one column.';
COMMENT ON TABLE  request_status_history IS 'Append-only audit trail of every status change (who, what, when).';
