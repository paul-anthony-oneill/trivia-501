-- Drop multiplayer-only columns. Multiplayer is deferred indefinitely;
-- the schema and engine will be rebuilt from a real product spec when it returns.
ALTER TABLE games   DROP COLUMN IF EXISTS player2_score;
ALTER TABLE games   DROP COLUMN IF EXISTS player2_consecutive_timeouts;
ALTER TABLE matches DROP COLUMN IF EXISTS player2_id;
ALTER TABLE matches DROP COLUMN IF EXISTS player2_games_won;
DROP INDEX IF EXISTS idx_matches_player2;
