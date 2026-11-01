-- HostelFix: optimistic locking for complaints (PostgreSQL).
--
-- Complaint.version (@Version) is incremented on every update; an update based
-- on an outdated version fails instead of silently overwriting another admin's
-- change. Existing rows must start at 0, not NULL.
-- Run in the Supabase SQL editor before deploying; safe to re-run.

ALTER TABLE complaints ADD COLUMN IF NOT EXISTS version BIGINT;
UPDATE complaints SET version = 0 WHERE version IS NULL;
ALTER TABLE complaints ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE complaints ALTER COLUMN version SET NOT NULL;
