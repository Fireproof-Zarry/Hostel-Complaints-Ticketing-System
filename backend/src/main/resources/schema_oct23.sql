-- HostelFix: user profile fields and worker-coordination fields on complaints (PostgreSQL).
--
-- Run once against the shared database (Supabase SQL editor or psql -f),
-- AFTER schema_oct22.sql. Spring does not run this file automatically.
-- Not re-runnable: the columns already exist after the first run.

ALTER TABLE users ADD COLUMN full_name VARCHAR, ADD COLUMN phone_number VARCHAR, ADD COLUMN roll_number VARCHAR, ADD COLUMN room_number VARCHAR;
ALTER TABLE complaints ADD COLUMN available_time_slot VARCHAR, ADD COLUMN key_with_security BOOLEAN DEFAULT FALSE;
