-- =============================================================================
-- CivicConnect | V2 - Management reporting views
-- Owner: Person 3 (Database & Management Reporting Module)
-- Run as civic_owner after V1.
--
-- Consistency rule: every report is built on ONE base view (v_request_overview), which
-- holds the ONLY definitions of "open", "overdue", "resolved", "closed", age and
-- resolution time. The Java ReportService reads all dashboard views inside one
-- read-only REPEATABLE READ transaction, so every number on a dashboard comes from
-- the same database snapshot and the same now().
-- =============================================================================

SET search_path = civic;

-- -----------------------------------------------------------------------------
-- Base view: one row per request, with all derived reporting fields
-- -----------------------------------------------------------------------------
CREATE VIEW v_request_overview AS
SELECT
    r.request_id,
    r.reference_no,
    r.title,
    r.description,
    r.priority,
    r.location_text,
    r.status_code,
    s.display_name                         AS status_name,
    s.lifecycle_group,
    s.sort_order                           AS status_sort_order,
    r.category_id,
    c.code                                 AS category_code,
    c.name                                 AS category_name,
    d.department_id,
    d.name                                 AS department_name,
    r.requester_id,
    req.full_name                          AS requester_name,
    r.assignee_id,
    asg.full_name                          AS assignee_name,
    r.created_at,
    r.updated_at,
    r.due_at,
    r.resolved_at,
    r.closed_at,
    r.version,
    -- THE definition of overdue: still open and past its SLA due date.
    (s.lifecycle_group = 'OPEN' AND r.due_at < now())                          AS is_overdue,
    CASE WHEN s.lifecycle_group = 'OPEN' AND r.due_at < now()
         THEN round((extract(epoch FROM now() - r.due_at) / 3600.0)::numeric, 1) END AS overdue_hours,
    floor(extract(epoch FROM coalesce(r.closed_at, now()) - r.created_at) / 86400)::int AS age_days,
    CASE WHEN r.resolved_at IS NOT NULL
         THEN round((extract(epoch FROM r.resolved_at - r.created_at) / 3600.0)::numeric, 1) END AS resolution_hours,
    CASE WHEN r.resolved_at IS NOT NULL THEN r.resolved_at <= r.due_at END      AS resolved_within_sla
FROM service_request r
JOIN request_status   s   ON s.status_code  = r.status_code
JOIN request_category c   ON c.category_id  = r.category_id
JOIN department       d   ON d.department_id = c.department_id
JOIN app_user         req ON req.user_id    = r.requester_id
LEFT JOIN app_user    asg ON asg.user_id    = r.assignee_id;

-- -----------------------------------------------------------------------------
-- Dashboard headline figures (single row)
-- -----------------------------------------------------------------------------
CREATE VIEW v_dashboard_kpis AS
SELECT
    count(*)                                                        AS total_requests,
    count(*) FILTER (WHERE lifecycle_group = 'OPEN')                AS open_requests,
    count(*) FILTER (WHERE is_overdue)                              AS overdue_requests,
    count(*) FILTER (WHERE lifecycle_group = 'RESOLVED')            AS resolved_requests,
    count(*) FILTER (WHERE lifecycle_group = 'CLOSED')              AS closed_requests,
    count(*) FILTER (WHERE status_code = 'REJECTED')                AS rejected_requests,
    count(*) FILTER (WHERE status_code = 'SUBMITTED')               AS unassigned_requests,
    count(*) FILTER (WHERE created_at >= now() - interval '7 days') AS created_last_7_days,
    count(*) FILTER (WHERE resolved_at >= now() - interval '7 days') AS resolved_last_7_days,
    round(avg(resolution_hours), 1)                                 AS avg_resolution_hours,
    round(100.0 * count(*) FILTER (WHERE resolved_within_sla)
          / nullif(count(*) FILTER (WHERE resolved_at IS NOT NULL), 0), 1) AS sla_compliance_pct,
    now()                                                           AS generated_at
FROM v_request_overview;

-- -----------------------------------------------------------------------------
-- Statistics by status (every status appears, even with zero requests)
-- -----------------------------------------------------------------------------
CREATE VIEW v_report_status_summary AS
SELECT
    s.status_code,
    s.display_name,
    s.lifecycle_group,
    s.sort_order,
    count(o.request_id)                          AS request_count,
    count(o.request_id) FILTER (WHERE o.is_overdue) AS overdue_count
FROM request_status s
LEFT JOIN v_request_overview o ON o.status_code = s.status_code
GROUP BY s.status_code, s.display_name, s.lifecycle_group, s.sort_order;

-- -----------------------------------------------------------------------------
-- Statistics by category (every category appears, even with zero requests)
-- -----------------------------------------------------------------------------
CREATE VIEW v_report_category_summary AS
SELECT
    c.category_id,
    c.code                                                          AS category_code,
    c.name                                                          AS category_name,
    d.name                                                          AS department_name,
    c.sla_hours,
    count(o.request_id)                                             AS total_requests,
    count(o.request_id) FILTER (WHERE o.lifecycle_group = 'OPEN')   AS open_requests,
    count(o.request_id) FILTER (WHERE o.is_overdue)                 AS overdue_requests,
    count(o.request_id) FILTER (WHERE o.lifecycle_group = 'RESOLVED') AS resolved_requests,
    count(o.request_id) FILTER (WHERE o.lifecycle_group = 'CLOSED') AS closed_requests,
    round(avg(o.resolution_hours), 1)                               AS avg_resolution_hours,
    round(100.0 * count(o.request_id) FILTER (WHERE o.resolved_within_sla)
          / nullif(count(o.request_id) FILTER (WHERE o.resolved_at IS NOT NULL), 0), 1) AS sla_compliance_pct
FROM request_category c
JOIN department d ON d.department_id = c.department_id
LEFT JOIN v_request_overview o ON o.category_id = c.category_id
GROUP BY c.category_id, c.code, c.name, d.name, c.sla_hours;

-- -----------------------------------------------------------------------------
-- Category x Status matrix (cross-tab source, zero-filled)
-- -----------------------------------------------------------------------------
CREATE VIEW v_report_category_status AS
SELECT
    c.category_id,
    c.name          AS category_name,
    s.status_code,
    s.display_name  AS status_name,
    s.sort_order    AS status_sort_order,
    count(o.request_id) AS request_count
FROM request_category c
CROSS JOIN request_status s
LEFT JOIN v_request_overview o
       ON o.category_id = c.category_id AND o.status_code = s.status_code
GROUP BY c.category_id, c.name, s.status_code, s.display_name, s.sort_order;

-- -----------------------------------------------------------------------------
-- Ageing of OPEN requests (backlog health for oversight)
-- -----------------------------------------------------------------------------
CREATE VIEW v_report_open_ageing AS
WITH buckets(bucket_order, bucket_label, min_days, max_days) AS (
    VALUES (1, '0-2 days', 0, 2),
           (2, '3-7 days', 3, 7),
           (3, '8-14 days', 8, 14),
           (4, '15-30 days', 15, 30),
           (5, 'Over 30 days', 31, 2147483647)
)
SELECT
    b.bucket_order,
    b.bucket_label,
    count(o.request_id)                               AS open_requests,
    count(o.request_id) FILTER (WHERE o.is_overdue)   AS overdue_requests
FROM buckets b
LEFT JOIN v_request_overview o
       ON o.lifecycle_group = 'OPEN' AND o.age_days BETWEEN b.min_days AND b.max_days
GROUP BY b.bucket_order, b.bucket_label;

-- -----------------------------------------------------------------------------
-- Monthly trend: requests created vs resolved (last 6 calendar months, zero-filled)
-- -----------------------------------------------------------------------------
CREATE VIEW v_report_monthly_trend AS
WITH months AS (
    SELECT generate_series(date_trunc('month', now()) - interval '5 months',
                           date_trunc('month', now()), interval '1 month') AS month_start
)
SELECT
    m.month_start::date                                                       AS month_start,
    to_char(m.month_start, 'Mon YYYY')                                        AS month_label,
    (SELECT count(*) FROM service_request r
      WHERE r.created_at  >= m.month_start AND r.created_at  < m.month_start + interval '1 month') AS created_count,
    (SELECT count(*) FROM service_request r
      WHERE r.resolved_at >= m.month_start AND r.resolved_at < m.month_start + interval '1 month') AS resolved_count
FROM months m;

-- The web application role reads the views; ALTER DEFAULT PRIVILEGES in V0 covers them,
-- but grant explicitly as well in case V0 was not used (e.g. a local dev database).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'civic_app') THEN
        GRANT SELECT ON v_request_overview, v_dashboard_kpis, v_report_status_summary,
                        v_report_category_summary, v_report_category_status,
                        v_report_open_ageing, v_report_monthly_trend TO civic_app;
    END IF;
END;
$$;
