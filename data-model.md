# CivicConnect data model (Person 3)

```mermaid
erDiagram
    ROLE ||--o{ APP_USER : "has"
    DEPARTMENT ||--o{ APP_USER : "employs (staff)"
    DEPARTMENT ||--o{ REQUEST_CATEGORY : "owns"
    REQUEST_CATEGORY ||--o{ SERVICE_REQUEST : "classifies"
    REQUEST_STATUS ||--o{ SERVICE_REQUEST : "current status"
    APP_USER ||--o{ SERVICE_REQUEST : "logs (requester)"
    APP_USER |o--o{ SERVICE_REQUEST : "works on (assignee)"
    SERVICE_REQUEST ||--|{ REQUEST_STATUS_HISTORY : "audit trail"
    REQUEST_STATUS ||--o{ REQUEST_STATUS_HISTORY : "from / to"
    APP_USER ||--o{ REQUEST_STATUS_HISTORY : "changed by"

    ROLE {
        varchar role_code PK
    }
    DEPARTMENT {
        smallint department_id PK
        varchar name UK
    }
    REQUEST_STATUS {
        varchar status_code PK
        varchar lifecycle_group "OPEN|RESOLVED|CLOSED"
        smallint sort_order
    }
    REQUEST_CATEGORY {
        smallint category_id PK
        varchar code UK
        smallint department_id FK
        int sla_hours "> 0"
    }
    APP_USER {
        bigint user_id PK
        varchar email UK
        varchar role_code FK
        smallint department_id FK
    }
    SERVICE_REQUEST {
        bigint request_id PK
        varchar reference_no UK "CC-000123 (generated)"
        smallint category_id FK
        varchar status_code FK
        varchar priority "LOW|MEDIUM|HIGH|URGENT"
        bigint requester_id FK
        bigint assignee_id FK
        timestamptz created_at
        timestamptz due_at "created_at + category SLA"
        timestamptz resolved_at
        timestamptz closed_at
        int version "optimistic concurrency"
    }
    REQUEST_STATUS_HISTORY {
        bigint history_id PK
        bigint request_id FK
        varchar from_status FK "NULL = created"
        varchar to_status FK
        bigint changed_by FK
        timestamptz changed_at
        bigint assignee_id FK
        text note
    }
```

## Ownership and lifecycle

| Entity | Written by | Lifecycle / retention |
|---|---|---|
| `service_request` | Request intake (create) and lifecycle module (status changes) via `ServiceRequestRepository` | Never deleted by the application (the app role has no DELETE grant) |
| `request_status_history` | Only inside the same transaction as the status change | **Append-only**: UPDATE and DELETE are blocked by a trigger |
| `request_category`, `request_status`, `department`, `role` | Admin / migration scripts | Reference data. Deactivate with `is_active`; do not delete |
| `app_user` | Account module | Deactivate with `is_active`. `password_hash` is owned by the auth module |

## Integrity rules (defence in depth: UI → service → database)

| Rule | Enforced by |
|---|---|
| Status is a known value | FK `service_request.status_code → request_status` |
| Priority is a known value | CHECK `ck_request_priority` |
| ASSIGNED / IN_PROGRESS must have an assignee | CHECK `ck_request_assignee_when_assigned` |
| RESOLVED must record `resolved_at` and resolution notes | CHECK `ck_request_resolution_recorded` |
| CLOSED / REJECTED must record `closed_at` | CHECK `ck_request_closed_timestamp` |
| Timestamps are not before creation | CHECKs `ck_request_*_after_created` |
| Every status has a matching history row | Deferred constraint trigger `request_status_has_history` (checked at COMMIT) |
| History cannot be altered | Trigger `history_append_only` |
| Two users cannot overwrite each other's change | `version` + `WHERE version = ? AND status_code = ?` in `changeStatus` |
| Which transitions are allowed (e.g. SUBMITTED ↛ CLOSED) | Lifecycle module (State pattern). Deliberately **not** duplicated in the DB (one source of truth) |

## Reporting views (V2)

`v_request_overview` is the only place where `lifecycle_group`, `is_overdue`, `age_days`, `resolution_hours` and `resolved_within_sla` are defined. Every other view and every report query reads from it:
`v_dashboard_kpis`, `v_report_status_summary`, `v_report_category_summary`, `v_report_category_status`, `v_report_open_ageing`, `v_report_monthly_trend`.
