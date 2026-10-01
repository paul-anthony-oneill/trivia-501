-- Result tokens signed before V47 embed the raw player id, which for guests is
-- their session credential. They were exposed via the public share endpoint.
-- New tokens carry a SHA-256 pseudonym (ResultSignerClient.playerPseudonym).
-- No feature reads these tokens yet, so clearing them loses nothing user-visible.
UPDATE games SET result_token = NULL WHERE result_token IS NOT NULL;
