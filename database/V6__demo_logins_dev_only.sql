-- =============================================================================
-- CivicConnect | V6 - DEMO LOGINS (development / demo ONLY - never production)
-- Owner: Person 2 (Backend). Run after V4 (sample data) and V5.
--
-- Gives five of the fictional V4 users a password so every role can be demonstrated.
-- Password for all five: CivicConnect-Demo-2026   (22 characters; NIST minimum is 15)
-- The value below is a PBKDF2-HMAC-SHA256 hash (600,000 iterations), not the password.
-- A fixed salt is used ONLY so this script is repeatable; real accounts get a random salt.
-- =============================================================================

SET search_path = civic;

UPDATE app_user
   SET password_hash = 'pbkdf2_sha256$600000$Q2l2aWNEZW1vU2FsdC0yNg==$mz647rWd1VdISkJPsSkIHEt63y2egGCQOebGOuOkkM8=',
       phone = COALESCE(phone, '0821234567')
 WHERE email IN ('resident1@mail.example',            -- RESIDENT
                 't.dlamini@civicconnect.example',    -- COORDINATOR
                 'k.mahlangu@civicconnect.example',   -- STAFF (Water and Sanitation)
                 'n.mokoena@civicconnect.example',    -- MANAGER
                 'admin@civicconnect.example');       -- ADMIN
