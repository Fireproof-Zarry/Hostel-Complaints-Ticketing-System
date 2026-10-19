-- HostelFix: work orders, comments and upvotes (PostgreSQL).
--
-- Run once AFTER schema.sql, e.g. `psql "$DATABASE_URL" -f schema_oct18.sql`
-- or the Supabase SQL editor. Spring does not run this file automatically.
-- The script is idempotent and can safely be re-run.
--
-- Note: complaints.id is BIGINT and users.id is VARCHAR(255) (existing Hibernate
-- mappings), so the foreign keys below use those types.

BEGIN;

-- ---------------------------------------------------------------------------
-- Work orders: one assignment of a complaint to a staff member
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS work_orders (
    work_order_id    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id     BIGINT       NOT NULL,
    assignee_id      VARCHAR(255) NOT NULL,
    priority         VARCHAR(255) NOT NULL,
    status           VARCHAR(255) NOT NULL,
    deadline         TIMESTAMP,
    completion_notes TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_work_orders_complaint
        FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_work_orders_assignee
        FOREIGN KEY (assignee_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_work_orders_complaint_id ON work_orders (complaint_id);
CREATE INDEX IF NOT EXISTS idx_work_orders_assignee_id ON work_orders (assignee_id);

-- ---------------------------------------------------------------------------
-- Comments on a complaint
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS comments (
    comment_id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id BIGINT       NOT NULL,
    author_id    VARCHAR(255) NOT NULL,
    body         TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comments_complaint
        FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_author
        FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_comments_complaint_id ON comments (complaint_id, created_at);

-- ---------------------------------------------------------------------------
-- Upvotes: at most one per (complaint, user)
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS upvotes (
    complaint_id BIGINT       NOT NULL,
    user_id      VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_upvotes PRIMARY KEY (complaint_id, user_id),
    CONSTRAINT fk_upvotes_complaint
        FOREIGN KEY (complaint_id) REFERENCES complaints (id) ON DELETE CASCADE,
    CONSTRAINT fk_upvotes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

-- The primary key already indexes complaint_id first; this covers "my upvotes".
CREATE INDEX IF NOT EXISTS idx_upvotes_user_id ON upvotes (user_id);

COMMIT;
