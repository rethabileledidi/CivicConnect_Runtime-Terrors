## What does this PR change?
<!-- One or two sentences. Link the issue: "Closes #12" -->

## Requirement / decision trace
- Requirement(s): <!-- e.g. FR-08, NFR-03 -->
- ADR / change request: <!-- e.g. ADR-P2-03, CR-05 -->
- RTM row updated? <!-- yes / no / not needed -->

## How was it verified?
- [ ] CI is green (backend + frontend)
- [ ] New or changed rules have unit tests
- [ ] Ran it locally (say what you clicked or called)

## Reviewer checklist
- [ ] Rules live in the service/lifecycle layer, not in a servlet or React component
- [ ] Every SQL value is a bound parameter; no string-built SQL
- [ ] No secrets, passwords or real personal data committed
- [ ] Schema changes (database/V*.sql) approved by the database owner (Rethabile)
- [ ] AI-assisted code is listed in the AI Usage Register and was read and understood by the author
