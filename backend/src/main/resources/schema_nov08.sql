-- HostelFix: work orders for complaints assigned before work orders existed (PostgreSQL).
--
-- Run in the Supabase SQL editor after schema_nov07.sql; safe to re-run.
-- The old Assign button only filled complaints.assigned_to, so these complaints show
-- an assignee but count as zero in Staff management and have no Work Order Details.
-- Only assignees who are still staff or admins are given a work order; nothing is pushed.

-- 1. Assigned-but-Pending complaints: log the move to ASSIGNED, with the assignee as the actor
INSERT INTO complaint_history (history_id, complaint_id, actor_id, old_status, new_status, reason, changed_at)
SELECT gen_random_uuid(), c.id, u.id, 'PENDING', 'ASSIGNED',
       'Assignment recorded when work orders were introduced', now()
FROM complaints c
JOIN users u ON lower(u.email) = lower(c.assigned_to)
WHERE c.status = 'PENDING'
  AND c.deleted_at IS NULL
  AND u.role IN ('STAFF', 'ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM work_orders w
      WHERE w.complaint_id = c.id AND w.status IN ('OPEN', 'IN_PROGRESS'));

-- 2. Then move them to ASSIGNED
UPDATE complaints c
SET status = 'ASSIGNED', version = c.version + 1, updated_at = now()
FROM users u
WHERE lower(u.email) = lower(c.assigned_to)
  AND c.status = 'PENDING'
  AND c.deleted_at IS NULL
  AND u.role IN ('STAFF', 'ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM work_orders w
      WHERE w.complaint_id = c.id AND w.status IN ('OPEN', 'IN_PROGRESS'));

-- 3. One work order for each open assigned complaint that has none
INSERT INTO work_orders (work_order_id, complaint_id, assignee_id, priority, status, created_at, updated_at)
SELECT gen_random_uuid(), c.id, u.id, 'MEDIUM',
       CASE WHEN c.status = 'IN_PROGRESS' THEN 'IN_PROGRESS' ELSE 'OPEN' END,
       now(), now()
FROM complaints c
JOIN users u ON lower(u.email) = lower(c.assigned_to)
WHERE c.status IN ('ASSIGNED', 'IN_PROGRESS')
  AND c.deleted_at IS NULL
  AND u.role IN ('STAFF', 'ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM work_orders w
      WHERE w.complaint_id = c.id AND w.status IN ('OPEN', 'IN_PROGRESS'));
