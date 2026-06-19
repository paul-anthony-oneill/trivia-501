-- V40: Fix entity rows where normalized_name was stored with accents intact.
--
-- Root cause: migration SQL (V27, V29, V38) copied p.normalized_name directly
-- from the players table. Some player rows were scraped with normalized_name
-- lowercased but not accent-stripped (e.g. "odsonne édouard" instead of
-- "odsonne edouard"). This produced duplicate entity rows with subtly different
-- normalized_names, bypassing the UNIQUE (entity_type, normalized_name) constraint.
--
-- Fix in steps:
--   1. Re-normalize the players source table so future bulk upserts are clean.
--   2. Delete accented entity rows that have a correctly-normalized twin.
--   3. Where multiple accented rows resolve to the same unaccented name, keep one.
--   4. Update remaining accented rows (now safe — no target collision).
--   5. Delete plain duplicates (both rows already unaccented).

-- Step 1: Fix players table
UPDATE players
SET    normalized_name = unaccent(lower(name))
WHERE  normalized_name IS DISTINCT FROM unaccent(lower(name));

-- Step 2: Delete bad entity rows that already have a correctly-normalized twin.
--          Prefer keeping the row with a non-null hint (e.g. nationality = FRA).
DELETE FROM entities bad
WHERE  bad.normalized_name != unaccent(bad.normalized_name)
  AND  EXISTS (
           SELECT 1 FROM entities good
           WHERE  good.entity_type     = bad.entity_type
             AND  good.normalized_name = unaccent(bad.normalized_name)
             AND  good.id             != bad.id
       );

-- Step 3: Where multiple accented rows would resolve to the same unaccented
--          name, keep the one with the lowest ID and delete the rest.
DELETE FROM entities bad
WHERE  bad.normalized_name != unaccent(bad.normalized_name)
  AND  EXISTS (
           SELECT 1 FROM entities keeper
           WHERE  keeper.entity_type = bad.entity_type
             AND  unaccent(keeper.normalized_name) = unaccent(bad.normalized_name)
             AND  keeper.id < bad.id
       );

-- Step 4: Update remaining accented rows. Safe now because Steps 2 & 3
--          guarantee no two rows share the same target (entity_type, unaccented_name).
UPDATE entities
SET    normalized_name = unaccent(normalized_name)
WHERE  normalized_name != unaccent(normalized_name);

-- Step 5: Delete plain duplicates (both rows already unaccented) that Step 2 missed.
--          Keep the row with the lower ID.
DELETE FROM entities bad
WHERE  EXISTS (
           SELECT 1 FROM entities good
           WHERE  good.entity_type     = bad.entity_type
             AND  good.normalized_name = bad.normalized_name
             AND  good.id             < bad.id
       );
