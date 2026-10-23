-- HostelFix: user profile fields and worker-coordination fields on complaints (PostgreSQL).
--
-- Run against the shared database (Supabase SQL editor or psql -f), after
-- schema_oct22.sql. Spring does not run this file automatically.
--
-- Safe to re-run. The backend uses spring.jpa.hibernate.ddl-auto=update, so if
-- it was started with the new entities before this script ran, Hibernate has
-- already added some or all of these columns (without the FALSE default).

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS full_name VARCHAR,
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR,
    ADD COLUMN IF NOT EXISTS roll_number VARCHAR,
    ADD COLUMN IF NOT EXISTS room_number VARCHAR;

ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS available_time_slot VARCHAR,
    ADD COLUMN IF NOT EXISTS key_with_security BOOLEAN DEFAULT FALSE;

-- A column created by Hibernate has no default and NULL in existing rows
ALTER TABLE complaints ALTER COLUMN key_with_security SET DEFAULT FALSE;
UPDATE complaints SET key_with_security = FALSE WHERE key_with_security IS NULL;
