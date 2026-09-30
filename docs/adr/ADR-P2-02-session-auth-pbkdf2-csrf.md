# ADR-P2-02: Server-side sessions, PBKDF2 passwords, lockout and a custom-header CSRF check

- **Status:** Proposed for M2 baseline
- **Owner:** Person 2
- **Related:** FR-12, NFR-02, NFR-10; OWASP Top 10:2025 A01/A07; ADR-P3-01 (`app_user`)

## Context
Residents register themselves. Staff, coordinators and managers are created by an administrator. Rethabile's `ManagementAccessFilter` already expects the session attribute `userRole`. The frontend and API share one origin through the dev proxy.

## Options considered
| Option | Assessment |
|---|---|
| **Server-side session + HttpOnly cookie (chosen)** | Tomcat already manages sessions; logout really ends the session; matches `ManagementAccessFilter` |
| JWT bearer tokens in browser storage | Readable by any injected script (XSS); revocation needs extra state; no benefit on a single origin |
| External identity provider (OAuth/OIDC) | Strong, but adds a third-party dependency and set-up beyond the M2 scope |

## Decision
- **Passwords:** PBKDF2-HMAC-SHA256 from the JDK, with 600,000 iterations, a 16-byte random salt and a constant-time compare. Stored as `pbkdf2_sha256$iter$salt$hash`, so the iteration count can rise later.
- **Policy:** 15–128 characters with no composition rules (NIST SP 800-63B-4 single-factor minimum). The server re-validates everything the React form checks.
- **Lockout:** 5 consecutive failures lock the account for 15 minutes. The state is stored in `app_user` (V5), so a restart does not reset it. Unknown email and wrong password return the same message.
- **Session:** `changeSessionId()` at sign-in (session fixation), HttpOnly cookie, 30-minute timeout (web.xml). Recommended: SameSite=Lax via Tomcat's cookie processor.
- **CSRF:** every state-changing call needs `X-Requested-With: CivicConnect` and a JSON body. Cross-site forms cannot set custom headers without a CORS pre-flight, and the API grants none by default.
- **Authorisation:** deny by default in `AccessPolicy`. "Not yours" returns 404, not 403.

## Consequences
- (+) No password or token is ever readable by page scripts. Credentials never appear in logs.
- (−) Sessions are held in Tomcat memory, so a restart signs everyone out and several servers would need sticky sessions. Accepted for a single-server prototype.
- (−) No MFA yet. That is why the 15-character single-factor minimum applies. MFA for staff roles is a deferred decision (evidence needed: stakeholder risk appetite).
