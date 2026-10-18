-- =============================================================================
-- CivicConnect | V7 - Pin search_path on the V1 trigger functions (hotfix)
-- Owner: Person 3 (Database). Run as civic_owner AFTER V1..V6:
--   psql -h <host> -U civic_owner -d civicconnect -f V7__pin_trigger_search_path.sql
--
-- Why: databases built from V1 before commit ac5de97 have trigger functions that
-- name tables WITHOUT the civic. prefix (request_category, service_request,
-- request_status_history). If the connection's search_path is not "civic"
-- (app connects as another user, the role default was lost, a pooler resets it),
-- every INSERT into civic.service_request fails with:
--   ERROR: relation "request_category" does not exist
--   CONTEXT: PL/pgSQL function civic.trg_request_defaults() line 5
-- so residents cannot log new requests, while reads and most other writes still work.
--
-- ac5de97 fixed V1 for NEW databases only; already-built databases (staging) still
-- have the old functions. This script patches them in place. It is idempotent and
-- harmless on databases that already have the fix. No data is touched.
-- =============================================================================

ALTER FUNCTION civic.trg_request_defaults()           SET search_path = civic;
ALTER FUNCTION civic.trg_history_append_only()        SET search_path = civic;
ALTER FUNCTION civic.trg_request_status_has_history() SET search_path = civic;

