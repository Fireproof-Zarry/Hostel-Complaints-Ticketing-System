-- HostelFix: device token for Firebase Cloud Messaging push notifications (PostgreSQL).
--
-- Run once against the shared database (Supabase SQL editor or psql -f).
-- Spring does not run this file automatically. Safe to re-run.

ALTER TABLE users ADD COLUMN IF NOT EXISTS fcm_token VARCHAR;
