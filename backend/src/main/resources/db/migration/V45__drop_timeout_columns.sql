-- ponytail: drop dead timeout columns — no multiplayer, no turn timer
ALTER TABLE games DROP COLUMN IF EXISTS player1_consecutive_timeouts;
ALTER TABLE games DROP COLUMN IF EXISTS turn_timer_seconds;
ALTER TABLE game_moves DROP COLUMN IF EXISTS is_timeout;
