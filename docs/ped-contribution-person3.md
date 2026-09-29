# PED v2.0 contribution: Database & Management Reporting (Person 3)

Paste or adapt these sections into the **same** PED, RTM and Risk Register. Replace the `REQ-…` IDs with the team's baselined M1 requirement IDs. Do not create a separate "M2 report" (M2 brief, section 2).

## 1. RTM rows (new and progressed columns)

| Req ID (map to M1) | Requirement (short) | ASR / quality driver | Module / component | Data / persistence impact | Design / interface decision | Technology | Implementation evidence | Verification evidence | Status | ADR / risk |
|---|---|---|---|---|---|---|---|---|---|---|
| REQ-DATA-01 | Persist service requests with category, requester, assignee, priority, location | Integrity, maintainability | Data module (`com.civicconnect.data`) | `service_request`, FKs to category/status/user; CHECK constraints | Repository interface `ServiceRequestRepository` (DIP) | PostgreSQL 16, pgjdbc 42.7, Java 21 | `database/V1__schema.sql`; `JdbcServiceRequestRepository.create` | `createWritesRequestAndInitialHistoryTogether` (DB test) | In Development | ADR-P3-01; R-P3-01 |
| REQ-AUD-01 | Every status change attributable (who/when) and unalterable | **ASR: accountability / auditability** | Data module | `request_status_history` append-only; deferred consistency trigger | Status change + history in one transaction | PostgreSQL triggers | `V1__schema.sql` §4-5; `changeStatus` | `statusChangeUpdatesRequestAndHistoryAtomically`, `statusChangeWithoutHistoryIsRejectedAtCommit`, `historyCannotBeEdited` | In Development | ADR-P3-02; R-P3-02 |
| REQ-CONC-01 | Concurrent staff updates must not overwrite each other | Reliability / correctness | Data module | `service_request.version` | Optimistic concurrency, `StaleUpdateException` | JDBC | `UPDATE_STATUS … WHERE version = ? AND status_code = ?` | `staleVersionIsRejectedAndNothingIsWritten` | In Development | ADR-P3-02; R-P3-03 |
| REQ-RPT-01 | Management dashboard for oversight | Usability, accuracy | Reporting module (`/dashboard`) | Views `v_dashboard_kpis` etc. | Snapshot transaction; reconciliation check | Servlet 6 / JSP on Tomcat 10.1 | `DashboardServlet`, `dashboard.jsp`, `V2__reporting_views.sql` | `dashboardReconciles`; `DashboardSnapshotTest` | In Development | ADR-P3-03 |
| REQ-RPT-02 | Reports: open, overdue, resolved, closed requests | **ASR: report accuracy** | Reporting module (`/reports/requests`) | `v_request_overview.lifecycle_group`, `is_overdue` (single definition) | `ReportType` presets over one view | as above | `RequestReportServlet`, `requests.jsp` | `reportCountsMatchDashboardTiles`, `everyRowInEachReportBelongsInIt` | In Development | ADR-P3-03; R-P3-04 |
| REQ-RPT-03 | Overdue determined by category SLA | Correctness | Data + reporting | `request_category.sla_hours` → `due_at` (trigger) | Overdue = OPEN AND `due_at < now()` | PostgreSQL | `trg_request_defaults`, `v_request_overview` | `createWritesRequestAndInitialHistoryTogether` (due_at set); overdue report test | In Development | ADR-P3-03 |
| REQ-RPT-04 | Statistics by category and by status | Accuracy | Reporting module | `v_report_status_summary`, `v_report_category_summary`, `v_report_category_status` (zero-filled) | Aggregates in SQL, not Java | PostgreSQL views | `ReportRepository` | `categoryFilterTotalsMatchCategoryStatistics` | In Development | ADR-P3-03 |
| REQ-RPT-05 | Search, filter and sort requests | Usability, **security (injection)** | Reporting module | Indexes on status, category, created_at, due_at (partial) | Whitelisted `SortField`; bound parameters; LIKE escaping; stable paging | JDBC | `RequestQueryBuilder`, `SearchCriteriaParser` | `RequestQueryBuilderTest` (9), `SearchCriteriaParserTest` (3), `pagingReturnsEveryRowExactlyOnceInSortedOrder` | In Development | R-P3-05 |
| REQ-RPT-06 | Export report data | Interoperability | Reporting module | none | CSV with formula-injection guard | Excel / Power BI | `CsvWriter`, `format=csv` | `CsvWriterTest`, `csvExportHasHeaderPlusOneLinePerRequest` | In Development | R-P3-06 |
| REQ-SEC-02 | Only authorised roles see oversight data | Security (OWASP A01) | Web module | Role on `app_user` | Deny-by-default `ManagementAccessFilter` | Servlet filter | `ManagementAccessFilter` | Planned: role-by-page negative tests | In Development | R-P3-07 |

## 2. End-to-end trace (M2 brief, section 9)

**REQ-AUD-01** (auditable status history) → **ASR:** accountability; the audit trail must never disagree with the current status → **Architecture:** the Data module owns persistence, and the lifecycle module owns transition rules → **Data decision:** append-only `request_status_history` plus a deferred status/history consistency trigger → **Design/interface:** `ServiceRequestRepository.changeStatus(StatusChange)` performs one atomic transaction with an optimistic version check → **Technology/ADR:** PostgreSQL 16 + JDBC; ADR-P3-01 and ADR-P3-02 (evidence: A2 Task 2, Approach 1 vs 2) → **Application artefact:** `V1__schema.sql` (trigger `request_status_has_history`) and `JdbcServiceRequestRepository.UPDATE_STATUS` → **Initial verification:** `JdbcServiceRequestRepositoryDbTest` (7 tests), plus direct SQL checks run against PostgreSQL 16 during development.

## 3. Risk Register entries

| ID | Risk | Likelihood | Impact | Mitigation | Owner |
|---|---|---|---|---|---|
| R-P3-01 | Single PostgreSQL instance is a single point of failure / data loss | Low | High | Daily `pg_dump`, tested restore; managed DB with PITR decided at deployment | P3 |
| R-P3-02 | A code path changes status without writing history | Medium | High | One repository method; deferred DB trigger rejects the commit; DB tests | P3 |
| R-P3-03 | Lost update when two staff act on the same request | Medium | Medium | Optimistic `version` check; user-facing reload message | P3 |
| R-P3-04 | Reports disagree with each other or with the records (different "overdue" definitions) | Medium | High | One base view; snapshot transaction; on-page reconciliation; consistency tests | P3 |
| R-P3-05 | SQL injection via search/sort parameters | Medium | High | Bound parameters only; enum sort whitelist; unit tests | P3 |
| R-P3-06 | CSV/formula injection when managers open exports in Excel | Low | Medium | Prefix `= + - @` cells; test | P3 |
| R-P3-07 | Dev auto-login role left enabled on a shared server | Medium | High | Documented in README + context.xml; add to deployment checklist / PR template | P3 + reviewer |
| R-P3-08 | Dashboard slows as data grows (live aggregates) | Low | Low | Indexes + partial index; switch to materialised views with "as of" time if needed | P3 |
| R-P3-09 | Credentials committed to Git | Low | High | Env vars / JNDI, `.env.example`, `.gitignore`; no secrets in repo | Team |

## 4. Assumptions, dependencies, and forward engineering considerations

- **Assumption:** the lifecycle set is Submitted, Assigned, In Progress, Resolved, Closed, Rejected, Reopened (from A2 P1). Adding a status means inserting a `request_status` row with its `lifecycle_group`. Reports pick it up automatically; the partial index `ix_request_open_due_at` lists the open codes and would need updating.
- **Assumption:** SLA hours per category are placeholders (V3) until stakeholders confirm them.
- **Dependency:** the login module sets session attribute `userRole`. The lifecycle module calls `ServiceRequestRepository.changeStatus()` after validating the transition.
- **Deferred:** backup/restore automation, a migration tool (Flyway) in CI, `pg_trgm` search index, materialised views, and hosting choice (local Tomcat vs cloud). The evidence needed later is volume estimates and the deployment target.

## 5. AI Usage Register entry (if your course requires it)

| Artefact | AI assistance | Team verification |
|---|---|---|
| Schema, views, repository, reporting code, ADR drafts | Drafted with an AI assistant from the team's A2 research and M2 brief | SQL executed on PostgreSQL 16 (all scripts, integrity rules, every generated query); Java compiled; unit tests run; *team to record: Tomcat run, DB tests on own machine, PR reviews* |
