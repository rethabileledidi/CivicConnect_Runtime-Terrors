-- =============================================================================
-- CivicConnect | V0 - Database and least-privilege roles (run ONCE as a superuser)
-- Owner: Person 3 (Database & Management Reporting Module)
--
-- Usage (psql, from the database/ folder):
--   psql -U postgres -v app_password='choose-a-password' -v owner_password='choose-another' -f V0__create_database_and_roles.sql
--
-- No real passwords are stored in this repository. Passwords are supplied at run time
-- through psql variables, following the team's secrets rule (A2 Task 4, section 4).
-- =============================================================================

-- civic_owner : owns the schema and runs the migration scripts V1..V4
-- civic_app   : used by the Tomcat web application (SELECT/INSERT/UPDATE only, no DDL)
CREATE ROLE civic_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE civic_app   LOGIN PASSWORD :'app_password';

CREATE DATABASE civicconnect OWNER civic_owner ENCODING 'UTF8' TEMPLATE template0;
-- Timestamps are stored as TIMESTAMPTZ (UTC internally); reports display in SAST.
ALTER DATABASE civicconnect SET timezone = 'Africa/Johannesburg';

\connect civicconnect

-- Keep the public schema closed; everything lives in the "civic" schema.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA civic AUTHORIZATION civic_owner;
GRANT USAGE ON SCHEMA civic TO civic_app;

-- Every table/sequence civic_owner creates later is automatically usable by the app role,
-- but the app role can never DELETE (history and requests are retained for accountability).
ALTER DEFAULT PRIVILEGES FOR ROLE civic_owner IN SCHEMA civic
    GRANT SELECT, INSERT, UPDATE ON TABLES TO civic_app;
ALTER DEFAULT PRIVILEGES FOR ROLE civic_owner IN SCHEMA civic
    GRANT USAGE, SELECT ON SEQUENCES TO civic_app;

ALTER ROLE civic_owner SET search_path = civic;
ALTER ROLE civic_app   SET search_path = civic;
