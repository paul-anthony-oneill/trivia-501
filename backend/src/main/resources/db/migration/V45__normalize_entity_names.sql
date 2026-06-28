-- Football 501 — V45: Handle NFD-opaque characters that V40 missed
--
-- V40 re-normalised the entities table with unaccent(), which strips standard
-- combining diacritics (ü→u, é→e, ñ→n) but leaves NFD-opaque characters
-- (ø, æ, ł, đ, œ, ð, þ, ß) unchanged because the standard PostgreSQL unaccent
-- dictionary does not map them.
--
-- Three different normalization functions inserted into the entities table:
--  1. Python scraper: lowercase + strip non-alnum  → "sergioaguero"
--  2. Java stripAccents(): NFD + opaque replacements → "sergio aguero"
--  3. PostgreSQL unaccent() in backfill             → "martin ødegaard" (ø unchanged)
--
-- V40 aligned paths 1 & 3 for standard accents, but NFD-opaque chars still
-- produce different normalized_name values for the same display_name, so the
-- UNIQUE (entity_type, normalized_name) constraint doesn't fire.  Result: the
-- same name appears 2-3 times in the autocomplete dropdown.
--
-- This migration:
--  1. Creates normalize_entity_name() — mirrors Java EntitySearchService.stripAccents()
--     by wrapping unaccent() with explicit replace() calls for NFD-opaque chars
--  2. Deduplicates: for each (entity_type, canonical_name) group, keeps the earliest row
--  3. Updates all remaining rows to the canonical normalized form

-- ── Step 1: PostgreSQL function that mirrors Java's stripAccents() ──────────
-- unaccent() handles standard accented chars (ü→u, é→e, ñ→n).
-- The explicit replace() calls handle NFD-opaque characters (ø, æ, ł, đ, etc.)
-- that unaccent() with the standard dictionary leaves unchanged.

CREATE OR REPLACE FUNCTION normalize_entity_name(input text) RETURNS text AS $$
BEGIN
    -- unaccent strips standard combining diacritics: ü→u, é→e, ñ→n, ç→c, etc.
    -- NFD-opaque characters (ø, æ, ł, đ, œ, ð, þ, ß) pass through unaccent unchanged.
    RETURN replace(replace(replace(replace(replace(replace(replace(replace(replace(
               unaccent(lower(input)),
           'ø', 'o'),  'æ', 'ae'), 'ł', 'l'),  'đ', 'd'),
           'œ', 'oe'), 'ð', 'd'),  'þ', 'th'), 'ß', 'ss'), '’', '''');
END;
$$ LANGUAGE plpgsql IMMUTABLE;

COMMENT ON FUNCTION normalize_entity_name(text) IS
'Mirrors Java EntitySearchService.stripAccents() — NFD decomposition + opaque-character replacements. Keep in sync with NFD_OPAQUE_REPLACEMENTS in Java and the equivalent replacements in the Python scraper.';

-- ── Step 2: Deduplicate — keep the earliest-created row per canonical form ──

DELETE FROM entities
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               row_number() OVER (
                   PARTITION BY entity_type, normalize_entity_name(normalized_name)
                   ORDER BY created_at
               ) AS rn
        FROM entities
    ) ranked
    WHERE ranked.rn > 1
);

-- ── Step 3: Normalize all remaining rows to the canonical form ──────────────

UPDATE entities
SET normalized_name = normalize_entity_name(normalized_name)
WHERE normalized_name != normalize_entity_name(normalized_name);
