# CivicConnect: Database & Management Reporting Module (Person 3)

This module covers the data and reporting work for CivicConnect:

| Task (Person 3) | Where it lives |
|---|---|
| Design and implement the database schema | `database/V1__schema.sql`, ERD in `docs/data-model.md` |
| Database connectivity and persistence | `com.civicconnect.data` (`Database`, `TransactionRunner`, `JdbcServiceRequestRepository`) |
| Management and oversight dashboard | `/dashboard`: `DashboardServlet` + `WEB-INF/jsp/dashboard.jsp` |
| Reports for open, overdue, resolved and closed requests | `/reports/requests?report=open\|overdue\|resolved\|closed\|all`, plus CSV export |
| Request statistics by category and status | `database/V2__reporting_views.sql` → dashboard tables and heat map |
| Search, filtering and sorting | `RequestSearchCriteria`, `RequestQueryBuilder`, `SortField` (whitelisted) |
| Accurate, consistent report data | One base view, one snapshot transaction per page, live reconciliation check, `ReportConsistencyDbTest` |

Engineering decisions are recorded in `docs/adr/`. The RTM rows and risk entries for PED v2.0 are in `docs/ped-contribution-person3.md`.

## Technology (current versions)

| Layer | Choice | Version used / tested |
|---|---|---|
| Language / runtime | Java (JDK) | 21 LTS |
| Web | Jakarta Servlet 6.0 + JSP/JSTL 3.0 on **Apache Tomcat** | Tomcat 10.1.x |
| Database | **PostgreSQL** | tested on 16.13; any 14+ works |
| Driver | PostgreSQL JDBC (pgjdbc) | 42.7.x |
| Build | Maven (works in IntelliJ IDEA and Apache NetBeans) | 3.9.x |
| Tests | JUnit 5 | 5.11.x |

GlassFish 7 works as well: it uses the same Jakarta EE 10 APIs, and you configure the JNDI resource in the admin console instead of `context.xml`.

## Project structure

```
database/
  V0__create_database_and_roles.sql    run once as postgres superuser (roles, DB, schema, grants)
  V1__schema.sql                       tables, constraints, indexes, triggers
  V2__reporting_views.sql              ALL report definitions (open/overdue/resolved/closed, stats)
  V3__reference_data.sql               roles, statuses, departments, categories + SLA hours
  V4__sample_data_dev_only.sql         180 realistic demo requests with full history (dev/test only)
src/main/java/com/civicconnect/
  data/        connectivity, transactions, ServiceRequestRepository (+ JDBC implementation)
  reporting/   ReportService, ReportRepository, search criteria, query builder, CSV export
  web/         servlets, access filter, context listener
  util/        display formatting (SAST)
src/main/webapp/
  WEB-INF/jsp/ dashboard.jsp, requests.jsp, request-detail.jsp
  static/      reporting.css
  META-INF/context.xml   JNDI DataSource (reads env vars; no secrets committed)
src/test/java/  unit tests (always run) + database tests (run when CIVIC_TEST_DB_URL is set)
docs/           ADRs, data model, PED/RTM contribution
```

## Setup

### 1. Database (PostgreSQL)

```bash
cd database
# One-time: create roles + database (choose your own passwords; they are NOT stored in the repo)
psql -U postgres -v app_password='APP_PW' -v owner_password='OWNER_PW' -f V0__create_database_and_roles.sql

# Schema, views, reference data (as the owner role)
psql -h localhost -U civic_owner -d civicconnect -f V1__schema.sql
psql -h localhost -U civic_owner -d civicconnect -f V2__reporting_views.sql
psql -h localhost -U civic_owner -d civicconnect -f V3__reference_data.sql
# Dev/demo only:
psql -h localhost -U civic_owner -d civicconnect -f V4__sample_data_dev_only.sql
```

You can also run the scripts in pgAdmin's Query Tool in the same order. Running `SELECT * FROM civic.v_dashboard_kpis;` should return 180 requests.

### 2. Tomcat

1. Install Tomcat 10.1 and copy `postgresql-42.7.x.jar` into `$CATALINA_HOME/lib`. The container connection pool needs it there.
2. Set the environment variables from `.env.example` (`CIVIC_DB_URL`, `CIVIC_DB_USER=civic_app`, `CIVIC_DB_PASSWORD`).
3. Tomcat only substitutes environment variables into `context.xml` if you add this line to `$CATALINA_BASE/conf/catalina.properties`:
   `org.apache.tomcat.util.digester.PROPERTY_SOURCE=org.apache.tomcat.util.digester.EnvironmentPropertySource`
   If you skip this, the app still starts: `Database.lookup()` falls back to reading the same environment variables directly (without pooling).
4. Build and deploy: `mvn package`, then copy `target/civicconnect.war` to `webapps/`. Alternatively, in IntelliJ or NetBeans, add a Tomcat run configuration that deploys the exploded war.
5. Open `http://localhost:8080/civicconnect/dashboard`.

**Access control:** the dashboard and reports only allow the `MANAGER`, `COORDINATOR` and `ADMIN` roles. The login module (owned by a teammate) must set the session attribute `userRole` after sign-in. Until that module exists, `META-INF/context.xml` sets `civicconnect.devAutoLoginRole=MANAGER` so the dashboard can be demoed. **Blank this value before any shared deployment.**

### 3. Tests

```bash
mvn test                      # unit tests; database tests are skipped
# Database tests need a SEPARATE throw-away database: its "civic" schema is dropped and rebuilt
createdb -U postgres -O civic_owner civicconnect_test
export CIVIC_TEST_DB_URL=jdbc:postgresql://localhost:5432/civicconnect_test
export CIVIC_TEST_DB_USER=civic_owner CIVIC_TEST_DB_PASSWORD=OWNER_PW
mvn test                      # now also runs JdbcServiceRequestRepositoryDbTest + ReportConsistencyDbTest
```

## How other modules use this module

The request-lifecycle module (State pattern, ADR on design problem P1) decides whether a transition is allowed. It then calls this module to persist the change:

```java
ServiceRequestRepository repo =
    (ServiceRequestRepository) getServletContext().getAttribute(AppContextListener.REQUEST_REPOSITORY);

int newVersion = repo.changeStatus(new StatusChange(
        requestId, versionUserSaw,            // optimistic concurrency token
        RequestStatusCode.ASSIGNED,           // status the user saw (from)
        RequestStatusCode.IN_PROGRESS,        // validated target (to)
        currentUserId, null, "Work started", null));
// StaleUpdateException  -> someone else changed it: reload and ask the user to retry
// DataIntegrityException -> a database rule rejected it (e.g. ASSIGNED without assignee)
```

- `create(...)` and `changeStatus(...)` always write the history row **in the same transaction**. The database also refuses any commit where a status has no matching history row.
- Because `ServiceRequestRepository` is an interface, lifecycle rules can be unit-tested with an in-memory fake.
- The P2 Observer design (status-change reactions) does **not** need a "reporting" subscriber. The reports are views computed live from the tables, so there are no counters to keep in sync and no silent subscriber failure can make a report wrong (ADR-P3-03).

## Report URLs

| URL | Shows |
|---|---|
| `/dashboard` | KPI tiles, by-status, open-request ageing, monthly trend, category statistics, category×status heat map, reconciliation check |
| `/reports/requests?report=open` | Submitted, Assigned, In Progress, Reopened |
| `/reports/requests?report=overdue` | Open **and** `due_at` (category SLA) has passed |
| `/reports/requests?report=resolved` | Resolved (awaiting closure) |
| `/reports/requests?report=closed` | Closed + Rejected |
| `...&q=&status=&category=&priority=&assignee=(id\|none)&from=&to=&sort=&dir=&page=&size=` | search, filter, sort, page |
| `...&format=csv` | full filtered result as CSV (Excel / Power BI) |
| `/reports/request?ref=CC-000123` | request detail + complete audit trail |

## Current status and known limitations

- **Verified:** the SQL scripts run from scratch on PostgreSQL 16 (V0 to V4). The integrity rules were exercised against the live database: status without history, assignment without assignee, editing history, and deletes by the app role are all rejected. Every SQL statement the Java code generates was executed against the database (all 5 reports, all 11 sort fields in both directions, combined filters, the create, change-status and stale-update flows). All Java sources compile, and the 18 unit tests pass.
- **To do on a developer machine:** the development sandbox could not download Maven dependencies, so the first `mvn test` with `CIVIC_TEST_DB_URL` set and the first Tomcat deployment of the JSPs still need to be run and recorded as verification evidence.
- Text search uses `ILIKE`. That is fine at the expected volume; see ADR-P3-01 for the `pg_trgm` upgrade path.
- Month and "last 7 days" boundaries use the database time zone, which V0 sets to `Africa/Johannesburg`.
- Login/authentication belongs to the security module. This module only enforces the role check on its own pages.
