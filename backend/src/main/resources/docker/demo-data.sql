-- Demo data for the all-in-one Docker image only (DockerDatabaseSetup runs this once,
-- into the container's own empty database). Never run this against Supabase.
--
-- Bhadra is a girls' hostel. Staff are the hostel office members who take charge of
-- complaints and arrange the repair; they are not the workers.
--
-- The demo students and staff below cannot sign in: their
-- addresses are made up. Whoever runs the image signs in with their own Google account.
-- Every complaint follows the real status workflow, with matching history and work orders.

-- ---- People ----
INSERT INTO users (id, email, name, full_name, role, phone_number, roll_number, room_number) VALUES
    ('d0000000-0000-0000-0000-000000000001', 'demo.ananya@smail.iitm.ac.in', 'Ananya Iyer',    'Ananya Iyer',    'STUDENT', '9000000001', 'CS22B014', '214'),
    ('d0000000-0000-0000-0000-000000000002', 'demo.meera@smail.iitm.ac.in',  'Meera Krishnan', 'Meera Krishnan', 'STUDENT', '9000000002', 'ED21B032', '305'),
    ('d0000000-0000-0000-0000-000000000003', 'demo.divya@smail.iitm.ac.in',  'Divya Raghavan', 'Divya Raghavan', 'STUDENT', '9000000003', 'ME23B045', '412'),
    ('d0000000-0000-0000-0000-000000000004', 'demo.sneha@smail.iitm.ac.in',  'Sneha Pillai',   'Sneha Pillai',   'STUDENT', '9000000004', 'EE22M007', '118'),
    ('d0000000-0000-0000-0000-000000000005', 'demo.kavya@smail.iitm.ac.in',  'Kavya Menon',    'Kavya Menon',    'STUDENT', '9000000005', 'BT21D003', '509'),
    ('d0000000-0000-0000-0000-000000000101', 'lakshmi.demo@example.com', 'Lakshmi R', 'Lakshmi R', 'STAFF', '9000000101', NULL, NULL),
    ('d0000000-0000-0000-0000-000000000102', 'ramesh.demo@example.com',  'Ramesh K',  'Ramesh K',  'STAFF', '9000000102', NULL, NULL),
    ('d0000000-0000-0000-0000-000000000103', 'anitha.demo@example.com',  'Anitha M',  'Anitha M',  'STAFF', '9000000103', NULL, NULL),
    ('d0000000-0000-0000-0000-000000000104', 'priya.demo@example.com',   'Priya S',   'Priya S',   'STAFF', '9000000104', NULL, NULL);

-- ---- Places: five rooms and four community areas ----
INSERT INTO locations (location_id, floor, room_number, wing, is_community_area) VALUES
    ('e0000000-0000-0000-0000-000000000001', 'First',  '214', NULL, false),
    ('e0000000-0000-0000-0000-000000000002', 'Second', '305', NULL, false),
    ('e0000000-0000-0000-0000-000000000003', 'Third',  '412', NULL, false),
    ('e0000000-0000-0000-0000-000000000004', 'Ground', '118', NULL, false),
    ('e0000000-0000-0000-0000-000000000005', 'Fourth', '509', NULL, false),
    ('e0000000-0000-0000-0000-000000000011', 'First',  NULL, 'East wing washroom', true),
    ('e0000000-0000-0000-0000-000000000012', 'Second', NULL, 'Corridor near rooms 310-320', true),
    ('e0000000-0000-0000-0000-000000000013', 'Ground', NULL, 'Common room', true),
    ('e0000000-0000-0000-0000-000000000014', 'Fourth', NULL, 'Water dispenser near the stairs', true);

-- ---- Complaints ----
INSERT INTO complaints (id, version, title, description, category_id, location_id, status, assigned_to,
                        available_time_slot, key_with_security, is_archived, last_nudged_at, student_id,
                        created_at, updated_at)
SELECT c.id, 0, c.title, c.description, cat.category_id, c.location_id::uuid, c.status, c.assigned_to,
       c.slot, c.key_with_security, false, c.last_nudged_at, c.student_id, c.created_at, c.updated_at
FROM (VALUES
    (1, 'Ceiling fan not working',
        'The ceiling fan in my room stopped working last night. The regulator turns but the fan does not move at all.',
        'Electrical', 'e0000000-0000-0000-0000-000000000001', 'IN_PROGRESS', 'lakshmi.demo@example.com',
        'Mon: 5 PM - 8 PM', false, NULL::timestamp, 'd0000000-0000-0000-0000-000000000001',
        now() - interval '3 days', now() - interval '2 days'),
    (2, 'Tap leaking in the bathroom',
        'The tap in the attached bathroom keeps dripping even when fully closed. The floor stays wet all day.',
        'Plumbing', 'e0000000-0000-0000-0000-000000000002', 'ASSIGNED', 'ramesh.demo@example.com',
        'Tue: 2 PM - 5 PM', false, NULL, 'd0000000-0000-0000-0000-000000000002',
        now() - interval '2 days', now() - interval '1 day'),
    (3, 'Washroom not cleaned since morning',
        'The first floor east wing washroom has not been cleaned today. The dustbins are overflowing.',
        'Cleaning', 'e0000000-0000-0000-0000-000000000011', 'PENDING', NULL,
        NULL, false, NULL, 'd0000000-0000-0000-0000-000000000001',
        now() - interval '5 hours', now() - interval '5 hours'),
    (4, 'Corridor lights flickering',
        'Two tube lights in the second floor corridor near rooms 310-320 keep flickering, and one is completely off. It is very dark at night.',
        'Electrical', 'e0000000-0000-0000-0000-000000000012', 'PENDING', NULL,
        NULL, false, now() - interval '2 days', 'd0000000-0000-0000-0000-000000000002',
        now() - interval '10 days', now() - interval '2 days'),
    (5, 'Cupboard door hinge broken',
        'The top hinge of my cupboard door came off and the door does not close.',
        'Carpentry', 'e0000000-0000-0000-0000-000000000003', 'RESOLVED', 'anitha.demo@example.com',
        'Wed: 10 AM - 1 PM', true, NULL, 'd0000000-0000-0000-0000-000000000003',
        now() - interval '12 days', now() - interval '9 days'),
    (6, 'LAN port not working',
        'The LAN port near my study table does not connect. Wi-Fi works but is slow for online exams.',
        'IT', 'e0000000-0000-0000-0000-000000000004', 'REJECTED', NULL,
        'Thu: 4 PM - 7 PM', false, NULL, 'd0000000-0000-0000-0000-000000000004',
        now() - interval '6 days', now() - interval '5 days'),
    (7, 'Wi-Fi very slow in the common room',
        'The Wi-Fi in the ground floor common room keeps disconnecting in the evenings.',
        'IT', 'e0000000-0000-0000-0000-000000000013', 'IN_PROGRESS', 'lakshmi.demo@example.com',
        NULL, false, NULL, 'd0000000-0000-0000-0000-000000000004',
        now() - interval '2 days', now() - interval '1 day'),
    (8, 'Water dispenser leaking',
        'Water is leaking from under the dispenser near the fourth floor stairs and the floor is slippery.',
        'Plumbing', 'e0000000-0000-0000-0000-000000000014', 'PENDING', NULL,
        NULL, false, NULL, 'd0000000-0000-0000-0000-000000000005',
        now() - interval '1 day', now() - interval '1 day'),
    (9, 'Geyser not heating',
        'The geyser in my bathroom switches on but the water stays cold.',
        'Electrical', 'e0000000-0000-0000-0000-000000000005', 'ASSIGNED', 'lakshmi.demo@example.com',
        'Fri: 9 AM - 12 PM', true, NULL, 'd0000000-0000-0000-0000-000000000005',
        now() - interval '1 day', now() - interval '12 hours'),
    (10, 'Bathroom drain blocked',
        'The bathroom floor drain is blocked and water collects after every bath.',
        'Plumbing', 'e0000000-0000-0000-0000-000000000003', 'RESOLVED', 'ramesh.demo@example.com',
        'Sat: 10 AM - 1 PM', false, NULL, 'd0000000-0000-0000-0000-000000000003',
        now() - interval '20 days', now() - interval '18 days')
) AS c(id, title, description, category, location_id, status, assigned_to, slot, key_with_security,
       last_nudged_at, student_id, created_at, updated_at)
JOIN categories cat ON cat.name = c.category;

-- Later complaints get the next ids
SELECT setval(pg_get_serial_sequence('complaints', 'id'), (SELECT max(id) FROM complaints));

-- ---- Status history, following the workflow ----
INSERT INTO complaint_history (history_id, complaint_id, actor_id, old_status, new_status, reason, changed_at) VALUES
    (gen_random_uuid(), 1, 'd0000000-0000-0000-0000-000000000101', 'PENDING', 'ASSIGNED', NULL, now() - interval '3 days' + interval '2 hours'),
    (gen_random_uuid(), 1, 'd0000000-0000-0000-0000-000000000101', 'ASSIGNED', 'IN_PROGRESS', 'The electrician checked the fan; the capacitor needs replacing. Waiting for the part.', now() - interval '2 days'),
    (gen_random_uuid(), 2, 'd0000000-0000-0000-0000-000000000102', 'PENDING', 'ASSIGNED', NULL, now() - interval '1 day'),
    (gen_random_uuid(), 5, 'd0000000-0000-0000-0000-000000000103', 'PENDING', 'ASSIGNED', NULL, now() - interval '11 days'),
    (gen_random_uuid(), 5, 'd0000000-0000-0000-0000-000000000103', 'ASSIGNED', 'IN_PROGRESS', NULL, now() - interval '10 days'),
    (gen_random_uuid(), 5, 'd0000000-0000-0000-0000-000000000103', 'IN_PROGRESS', 'RESOLVED', 'The carpenter replaced both hinges.', now() - interval '9 days'),
    (gen_random_uuid(), 6, 'd0000000-0000-0000-0000-000000000101', 'PENDING', 'REJECTED', 'The network team checked the port and it works. The laptop''s LAN adapter was turned off in its settings.', now() - interval '5 days'),
    (gen_random_uuid(), 7, 'd0000000-0000-0000-0000-000000000101', 'PENDING', 'ASSIGNED', NULL, now() - interval '2 days' + interval '3 hours'),
    (gen_random_uuid(), 7, 'd0000000-0000-0000-0000-000000000101', 'ASSIGNED', 'IN_PROGRESS', 'The network team says the access point is overloaded in the evenings; a second one has been requested.', now() - interval '1 day'),
    (gen_random_uuid(), 9, 'd0000000-0000-0000-0000-000000000101', 'PENDING', 'ASSIGNED', NULL, now() - interval '12 hours'),
    (gen_random_uuid(), 10, 'd0000000-0000-0000-0000-000000000102', 'PENDING', 'ASSIGNED', NULL, now() - interval '19 days'),
    (gen_random_uuid(), 10, 'd0000000-0000-0000-0000-000000000102', 'ASSIGNED', 'IN_PROGRESS', NULL, now() - interval '19 days' + interval '4 hours'),
    (gen_random_uuid(), 10, 'd0000000-0000-0000-0000-000000000102', 'IN_PROGRESS', 'RESOLVED', 'The plumber cleared the drain and fitted a new grating.', now() - interval '18 days');

-- ---- Work orders: who is responsible. The fan's deadline has passed, so it shows as overdue ----
INSERT INTO work_orders (work_order_id, complaint_id, assignee_id, priority, status, deadline, completion_notes, created_at, updated_at) VALUES
    (gen_random_uuid(), 1, 'd0000000-0000-0000-0000-000000000101', 'HIGH',   'IN_PROGRESS', now() - interval '1 day', NULL, now() - interval '3 days' + interval '2 hours', now() - interval '2 days'),
    (gen_random_uuid(), 2, 'd0000000-0000-0000-0000-000000000102', 'MEDIUM', 'OPEN', now() + interval '1 day', NULL, now() - interval '1 day', now() - interval '1 day'),
    (gen_random_uuid(), 5, 'd0000000-0000-0000-0000-000000000103', 'LOW',    'DONE', now() - interval '8 days', 'The carpenter replaced both hinges.', now() - interval '11 days', now() - interval '9 days'),
    (gen_random_uuid(), 7, 'd0000000-0000-0000-0000-000000000101', 'MEDIUM', 'IN_PROGRESS', now() + interval '2 days', NULL, now() - interval '2 days' + interval '3 hours', now() - interval '1 day'),
    (gen_random_uuid(), 9, 'd0000000-0000-0000-0000-000000000101', 'HIGH',   'OPEN', now() + interval '36 hours', NULL, now() - interval '12 hours', now() - interval '12 hours'),
    (gen_random_uuid(), 10, 'd0000000-0000-0000-0000-000000000102', 'MEDIUM', 'DONE', now() - interval '17 days', 'The plumber cleared the drain and fitted a new grating.', now() - interval '19 days', now() - interval '18 days');

-- ---- Upvotes on the open community complaints ----
INSERT INTO upvotes (complaint_id, user_id, created_at) VALUES
    (3, 'd0000000-0000-0000-0000-000000000002', now() - interval '4 hours'),
    (3, 'd0000000-0000-0000-0000-000000000003', now() - interval '3 hours'),
    (4, 'd0000000-0000-0000-0000-000000000003', now() - interval '9 days'),
    (4, 'd0000000-0000-0000-0000-000000000005', now() - interval '8 days'),
    (4, 'd0000000-0000-0000-0000-000000000001', now() - interval '7 days'),
    (8, 'd0000000-0000-0000-0000-000000000004', now() - interval '20 hours');
