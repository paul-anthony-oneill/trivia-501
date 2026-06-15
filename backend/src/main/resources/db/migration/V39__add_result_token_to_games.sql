-- Add result_token to store the ed25519-signed token issued by go-signer on checkout.
-- Nullable: games that completed before the signing service existed, or where the
-- signing service was unavailable, will have NULL. A missing token means the result
-- cannot be cryptographically verified, not that the game was invalid.
ALTER TABLE games
    ADD COLUMN IF NOT EXISTS result_token TEXT;
