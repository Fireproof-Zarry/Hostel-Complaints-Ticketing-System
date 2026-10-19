-- HostelFix normalization migration (PostgreSQL 15+).
--
-- Run once against the existing database BEFORE starting the app with the new
-- entities, e.g. `psql "$DATABASE_URL" -f schema.sql` or the Supabase SQL editor.
-- Spring does not run this file automatically (spring.sql.init.mode=never).
-- The script is idempotent and can safely be re-run.
--
-- Note: complaints.id is BIGINT and users.id is VARCHAR(255) (existing Hibernate
-- mappings), so the complaint_history foreign keys use those types.

BEGIN;

-- ---------------------------------------------------------------------------
-- Reference tables
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS categories (
    category_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    default_sla_hours INTEGER      NOT NULL,
    CONSTRAINT uk_categories_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS locations (
    location_id       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    floor             VARCHAR(255) NOT NULL,
    room_number       VARCHAR(255),
    wing              VARCHAR(255),
    is_community_area BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_locations_floor_room_wing UNIQUE NULLS NOT DISTINCT (floor, room_number, wing)
);

-- Categories offered by the student complaint form.
INSERT INTO categories (name, default_sla_hours) VALUES
    ('Electrical', 48),
    ('Plumbing',   48),
    ('Carpentry',  72),
    ('Cleaning',   24),
    ('IT',         72)
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------------
-- complaints: replace raw category/floor/room strings with foreign keys
-- ---------------------------------------------------------------------------

ALTER TABLE complaints ADD COLUMN IF NOT EXISTS category_id UUID;
ALTER TABLE complaints ADD COLUMN IF NOT EXISTS location_id UUID;

-- Backfill from the legacy string columns, only while they still exist.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'complaints'
          AND column_name = 'category'
    ) THEN
        -- Any category not in the seed list gets its own row (case-insensitive).
        INSERT INTO categories (name, default_sla_hours)
        SELECT DISTINCT ON (LOWER(TRIM(c.category))) TRIM(c.category), 72
        FROM complaints c
        WHERE NOT EXISTS (
            SELECT 1 FROM categories cat
            WHERE LOWER(cat.name) = LOWER(TRIM(c.category))
        )
        ORDER BY LOWER(TRIM(c.category)), TRIM(c.category)
        ON CONFLICT (name) DO NOTHING;

        UPDATE complaints c
        SET category_id = cat.category_id
        FROM categories cat
        WHERE c.category_id IS NULL
          AND LOWER(cat.name) = LOWER(TRIM(c.category));
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'complaints'
          AND column_name = 'floor'
    ) THEN
        INSERT INTO locations (floor, room_number, wing, is_community_area)
        SELECT DISTINCT TRIM(c.floor), NULLIF(TRIM(c.room), ''), NULL::VARCHAR, FALSE
        FROM complaints c
        ON CONFLICT ON CONSTRAINT uk_locations_floor_room_wing DO NOTHING;

        UPDATE complaints c
        SET location_id = l.location_id
        FROM locations l
        WHERE c.location_id IS NULL
          AND l.floor = TRIM(c.floor)
          AND l.room_number IS NOT DISTINCT FROM NULLIF(TRIM(c.room), '')
          AND l.wing IS NULL;
    END IF;
END $$;

ALTER TABLE complaints ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE complaints ALTER COLUMN location_id SET NOT NULL;

ALTER TABLE complaints DROP CONSTRAINT IF EXISTS fk_complaints_category;
ALTER TABLE complaints
    ADD CONSTRAINT fk_complaints_category
    FOREIGN KEY (category_id) REFERENCES categories (category_id);

ALTER TABLE complaints DROP CONSTRAINT IF EXISTS fk_complaints_location;
ALTER TABLE complaints
    ADD CONSTRAINT fk_complaints_location
    FOREIGN KEY (location_id) REFERENCES locations (location_id);

ALTER TABLE complaints DROP COLUMN IF EXISTS category;
ALTER TABLE complaints DROP COLUMN IF EXISTS floor;
ALTER TABLE complaints DROP COLUMN IF EXISTS room;

CREATE INDEX IF NOT EXISTS idx_complaints_category_id ON complaints (category_id);
CREATE INDEX IF NOT EXISTS idx_complaints_location_id ON complaints (location_id);

-- ---------------------------------------------------------------------------
-- Audit trail
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS complaint_history (
    history_id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id BIGINT       NOT NULL,
    actor_id     VARCHAR(255) NOT NULL,
    old_status   VARCHAR(255) NOT NULL,
    new_status   VARCHAR(255) NOT NULL,
    reason       TEXT,
    changed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_complaint_history_complaint
        FOREIGN KEY (complaint_id) REFERENCES complaints (id),
    CONSTRAINT fk_complaint_history_actor
        FOREIGN KEY (actor_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_complaint_history_complaint_id
    ON complaint_history (complaint_id, changed_at);

COMMIT;
