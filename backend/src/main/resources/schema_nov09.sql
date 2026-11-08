-- HostelFix: the hostel's resident list (PostgreSQL).
--
-- Run in the Supabase SQL editor after schema_nov08.sql; safe to re-run.
-- While this table has any rows, only students whose roll number is in it can use the
-- student portal (their email is rollno@smail.iitm.ac.in). An empty table lets every
-- verified smail student in, so creating it changes nothing until the first upload.
-- Admins fill it from the Residents page; staff and admins are never affected.

CREATE TABLE IF NOT EXISTS residents (
    roll_number VARCHAR(8) PRIMARY KEY
        CHECK (roll_number ~ '^[a-z]{2}[0-9]{2}[a-z][0-9]{3}$'),
    added_at TIMESTAMP NOT NULL DEFAULT now(),
    added_by VARCHAR(255)
);
