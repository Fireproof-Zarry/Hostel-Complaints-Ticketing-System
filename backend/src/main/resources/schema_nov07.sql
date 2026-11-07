-- HostelFix: remember when a complaint's owner last nudged the admins (PostgreSQL).
--
-- Run in the Supabase SQL editor before deploying; safe to re-run.

ALTER TABLE complaints ADD COLUMN IF NOT EXISTS last_nudged_at TIMESTAMP(6);
