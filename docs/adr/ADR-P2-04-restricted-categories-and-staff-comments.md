# ADR-P2-04: Restricted categories (FR-12) and append-only staff comments (FR-09)

- **Status:** Accepted for the M4 final baseline (closes CR-08 items under CR-11)
- **Owner:** Person 2 (backend). Schema reviewed by Person 3 (database); UI reviewed by Person 1 (frontend)
- **Requirements:** FR-09, FR-12, NFR-02, NFR-03; risks RR-03, R-19; debt TD-01, TD-02

## Design problem 1: how is a request marked as sensitive?
**Decision:** restriction is an attribute of the **category** (`request_category.is_restricted`, V7), not a flag on each request. V7 adds one restricted category, *Safety or Security Concern*, owned by a new *Community Safety* department.

**Why not a per-request flag?** The requester would have to set it correctly on every submission, and a forgotten flag leaks the report. A category rule is set once and cannot be skipped. **Cost:** a sensitive report filed under the wrong category is not protected; staff can still see it only if they are assigned or oversight.

## Design problem 2: who may see a restricted request?
**Decision:** the requester, the assigned staff member, MANAGER and ADMIN. COORDINATOR loses access (`AccessPolicy.canView`). A coordinator who opens one gets the same **404** as for any request they may not see (TC-05 rule), and restricted rows are left out of the coordinator's work queue (`listOpenQueue`), JSP report lists and CSV exports (`RequestQueryBuilder`).

**Interpretation of the M1 criterion.** FR-12 says an attempt is "denied rather than silently filtered". Opening a restricted request is denied, and the denial does not confirm that it exists (OWASP A01: no existence oracle). Lists leave the rows out because listing them, even redacted, would disclose titles and locations. Managers assign restricted requests; coordinators keep triage of everything else.

**Accepted gap:** dashboard tiles still count restricted requests. Counts reveal no title, location or requester.

## Design problem 3: how are staff comments kept trustworthy?
**Decision:** a separate `request_comment` table, written only by INSERT. The database sets `created_at` (`clock_timestamp()`), a trigger refuses UPDATE and DELETE, and the app role has no DELETE privilege. Comments are **internal**: staff assigned to the request and oversight roles read and add them; residents receive an empty list.

**Why not store comments as history rows?** History rows record status changes; the deferred trigger (ADR-P3-02) ties each one to a transition. A comment changes no status, so mixing them would weaken that rule.

## Consequences
- (+) RR-03 closed: coordinators no longer see security reports. FR-09 meets its M1 criterion: timestamped, attributed, not editable.
- (+) Tested at both layers: TC-24/TC-25 (service rules) and TC-26 (PostgreSQL rules). V7 is additive, so rollback is still "redeploy the previous WAR".
- (−) Two more places must honour the restricted rule (API and JSP reports). A new screen that lists requests must use `AccessPolicy` or `RequestQueryBuilder`, or it will leak.
- (−) The restricted category id is read per request load (one small indexed query). Acceptable at ~50 requests a day; cache it if load grows.
