-- HostelFix: students can delete (withdraw) their own PENDING complaints (PostgreSQL).
--
-- Soft delete: the row is kept with the time and reason, and Hibernate hides
-- it everywhere (Complaint is annotated @SQLRestriction("deleted_at IS NULL")).
-- Run in the Supabase SQL editor; Spring does not run this file. Safe to re-run.

ALTER TABLE complaints
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR;
