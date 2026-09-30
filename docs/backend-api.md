# CivicConnect REST API (Backend, Person 2)

Base path: `/civicconnect/api` on Tomcat. In development the React app calls `/api/...` and the Vite
dev server forwards it (see `CivicConnect-Frontend/vite.config.ts`).

**Conventions**
- JSON in and out (`Content-Type: application/json`). Dates are ISO-8601 strings.
- Authentication is a server-side session (HttpOnly `JSESSIONID` cookie). A new session ID is issued at sign-in.
- Every `POST` must send `X-Requested-With: CivicConnect` (CSRF defence, `ApiSecurityFilter`). Otherwise it gets `403 CSRF_CHECK_FAILED`.
- Errors: `{"error": "CODE", "message": "human text", "fieldErrors": {"field": "message"}}`.

| Status | `error` codes | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `BAD_JSON` | Input broke a rule. `fieldErrors` says which field |
| 401 | `UNAUTHENTICATED` | Not signed in, or wrong email/password (same message for both) |
| 403 | `FORBIDDEN`, `CSRF_CHECK_FAILED` | Signed in, but this role may not do this |
| 404 | `NOT_FOUND` | Missing, **or not yours** (stops reference-number probing, NFR-02) |
| 409 | `STALE_REQUEST`, `EMAIL_TAKEN`, `FEEDBACK_EXISTS`, `INTEGRITY_RULE` | Conflict. `STALE_REQUEST` means reload and retry |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Body was not JSON |
| 422 | `TRANSITION_NOT_ALLOWED` | The lifecycle (State pattern) refused the move |
| 423 | `ACCOUNT_LOCKED` | 5 failed sign-ins. Locked for 15 minutes |

## Endpoints

| Method & path | Who | Body | Returns |
|---|---|---|---|
| `POST /auth/register` | anyone | `fullName, email, phone, municipality, password (15+), confirmPassword` | `201` user (always role RESIDENT) |
| `POST /auth/login` | anyone | `email, password` | `200` user |
| `POST /auth/logout` | signed in | none | `204` |
| `GET /auth/me` | signed in | none | `200` user, or `401` |
| `GET /categories` | anyone | none | active categories with `slaHours` |
| `GET /requests?scope=mine` | signed in | none | my requests (summaries) |
| `GET /requests?scope=queue&status=ASSIGNED` | STAFF, COORDINATOR, MANAGER, ADMIN | none | open work queue, overdue first. Staff see only their assignments |
| `POST /requests` | signed in | `title, description, categoryId, priority (LOW/MEDIUM/HIGH/URGENT), address, suburb, city, contactPhone?` | `201` detail |
| `GET /requests/{id}` | owner, assignee, oversight | none | detail: `request, history[], actions[], feedback, canGiveFeedback, messages[]` |
| `POST /requests/{id}/status` | depends on the move (below) | `target, expectedVersion, assigneeId?, note?, resolutionNotes?` | `{request: detail, warnings: []}` |
| `POST /requests/{id}/feedback` | requester, once, when Resolved/Closed | `rating (1-5), comment?` | detail |
| `GET /staff` | COORDINATOR, MANAGER, ADMIN | none | active staff for "assign to" |
| `GET /notifications` | signed in | none | `{items: [...], unread: n}` |
| `POST /notifications/read` | signed in | none | `{updated: n}` |
| `GET /health` | anyone | none | `200 {"status":"UP"}` or `503` if the database is down |

## Lifecycle moves (`POST /requests/{id}/status`)

| From → To | Allowed actor | Must include |
|---|---|---|
| SUBMITTED → ASSIGNED | coordinator / manager / admin | `assigneeId` (an active STAFF user) |
| SUBMITTED → REJECTED | coordinator / manager / admin | `note` |
| ASSIGNED → IN_PROGRESS | the assigned staff member | none |
| IN_PROGRESS → RESOLVED | the assigned staff member | `resolutionNotes` |
| RESOLVED → CLOSED | requester or coordinator / manager / admin | none |
| RESOLVED → REOPENED | requester only | `note` |
| REOPENED → ASSIGNED | coordinator / manager / admin | `assigneeId` |
| REOPENED → RESOLVED | the assigned staff member | `resolutionNotes` |

Every other pair (34 of 42) is refused with `422`. `expectedVersion` is the `version` the user saw. If
someone else changed the request in the meantime, the answer is `409 STALE_REQUEST` and nothing is written.

## Order of work for a status change (FR-08 trace)

`RequestsServlet` → `RequestService.changeStatus` → `AccessPolicy.canView` → `RequestLifecycle.plan`
(State pattern) → `ServiceRequestRepository.changeStatus` (Rethabile: one transaction, status + history,
version check) → **commit** → `StatusChangePublisher` → `InAppNotificationListener`,
`SimulatedMessagingListener` (Observer). A listener failure is logged and returned as a warning. It never
undoes the change.

## Demo accounts (after `V6__demo_logins_dev_only.sql`)

Password for all: `CivicConnect-Demo-2026`

| Email | Role |
|---|---|
| `resident1@mail.example` | RESIDENT |
| `k.mahlangu@civicconnect.example` | STAFF (Water and Sanitation) |
| `t.dlamini@civicconnect.example` | COORDINATOR |
| `n.mokoena@civicconnect.example` | MANAGER |
| `admin@civicconnect.example` | ADMIN |
