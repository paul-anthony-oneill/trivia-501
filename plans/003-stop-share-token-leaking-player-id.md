# Plan 003: Share links no longer expose the player's anonymous session ID

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 0baf851..HEAD -- backend/src/main/java/com/trivia501/service/ResultSignerClient.java backend/src/main/java/com/trivia501/controller/GameEndpointHandler.java backend/src/main/java/com/trivia501/dto/DailyChallengeShareResponse.java backend/src/main/resources/db/migration`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1 (security)
- **Effort**: S
- **Risk**: LOW (the frontend doesn't read `resultToken` at all; verified by grep)
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `0baf851`, 2026-09-28

## Why this matters

Anonymous (guest) players are identified **only** by the `X-Anonymous-Id` cookie, a
random UUID. Whoever sends that value *is* that player, and the cookie lives 24h on a
sliding window. When a player checks out, the backend asks the go-signer service to sign
`{gameId, playerId, finalScore, completedAt}`. The token's payload is plain base64url JSON:
**signed, not encrypted**. That token is stored on the game and returned by the **public**
share endpoint `GET /api/daily-challenge/share/{gameId}`, the URL players paste to friends.
So anyone with a share link can decode the payload, read the guest's session UUID, set it as
their own cookie, and act as that player (read or abandon their other games, submit answers
to their other dailies). This plan fixes it in three layers: sign a one-way pseudonym instead
of the raw ID, stop returning the token from the public endpoint, and wipe the already-issued
tokens that contain raw IDs.

## Current state

- `backend/src/main/java/com/trivia501/service/ResultSignerClient.java`: HTTP client for
  go-signer. `sign(...)` at line 58 sends the raw player UUID:
  ```java
  public Optional<String> sign(UUID gameId, UUID playerId, int finalScore, LocalDateTime completedAt) {
      ...
          SignRequest request = new SignRequest(
                  gameId.toString(),
                  playerId.toString(),      // ← raw anon cookie value for guests
                  finalScore,
                  completedAtIso
          );
  ```
  Its only caller is `GameService.java:137`, `resultSignerClient.sign(gameId, playerId, transition.scoreAfter(), game.getCompletedAt())`.
- `go-signer/signer/signer.go:17-23`: `Payload{GameID, PlayerID, FinalScore, CompletedAt, KeyID}`.
  `Sign` rejects an empty `PlayerID`, but any non-empty string is accepted. **No Go change is needed.**
- `backend/src/main/java/com/trivia501/controller/GameEndpointHandler.java:144-199`:
  `getShareData(UUID gameId)` does no auth/owner check (by design; share links are public)
  and ends with:
  ```java
                .moveEmojis(emojis)
                .resultToken(game.getResultToken())
                .build());
  ```
- `backend/src/main/java/com/trivia501/dto/DailyChallengeShareResponse.java:27-29`:
  ```java
      // Null when go-signer was unavailable at checkout. Presence means the result is
      // cryptographically verifiable against the public key at GET /pubkey on go-signer.
      private String resultToken;
  ```
- `grep -rn resultToken frontend-react/src` → **no matches**. The frontend never reads the field.
- `Game.resultToken` is column `games.result_token` (TEXT), added in `V39__add_result_token_to_games.sql`.
- Latest Flyway migration at 0baf851: `V46__dedup_entities_by_display_name.sql` (SQL migrations
  are in `backend/src/main/resources/db/migration/`, Java ones in `backend/src/main/java/db/migration/`,
  sharing one version sequence). The next free version is **V47**. CI fails on duplicate versions (`backend-ci.yml` `flyway-check`).
- Test convention: plain JUnit 5, no Spring context, `@DisplayName` on class and methods.
  See `backend/src/test/java/com/trivia501/engine/DartsValidatorTest.java`.
- Project rule (CLAUDE.md): `GlobalExceptionHandler` owns error formatting, so don't add local exception handlers.

## Commands you will need

| Purpose | Command (from `backend/`) | Expected on success |
|---|---|---|
| Unit tests (no Docker) | `mvn -B test -Dtest='ResultSignerClientTest,GameServiceTest'` | `BUILD SUCCESS` |
| Full suite | `mvn -B test` | `BUILD SUCCESS`; Testcontainers tests are skipped if Docker is down (14 skipped at baseline) |
| Compile | `mvn -B -q compile` | exit 0 |

## Scope

**In scope**:
- `backend/src/main/java/com/trivia501/service/ResultSignerClient.java`
- `backend/src/main/java/com/trivia501/controller/GameEndpointHandler.java`
- `backend/src/main/java/com/trivia501/dto/DailyChallengeShareResponse.java`
- `backend/src/main/resources/db/migration/V47__clear_result_tokens_with_raw_player_ids.sql` (create)
- `backend/src/test/java/com/trivia501/service/ResultSignerClientTest.java` (create)

**Out of scope**:
- `go-signer/`: the payload field stays named `playerId`; it just carries a pseudonym now.
- `OptionalJwtFilter` cookie rotation. It's known to be broken (the header is added after the response is committed), but that's a separate issue.
- Building a public "verify result" page. Deferred product work.
- `GameService.java`: the call site doesn't change.

## Git workflow

- Branch: `advisor/003-share-token-pseudonym`
- Commit per step; example: `fix(security): sign a pseudonymous player id in result tokens`
- Do NOT push unless instructed.

## Steps

### Step 1: Sign a one-way pseudonym instead of the raw player ID

In `ResultSignerClient.java`, add a package-private static helper and use it in `sign(...)`:

```java
/**
 * One-way pseudonym for the signed payload. The raw player id is the guest's
 * session credential (X-Anonymous-Id cookie), and token payloads are readable by
 * anyone holding a share link, so the raw id must never be signed.
 * A SHA-256 of a random v4 UUID can't be reversed, but it still lets the owner
 * prove "this token is mine" by recomputing it.
 */
static String playerPseudonym(UUID playerId) {
    try {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(playerId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException e) {
        throw new IllegalStateException("SHA-256 unavailable", e); // every JVM ships SHA-256
    }
}
```

Replace `playerId.toString(),` in the `new SignRequest(...)` call with `playerPseudonym(playerId),`.
Prefer imports at the top of the file over fully-qualified names, to match the file's style.

**Verify**: `mvn -B -q compile` → exit 0, and `grep -n "playerPseudonym(playerId)" src/main/java/com/trivia501/service/ResultSignerClient.java` → 1 match (note: `playerId.toString()` still appears once, legitimately, inside the helper body).

### Step 2: Unit-test the pseudonym

Create `backend/src/test/java/com/trivia501/service/ResultSignerClientTest.java` (same package
so it can call the package-private helper), modelled on `DartsValidatorTest`:

- `pseudonymIsDeterministic`: the same UUID gives an equal string both times.
- `pseudonymDoesNotContainRawId`: for `UUID.fromString("00000000-0000-0000-0000-000000000001")`,
  the result doesn't contain `"00000000-0000-0000-0000-000000000001"` and matches `^[0-9a-f]{64}$`.
- `differentIdsGiveDifferentPseudonyms`: two random UUIDs give different results.

**Verify**: `mvn -B test -Dtest=ResultSignerClientTest` → `Tests run: 3, Failures: 0, Errors: 0`.

### Step 3: Stop returning the token from the public share endpoint

In `GameEndpointHandler.getShareData`, delete the line `.resultToken(game.getResultToken())`.
In `DailyChallengeShareResponse`, delete the `resultToken` field and its two comment lines.
The token stays in the `games.result_token` column for future server-side verification.

**Verify**: `grep -rn "resultToken" src/main/java/com/trivia501/controller src/main/java/com/trivia501/dto` → no matches; `mvn -B -q compile` → exit 0.

### Step 4: Wipe tokens already issued with raw IDs

Create `backend/src/main/resources/db/migration/V47__clear_result_tokens_with_raw_player_ids.sql`:

```sql
-- Result tokens signed before V47 embed the raw player id, which for guests is
-- their session credential. They were exposed via the public share endpoint.
-- New tokens carry a SHA-256 pseudonym (ResultSignerClient.playerPseudonym).
-- No feature reads these tokens yet, so clearing them loses nothing user-visible.
UPDATE games SET result_token = NULL WHERE result_token IS NOT NULL;
```

**Verify**: `ls src/main/resources/db/migration src/main/java/db/migration | grep -oE '^V[0-9]+' | sort | uniq -d` → empty (no duplicate versions).

### Step 5: Full backend suite

`mvn -B test`

**Verify**: `BUILD SUCCESS`, 0 failures, 0 errors. The skipped count should be unchanged from baseline (14 when Docker is down).

## Test plan

- New: `ResultSignerClientTest` (3 tests, Step 2).
- Existing `GameServiceTest` mocks `ResultSignerClient`, so it's unaffected. Keep it green.
- If Docker is available, the Testcontainers suite runs V47 against a real Postgres as part of `mvn test`. That covers the migration syntax.

## Done criteria

- [ ] `grep -n "playerPseudonym(playerId)" backend/src/main/java/com/trivia501/service/ResultSignerClient.java` → 1 match
- [ ] `grep -rn "resultToken" backend/src/main/java/com/trivia501/dto backend/src/main/java/com/trivia501/controller` → no matches
- [ ] `V47__clear_result_tokens_with_raw_player_ids.sql` exists; no duplicate migration versions
- [ ] `mvn -B test` → BUILD SUCCESS, including 3 new tests
- [ ] `git status` shows only in-scope files
- [ ] `plans/README.md` status row updated

## STOP conditions

- A `V47__*` migration already exists. Use the next free number and say so. If numbering looks inconsistent, STOP.
- `grep -rn resultToken frontend-react/src` now returns matches. The frontend has started using the field, so removing it would break something. Report instead.
- `ResultSignerClient.sign` has gained callers other than `GameService` that rely on the raw ID.
- Any `GameServiceTest` test asserts on the exact `playerId` string passed to the signer and fails. Report which one; don't weaken the assertion without saying so.

## Maintenance notes

- **Deploy note for the operator**: V47 runs on the next Fly deploy (Flyway on boot). It's a single UPDATE on `games`, so it's fast.
- If a public "verify this result" page is built later, return the token from a dedicated endpoint, and have the owner prove ownership by recomputing `playerPseudonym` server-side, never by exposing the raw ID.
- Related but separate: guest cookie rotation in `OptionalJwtFilter` (lines ~153-172) adds `Set-Cookie` after `filterChain.doFilter`, when the response may already be committed. Worth its own investigation.
