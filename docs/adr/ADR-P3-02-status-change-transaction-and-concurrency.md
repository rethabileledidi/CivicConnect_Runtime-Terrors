# ADR-P3-02: Status change and history in one transaction, with optimistic concurrency

- **Status:** Proposed for M2 baseline
- **Owner:** Person 3
- **Research evidence:** A2 Task 2 (Persistence and Data-Integrity Decisions), sections 1.2 to 1.7. A2 compared Approach 1 (one atomic service transaction) with Approach 2 (commit status first, write the audit record afterwards).

## Context
M1 requires every status change to be attributable to a user and a time, with a history that cannot be altered later. Management reports (open/overdue/resolved/closed) must match the audit trail. Two staff members can act on the same request at the same moment.

## Decision
1. **Atomic boundary:** `JdbcServiceRequestRepository.changeStatus()` updates `service_request` and inserts the `request_status_history` row in **one** READ COMMITTED transaction owned by `TransactionRunner`. Any failure rolls back both (A2 Approach 1).
2. **Database backstop:** a `DEFERRABLE INITIALLY DEFERRED` constraint trigger rejects the COMMIT if a request's current status differs from its latest history row. This holds even for a path that bypasses the repository.
3. **Optimistic concurrency:** each update is `WHERE request_id = ? AND version = ? AND status_code = ?`, followed by `version + 1`. If no row matches, the repository raises `StaleUpdateException`, and the UI asks the user to reload (A2 Task 2 section 1.4: proportionate for the expected workload; SERIALIZABLE everywhere was rejected because of retry cost).
4. **Layered validation:** transition *permission* is decided by the lifecycle module's State pattern. Data *obligations* (assignee present, resolution recorded, timestamps) are also CHECK constraints. The UI only offers valid choices.
5. **History is append-only:** a trigger blocks UPDATE/DELETE on `request_status_history`, and the app role has no DELETE grant.
6. **No caching on the write path** (A2 Task 2 section 1.5).

## Consequences
- (+) A report can never count a status the audit trail cannot explain.
- (+) Lost updates are impossible, and conflicts are reported clearly.
- (−) A slightly longer transaction, and users occasionally see a "changed by someone else, reload" message.
- (−) The deferred trigger adds one indexed lookup per status change (negligible at this scale).
- Notifications stay **outside** this transaction (A2 Task 3). A notification failure must not undo a valid status change.

## Verification
`JdbcServiceRequestRepositoryDbTest`: atomic create/change, stale version rejected with nothing written, assignment without assignee rolled back, status-without-history rejected at commit, history immutable. The same rules were also exercised directly in SQL against PostgreSQL 16 during development.
