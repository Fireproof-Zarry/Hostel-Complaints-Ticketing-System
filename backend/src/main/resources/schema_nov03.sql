-- HostelFix: work orders for staff / in-charges (PostgreSQL).
--
-- Run in the Supabase SQL editor before deploying; safe to re-run.
-- users.role is a VARCHAR, so the new STAFF role needs no change.

ALTER TABLE work_orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Earlier versions allowed free-text priority/status; normalise to the enum values
UPDATE work_orders SET priority = UPPER(priority) WHERE priority <> UPPER(priority);
UPDATE work_orders SET priority = 'MEDIUM' WHERE priority NOT IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT');
UPDATE work_orders SET status = UPPER(status) WHERE status <> UPPER(status);
UPDATE work_orders SET status = 'OPEN' WHERE status NOT IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED');

-- At most one active work order per complaint (also stops two staff claiming
-- the same complaint at the same moment)
CREATE UNIQUE INDEX IF NOT EXISTS uk_work_orders_one_active_per_complaint
    ON work_orders (complaint_id) WHERE status IN ('OPEN', 'IN_PROGRESS');

-- An in-charge's queue
CREATE INDEX IF NOT EXISTS idx_work_orders_assignee_status ON work_orders (assignee_id, status);
