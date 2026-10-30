-- HostelFix: soft-archiving of old RESOLVED / REJECTED complaints (PostgreSQL).
--
-- Run against the shared database (Supabase SQL editor or psql -f) BEFORE
-- starting the backend with the new Complaint entity: Hibernate
-- (ddl-auto=update) cannot add a NOT NULL column to a table that has rows.
-- Spring does not run this file automatically. Safe to re-run.

ALTER TABLE complaints ADD COLUMN IF NOT EXISTS is_archived BOOLEAN NOT NULL DEFAULT FALSE;

-- The admin dashboard only reads non-archived complaints
CREATE INDEX IF NOT EXISTS idx_active_complaints ON complaints (status) WHERE is_archived = false;
