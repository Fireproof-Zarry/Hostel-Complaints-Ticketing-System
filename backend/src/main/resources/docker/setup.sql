-- The all-in-one Docker image: what the migrations add on top of the tables Hibernate
-- creates. Runs on every start (DockerDatabaseSetup); safe to re-run.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

INSERT INTO categories (category_id, name, default_sla_hours)
SELECT gen_random_uuid(), v.name, v.hours
FROM (VALUES ('Electrical', 48), ('Plumbing', 48), ('Carpentry', 72), ('Cleaning', 24), ('IT', 72))
     AS v(name, hours)
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.name = v.name);

CREATE INDEX IF NOT EXISTS idx_complaints_title_trgm ON complaints USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_complaints_description_trgm ON complaints USING gin (description gin_trgm_ops);
CREATE UNIQUE INDEX IF NOT EXISTS uk_work_orders_one_active_per_complaint
    ON work_orders (complaint_id) WHERE status IN ('OPEN', 'IN_PROGRESS');
CREATE INDEX IF NOT EXISTS idx_work_orders_assignee_status ON work_orders (assignee_id, status);
