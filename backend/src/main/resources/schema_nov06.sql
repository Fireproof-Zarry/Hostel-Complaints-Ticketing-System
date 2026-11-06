-- HostelFix: allow the STAFF role on users (PostgreSQL).
--
-- Run in the Supabase SQL editor before deploying; safe to re-run.
-- Hibernate created users.role with a check listing the roles of the time, and
-- ddl-auto=update never changes a check, so adding staff failed with a server error.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('STUDENT', 'ADMIN', 'STAFF'));
