# ADR-P3-03: Management reports computed live from SQL views in one snapshot

- **Status:** Proposed for M2 baseline
- **Owner:** Person 3
- **Research evidence:** A2 Task 1 P2 (Observer: silent subscriber failure risk); A2 Task 2 section 1.5 (no cache without a defined staleness)

## Context
Management needs open, overdue, resolved and closed reports, statistics by category and status, and a dashboard. The figures must agree with each other and with the underlying records. A2's P2 analysis listed "update reporting" as one reaction to a status change, and identified silent subscriber failure as the main Observer risk.

## Options
| Option | Assessment |
|---|---|
| **A. Views over the live tables (chosen)** | One definition per metric; always current; nothing to keep in sync |
| B. Summary/counter tables updated by an Observer on each status change | Faster reads, but counters can drift if a subscriber fails silently; needs a repair job |
| C. Nightly materialised views / Power BI extract | Cheap reads, but figures are stale and staleness must be defined and shown |

## Decision
- `v_request_overview` holds the **only** definitions of lifecycle group, overdue (`OPEN AND due_at < now()`), age, resolution time and SLA compliance. Every report and statistic reads from it. Report types are presets over the same view (`ReportType`), so a dashboard tile and the list it links to use the same filter.
- `ReportService` runs every page's queries in **one read-only REPEATABLE READ transaction**. All tiles, charts and tables share one snapshot and one `now()`, and each report's "x of N" count and its rows come from the same snapshot.
- The dashboard shows a **reconciliation check** (status totals = category totals = open+resolved+closed = matrix total; overdue ≤ open; ageing = open). If a future change breaks consistency, managers see a warning instead of a wrong number.
- Reporting is therefore **not** an Observer subscriber in the P2 design. That removes one reaction, and its failure mode, from the status-change path.
- Search, filter and sort: every value is bound as a parameter. Sort columns come from a whitelist enum (`SortField`). LIKE wildcards are escaped. Paging always has a unique `request_id` tie-breaker, so pages never skip or repeat rows.
- CSV export neutralises formula injection for Excel and Power BI users.

## Consequences
- (+) Accuracy and consistency by construction, testable with `ReportConsistencyDbTest`.
- (−) Aggregates are computed on each dashboard load. With the expected thousands of requests and the supporting indexes, this is milliseconds. If volume grows, switch the heavy views to materialised views refreshed on a schedule, and show "data as of" (the dashboard already displays the snapshot time).
