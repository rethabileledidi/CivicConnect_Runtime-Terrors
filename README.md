# CivicConnect

[![CI](https://github.com/rethabileledidi/CivicConnect_Runtime-Terrors/actions/workflows/ci.yml/badge.svg)](https://github.com/rethabileledidi/CivicConnect_Runtime-Terrors/actions/workflows/ci.yml)

CivicConnect is a web platform where residents report municipal service problems such as potholes, burst pipes and broken streetlights. They can then follow each request until it is resolved. Municipal staff work through the requests in a queue, and managers see live reports.

*SEN381 Software Engineering project by **Runtime Terrors**.*

## Features

- **Residents:** register, log a request with a category and priority, track its status, get notified, and rate the outcome.
- **Coordinators:** assign requests to staff or reject them.
- **Staff:** start and resolve the requests assigned to them.
- **Managers:** use the management dashboard with open, overdue, resolved and closed reports, statistics by category and status, search and filtering, and CSV export.
- **Lifecycle:** `Submitted → Assigned → In Progress → Resolved → Closed` (requests can also be `Rejected` or `Reopened`). Every change is recorded in an append-only audit history.
- **SLA tracking:** each category has a target time, and overdue requests appear first in the queue.
- **Notifications:** in-app messages, plus simulated SMS and WhatsApp messages sent through an outbox with retries.

## Architecture

```
React 19 (Vite)  ──/api──▶  Java 21 REST API on Tomcat 10.1  ──JDBC──▶  PostgreSQL 16
   :8080                     :8081/civicconnect                         schema "civic"
```

| Layer | Tech | Owner |
|---|---|---|
| Frontend | React 19, TypeScript, Vite, Tailwind | Nhlavutelo |
| Backend | Java 21, Jakarta Servlets, REST/JSON, session auth (PBKDF2 + CSRF) | Tsholofelo |
| Database & reporting | PostgreSQL 16, SQL views, JSP management dashboard | Rethabile |

Design patterns used: **State** (request lifecycle), **Observer** (status-change notifications), **Repository** (data access).

## Quick start

You need JDK 21, Maven, Tomcat 10.1, PostgreSQL 16 and Node or Bun.

1. **Database:** run `database/V0` to `V6` in order.
2. **Backend:** set `CIVIC_DB_URL`, `CIVIC_DB_USER` and `CIVIC_DB_PASSWORD` (see `.env.example`), run `mvn package`, and deploy to Tomcat on port **8081**.
3. **Frontend:** in `CivicConnect-Frontend/`, run `bun install` and then `bun run dev`. The app opens at http://localhost:8080.

Full step-by-step instructions are in **[RUNNING.md](RUNNING.md)**. To check that the backend is up, open `http://localhost:8081/civicconnect/api/health`.

### Demo accounts (dev only)

The password for all of them is `CivicConnect-Demo-2026`.

| Email | Role |
|---|---|
| `resident1@mail.example` | Resident |
| `t.dlamini@civicconnect.example` | Coordinator |
| `k.mahlangu@civicconnect.example` | Staff |
| `n.mokoena@civicconnect.example` | Manager |
| `admin@civicconnect.example` | Admin |

## Tests

```bash
mvn test      # unit tests
mvn verify    # + database tests when CIVIC_TEST_DB_URL points at a THROW-AWAY database
```

GitHub Actions runs the backend tests (against a disposable PostgreSQL) and the frontend build on every push and pull request.

## Project layout

```
CivicConnect-Frontend/   React app
src/main/java/...        api · auth · service · lifecycle · notification · data · reporting · web
src/main/webapp/         JSP dashboard, context.xml (no secrets committed)
database/                V0–V6 SQL migrations (V4 and V6 are demo data only)
docs/                    API reference, data model, ADRs
```

## Documentation

- [RUNNING.md](RUNNING.md): run the whole system locally
- [docs/backend-api.md](docs/backend-api.md): REST endpoints and lifecycle rules
- [docs/data-model.md](docs/data-model.md): ERD and schema notes
- [docs/adr/](docs/adr/): architecture decision records
- [docs/database-and-reporting.md](docs/database-and-reporting.md): database and reporting module in detail

## Team: Runtime Terrors

| Member | Role |
|---|---|
| Tsholofelo Lephondo | Backend Developer |
| Nhlavutelo Shiviri | Frontend Developer |
| Rethabile Ledidi | Database Developer |
