# M4 acceptance checks — NFR-04 and NFR-07

**Run and recorded by:** Nhlavutelo Shiviri
**Date:** 18 October 2026
**Build tested:** branch `m4-finalise`, commit `57dc961`, on simulated staging
(Tomcat 10.1.60 on port 8081, Vite dev server on port 8080, PostgreSQL 16, migrations V0–V7)
**Browser:** Google Chrome

## EV-01 — NFR-07: core flows on desktop and mobile viewports

**M1 acceptance criterion:** submit, view status and staff update are completable on a
representative desktop and mobile viewport without horizontal scroll or an unusable control.

**Method:** Chrome DevTools → Toggle device toolbar. Each flow run at **375 × 812** (phone)
and **1280 × 800** (desktop). Checked on every row: no sideways scrolling; every button and
field reachable and usable; text readable without zooming.

| # | Flow | Account | 375 px | 1280 px | Defect |
|---|---|---|---|---|---|
| 1 | Sign in | resident1@mail.example | PASS | PASS | — |
| 2 | Submit a request (all fields, reference number shown) | resident1@mail.example | PASS | PASS | — |
| 3 | My dashboard shows the request and its status | resident1@mail.example | PASS | PASS | — |
| 4 | Work queue → open request → assign | t.dlamini@civicconnect.example (coordinator) | PASS | PASS | — |
| 5 | Start → add a comment → resolve with notes | k.mahlangu@civicconnect.example (staff) | PASS | PASS | — |
| 6 | Close and rate | resident1@mail.example | PASS | PASS | — |

**Observations:** None. No screenshots were captured; results were recorded directly in this table.

**Result:** ☑ Met — all six flows pass on both viewport sizes.

## EV-02 — NFR-04: first-time user can submit without help

**M1 acceptance criterion:** a first-time user completes and submits a request without
external explanation of any field.

**Participant:** S.T. — fellow student (not on our team and has never seen CivicConnect before this)
**Date and time:** 18 October 2026, 19:13   **Device:** Laptop, using Google Chrome

**Script given (and nothing else):** "You noticed a burst pipe in your street. Please report it
using this site. I can't help you, but please think out loud." The observer stayed silent
and took notes.

| Observation | Notes |
|---|---|
| Time from landing page to reference number shown | 9 minutes 12 seconds (includes registering an account) |
| Asked for help or got stuck? On which field? | No |
| Any field misunderstood (entered the wrong thing)? | Typed the suburb into the address field at first, corrected it alone |
| Errors shown, and did they understand how to fix them? | "Password should be 15 characters long or more" — fixed it on his own |
| Completed without explanation? | YES |
| One thing they said was confusing or could be clearer | Nothing |

**Result:** ☑ Met — the participant completed a submission with no explanation.

## Outcome for the RTM

- **NFR-07:** Met (EV-01)
- **NFR-04:** Met (EV-02)
- **Defects raised:** None
