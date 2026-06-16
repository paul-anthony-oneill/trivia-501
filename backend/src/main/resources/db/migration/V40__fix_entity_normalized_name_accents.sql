-- V40: Fix entity rows where normalized_name was stored with accents intact.
--
-- Root cause: migration SQL (V27, V29, V38) copied p.normalized_name directly
-- from the players table. Some player rows were scraped with normalized_name
-- lowercased but not accent-stripped (e.g. "odsonne édouard" instead of
-- "odsonne edouard"). This produced duplicate entity rows with subtly different
-- normalized_names, bypassing the UNIQUE (entity_type, normalized_name) constraint.
--
-- Fix in two steps:
--   1. Re-normalize the players source table so future bulk upserts are clean.
--   2. Deduplicate entities:
--      a. Where a correctly-normalized counterpart already exists, delete the bad row.
--      b. Where no counterpart exists, update the bad row in place.

-- Step 1: Fix players table
UPDATE players
SET    normalized_name = unaccent(lower(name))
WHERE  normalized_name IS DISTINCT FROM unaccent(lower(name));

-- Step 2a: Delete bad entity rows that already have a correctly-normalized twin.
--          Prefer keeping the row with a non-null hint (e.g. nationality = FRA).
DELETE FROM entities bad
WHERE  bad.normalized_name != unaccent(bad.normalized_name)
  AND  EXISTS (
           SELECT 1 FROM entities good
           WHERE  good.entity_type     = bad.entity_type
             AND  good.normalized_name = unaccent(bad.normalized_name)
             AND  good.id             != bad.id
       );

-- Step 2b: Fix remaining bad entity rows (no correctly-normalized twin exists yet).
UPDATE entities
SET    normalized_name = unaccent(normalized_name)
WHERE  normalized_name != unaccent(normalized_name);
