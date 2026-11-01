-- HostelFix: trigram similarity search for duplicate complaint detection (PostgreSQL).
--
-- Run against the shared database (Supabase SQL editor or psql -f).
-- Spring does not run this file automatically. Safe to re-run.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- GIN trigram indexes back the similarity operators (%, <%) used to find
-- complaints whose title or description resembles a new complaint's title.
-- On Supabase, if "operator class gin_trgm_ops does not exist" is reported,
-- pg_trgm lives in the extensions schema: use extensions.gin_trgm_ops.
CREATE INDEX IF NOT EXISTS idx_complaints_title_trgm
    ON complaints USING gin (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_complaints_description_trgm
    ON complaints USING gin (description gin_trgm_ops);
