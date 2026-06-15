# go-signer

A small Go microservice that cryptographically signs and verifies Football 501 game results using **ed25519** from the Go standard library.

## Why this exists

When a player checks out in Football 501, their result is shared as an emoji grid (Wordle-style). Without signing, anyone can fabricate a share claiming a perfect game. This service issues a signed token when a game completes; the share link embeds the token, and anyone can verify the result is genuine.

The Java backend validates all game logic during play — this service only runs once, after checkout, to produce an unforgeable record.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/sign` | Sign a completed game result → returns a token |
| `POST` | `/verify` | Verify a token → returns `valid` + decoded payload |
| `GET`  | `/pubkey` | Return the public key (for independent verification) |
| `GET`  | `/health` | Liveness check |

### POST /sign

```json
// Request
{
  "gameId":      "550e8400-e29b-41d4-a716-446655440000",
  "playerId":    "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "finalScore":  -3,
  "completedAt": "2026-06-14T12:00:00Z"
}

// Response 200
{
  "token": {
    "payload": "<base64url-encoded JSON payload>",
    "sig":     "<base64url-encoded ed25519 signature>"
  }
}
```

### POST /verify

```json
// Request
{
  "token": {
    "payload": "...",
    "sig":     "..."
  }
}

// Response 200 — valid
{ "valid": true, "payload": { "gameId": "...", "playerId": "...", "finalScore": -3, "completedAt": "...", "kid": "k1749906000" } }

// Response 200 — invalid (not 4xx — callers should branch on "valid")
{ "valid": false, "error": "signature verification failed" }
```

## Running locally

```bash
# Generate a keypair
go run keygen/keygen.go

# Copy the SIGNING_KEY and SIGNING_KEY_ID values, then:
export SIGNING_KEY=<value>
export SIGNING_KEY_ID=<value>

go run main.go
# Listening on :8090
```

## Tests

```bash
go test ./...
```

All tests are table-driven and use only the standard library. No external dependencies.

## Key generation and rotation

```bash
go run keygen/keygen.go
```

Prints `SIGNING_KEY`, `SIGNING_KEY_ID`, and the public key. Set as Fly.io secrets:

```bash
fly secrets set SIGNING_KEY=<value> SIGNING_KEY_ID=<value> --app go-signer-football-501
```

Give the Java backend the public key via `RESULT_SIGNER_PUBLIC_KEY` so it can verify tokens independently without calling this service on every share view.

**Rotation**: generate a new pair, deploy with the new key. Old tokens remain valid as long as the old public key is retained — they embed a `kid` (key ID) field. Drop the old public key only after all in-flight tokens have expired or been superseded.

## Deployment

```bash
fly deploy --app go-signer-football-501
```

Required secrets:

| Secret | Description |
|--------|-------------|
| `SIGNING_KEY` | base64-encoded 64-byte ed25519 private key |
| `SIGNING_KEY_ID` | Short label for this key (e.g. `k1749906000`) |

## Java integration

The Spring Boot backend calls `/sign` once after a successful checkout in `GameService`. See `ResultSignerClient.java` for the HTTP client wrapper. The token is stored in `games.result_token` and included in the `GET /share/{gameId}` response.

A signing failure (service unavailable, timeout) is non-fatal: the checkout is recorded, `result_token` is left null, and an error is logged. Games without a token are displayed without the verified badge.
