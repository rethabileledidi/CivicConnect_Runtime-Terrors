# ADR-P2-01: A JSON REST API on the existing Java/Tomcat backend, called by the React frontend

- **Status:** Proposed for M2 baseline (team review / 2 approvals required)
- **Owner:** Person 2 (Backend: security, lifecycle, notifications)
- **Related:** ADR-P3-01 (PostgreSQL + JDBC), ADR-P2-02, ADR-P2-03; PED v2.0 CR-01, ADR-01, ADR-02

## Context
The frontend (Person 1) is a React 19 single-page application built with TanStack Start and Vite. Until M2 it kept data in the browser's localStorage. The database and reporting module (Person 3) is Java 21 on Tomcat 10.1 with PostgreSQL, and it already exposes `ServiceRequestRepository` for the lifecycle module to call. The frontend needs sign-in, request submission, tracking, notifications and a staff work queue backed by the real database.

## Options considered
| Option | For | Against |
|---|---|---|
| **A. JSON REST API in the same Maven/Tomcat project (chosen)** | One backend language and one deployable. Reuses Rethabile's repository, transactions and views directly. Servlets are already the team's web technology | Hand-written servlets are more verbose than a framework |
| B. Spring Boot REST service | Less boilerplate, built-in security module | A second framework to learn mid-project. Would duplicate or rewrite the Tomcat module and its JNDI set-up |
| C. Node/Express or TanStack Start server functions talking to PostgreSQL | Same language as the frontend | Business rules would be split across two languages. Rethabile's Java persistence rules would be bypassed or duplicated |
| D. Keep localStorage | Nothing to build | No multi-user data, no security, no audit trail: fails FR-08, NFR-02, NFR-03 |

## Decision
Add package `com.civicconnect.api` (servlets + `ApiSecurityFilter`) on top of `com.civicconnect.service`, `lifecycle`, `auth` and `notification`. These call Rethabile's `com.civicconnect.data` layer. Jackson (databind + jsr310) handles JSON. During development the Vite dev server proxies `/api` to Tomcat, so the browser sees one origin.

## Consequences
- (+) One transaction boundary and one set of integrity rules for every client (NFR-06).
- (+) The layers stay one-directional: `api → service → lifecycle/auth/notification → data`. Business rules are unit-tested with in-memory fakes.
- (−) Servlets need manual routing and JSON mapping. `ApiServlet` keeps that in one place.
- (−) Two processes in development (Vite + Tomcat). A production build can serve the static React bundle from Tomcat or a static host. Deferred to the M3/M4 deployment decision.
- **Reversal condition:** if routing boilerplate becomes a schedule risk, move the same services behind Spring MVC controllers. The service layer does not change.
