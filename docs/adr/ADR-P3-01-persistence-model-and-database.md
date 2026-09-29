# ADR-P3-01: Relational persistence on PostgreSQL, accessed through JDBC

- **Status:** Proposed for M2 baseline (team review / 2 approvals required)
- **Owner:** Person 3 (Database & Management Reporting)
- **Related:** RTM rows REQ-DATA-01, REQ-RPT-01..06; ADR-P3-02; ADR-P3-03; A2 Task 2

## Context
CivicConnect stores service requests, their categories, the people involved and an auditable history of status changes. Management needs aggregate reports (by status, category, age and SLA). The data is highly relational: a request references a category, status, requester and assignee, and owns many history rows. Integrity matters more than write throughput, because accountability is a baselined requirement. The permitted tool list is IntelliJ/NetBeans, Java Servlets on Tomcat/GlassFish, C# .NET, SQL Server, PostgreSQL and Microsoft Power Platform. The team has three people and a fixed milestone schedule.

## Options considered
| Option | For | Against |
|---|---|---|
| **PostgreSQL + JDBC (chosen)** | Free, open source (no licence risk); strong constraints, deferrable constraint triggers, views, `FILTER` aggregates; runs locally on every OS; the team's A2 persistence research used PostgreSQL's concurrency/isolation documentation | Team must write SQL by hand (no ORM) |
| SQL Server + C# .NET | Mature tooling, EF Core | Would split the stack from the Java/Servlet UI work; Express edition size and OS limits; less alignment with A2 evidence |
| Microsoft Power Platform (Dataverse + Power BI) | Fast dashboards | Licensing and tenant needs; business rules less transparent and harder to unit-test; weak fit with the Git/PR/CI governance in A2 Task 4 |
| Document store | Flexible schema | Not on the permitted list; weaker multi-entity transactions and joins, which the reports need |

## Decision
Use PostgreSQL (tested on 16) in a dedicated `civic` schema, accessed with plain JDBC (pgjdbc 42.7) through a container-managed JNDI DataSource on Tomcat 10.1. All SQL is parameterised. Schema changes are versioned SQL scripts (`V0..V4`) kept in the repository.

## Consequences
- (+) Integrity rules sit next to the data and protect it from every code path, including admin tools.
- (+) Reporting uses SQL views, so figures are defined once (ADR-P3-03).
- (+) Least privilege: the web app connects as `civic_app` (SELECT/INSERT/UPDATE only, no DDL, no DELETE).
- (−) No ORM means more hand-written mapping. This is mitigated by a single shared row mapper (`RequestOverviewMapper`).
- (−) Single database instance = single point of failure. At this project's scale that is accepted. Mitigation: daily `pg_dump` backup and a documented restore. Read replicas and managed PostgreSQL are deferred until deployment is decided (Forward Engineering Consideration).
- Deferred: `pg_trgm` index for free-text search if volume grows; a migration tool (Flyway) once CI is in place. The scripts already use Flyway's `V<n>__name.sql` naming, so adopting it later needs no renames.

## Reversal condition
If the team standardises on C#/.NET, the SQL scripts are portable in concept, but the deferred trigger, `FILTER` and generated-column syntax would need rewriting for SQL Server. Record that as a change request.
