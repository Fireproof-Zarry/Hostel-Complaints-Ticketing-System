-- HostelFix: students can delete their own PENDING complaints (PostgreSQL).
--
-- Soft delete: deleting sets deleted_at and Hibernate hides the row everywhere
-- (Complaint is annotated @SQLRestriction("deleted_at IS NULL")).
-- ComplaintPurgeService hard-deletes rows 15 days after deleted_at.
-- Run in the Supabase SQL editor; Spring does not run this file. Safe to re-run.

ALTER TABLE complaints ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- Deletion reasons were dropped; removes the column if an earlier version added it
ALTER TABLE complaints DROP COLUMN IF EXISTS deletion_reason;

-- The nightly purge looks up complaints by deletion time
CREATE INDEX IF NOT EXISTS idx_complaints_deleted_at ON complaints (deleted_at) WHERE deleted_at IS NOT NULL;
