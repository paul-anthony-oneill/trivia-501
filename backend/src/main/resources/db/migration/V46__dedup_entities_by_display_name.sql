-- Football 501 — V46: Deduplicate entities by canonical display_name
--
-- V45 normalised existing normalized_name values, but the Python scraper strips
-- ALL non-alphanumeric characters (including spaces) while Java stripAccents()
-- preserves them.  So two rows for "Aaron Anselmino" could have:
--   normalized_name = "aaron anselmino"   (Java — spaces preserved)
--   normalized_name = "aaronanselmino"    (Python — spaces stripped)
--
-- Normalizing both still yields different strings — you can't recover lost spaces.
-- The fix: compute the canonical form from display_name (which always has spaces),
-- deduplicate on that, then set normalized_name = normalize_entity_name(display_name).

-- ── Step 1: Delete duplicates, keeping earliest per canonical display name ──

DELETE FROM entities
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               row_number() OVER (
                   PARTITION BY entity_type, normalize_entity_name(display_name)
                   ORDER BY created_at
               ) AS rn
        FROM entities
    ) ranked
    WHERE ranked.rn > 1
);

-- ── Step 2: Set normalized_name from display_name (the canonical source) ─────

UPDATE entities
SET normalized_name = normalize_entity_name(display_name)
WHERE normalized_name != normalize_entity_name(display_name);
