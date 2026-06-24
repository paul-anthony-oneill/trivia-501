# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Backlog Discipline

Before starting any task, check **`docs/BACKLOG.md`**. After completing or deferring work:
- **Tick off** any backlog item your change addresses (move it to the Completed table).
- **Add a new entry** if you are consciously deferring something that came up during the task — capture what it is, why it's deferred, and which files are relevant.
- **Do not add** Flyway migrations, new abstractions, or hardening work that isn't needed right now just because it feels tidy. Defer it and document it instead.

## Project Overview

Trivia 501 is a daily social trivia game that combines football knowledge with darts 501 scoring mechanics. Each day, players are given a question and a target score — the goal is to reach exactly 0 by naming players whose statistics match the question. Share your emoji-grid result with friends. Free Play mode lets players pick any category and question to play on their own terms.

**Current Status**: Single-player focus — Daily Challenge + Free Play. Multiplayer and competitive ranking are deferred indefinitely (2026-06-08).

**Product direction**: Wordle-style daily social trivia with emoji-grid sharing. Real-time multiplayer, MMR, league tiers, and matchmaking are parked. Player profiles will be a personal dashboard (stats, history, streaks) rather than competitive ranking.

**Tech Stack**:
- Frontend: Next.js 16 (App Router) + React 19 + TypeScript + Tailwind CSS 4 — `frontend-react/`
- Backend: Spring Boot 4.0.6 + Java 25 + PostgreSQL 15+
- Data Source: ScraperFC (Python Microservice)
- Real-time: REST API (WebSocket deferred indefinitely with multiplayer)

## Architecture

### High-Level System Design

The application follows a client-server architecture with a separate Python microservice for data scraping:

```
PWA Client (Next.js/React) <--HTTPS--> Spring Boot Server
                                            |
                                       PostgreSQL
                                            ^
                                            |
                                Python Scraper Service (ScraperFC)
```

**Critical Architectural Principles**:
1. **Zero API Calls During Gameplay**: All match validation uses pre-cached player data from the database.
2. **Server-Side Validation**: All game logic (scoring rules, darts validation, win conditions) is validated server-side to prevent cheating.
3. **Aggressive Caching**: Question/answer data is cached in PostgreSQL.
4. **Scraping Service**: A dedicated Python microservice uses ScraperFC to populate the database via batch jobs, keeping the main backend clean.

### Backend Module Structure

The Spring Boot application is organized into modules:
- **API Module**: REST endpoints for CRUD operations and game play (Daily Challenge, Free Play)
- **WebSocket Module**: Real-time match communication handler (deferred indefinitely with multiplayer)
- **Game Engine Module**: Core game logic (scoring, validation, win conditions) — shared across all modes
- **Auth Module**: User authentication (OAuth 2.0 + anonymous guest play)
- **Scheduler Module**: Automated tasks (daily challenge generation, stats refresh)
- **Integration Module**: External API client for API-Football

### Named Entity Autocomplete

The `entities` table is a global registry of named things that players can type as answers during gameplay. It powers the autocomplete dropdown that appears as the player types.

**Key design constraint**: the `entities` table is intentionally decoupled from the `answers` table. A name appearing in autocomplete tells the player nothing about whether it is a valid answer to the current question. All answer validation happens server-side in `AnswerEvaluator` against the `answers` table only.

**How it works**:
- `entity_type` column (e.g. `"footballer"`, `"city"`, `"country"`) groups names into pools
- Questions declare which pool to use via a `config` JSONB column: `{"entity_type": "footballer", ...}` or `{"entity_type": "city", ...}`
- The frontend reads `question.config.entity_type` and passes it to the `EntitySearch` component, which calls `GET /api/entities/search?type={entityType}&query={query}`
- Search uses PostgreSQL `unaccent()` + a GIN trigram index (`idx_entities_unaccent_trgm`) for accent-insensitive substring matching — typing "aguero" returns "Sergio Agüero"
- The `entities` table is populated automatically: when answers are bulk-imported via `AdminAnswerService`, it calls `EntitySearchService.upsertEntity()` for each player name. The upsert is idempotent and uses `(entity_type, normalized_name)` as the unique key.

**Naming note**: the Java model class is `NamedEntity` (not `Entity`) to avoid a name clash with the JPA `@Entity` annotation.

See `docs/design/AUTOCOMPLETE_ENTITY_DESIGN.md` for the full design document, including how to add a new entity type.

### Frontend Architecture

- **App Router**: Next.js 16 App Router; all pages are `"use client"` (no SSR needed — data comes from Spring Boot)
- **State Management**: Local `useState` + React Context API (`ToastContext`); no Redux/Zustand
- **API Proxy**: `next.config.ts` rewrites `/api/*` → `http://localhost:8080/api/*` in dev
- **Game Loop**: `useGameLoop` hook fetches game state via REST; no WebSocket in current single-player modes
- **Optimistic UI**: Client updates UI immediately (autocomplete, answer submission), then syncs with server response
- **Styling**: Tailwind v4 with `@theme inline` in `globals.css` (no `tailwind.config.ts`)
- **Entity Autocomplete**: `EntitySearch.tsx` (`frontend-react/src/components/game/EntitySearch.tsx`) is the autocomplete input component used during gameplay. It accepts an `entityType` prop — the caller reads `question.config.entity_type` and passes it through; the prop defaults to `"footballer"` for backward compatibility. The component fires a search after 4 characters are typed, shows a "Keep typing…" hint at 1–3 characters, and fills the input on selection without auto-submitting so the player can confirm.

## Core Game Mechanics

### Scoring System (Critical)

1. Player starts with a target score drawn from a pool of 20–30 curated values (101–501 range, currently 9 values: {501, 401, 351, 301, 251, 201, 167, 125, 101})
2. Player names players matching the question; each valid answer's statistic is deducted from their score
3. **Valid Darts Scores**: Only scores achievable with 3 darts in standard 501 are valid
   - Invalid scores (163, 166, 169, 172, 173, 175, 176, 178, 179) result in **bust** (no score deducted)
4. **Bust Rules**:
   - Scores > 180 = bust (turn wasted)
   - Invalid darts scores = bust
   - Scores below -10 = bust (overshot the checkout zone)
5. **Checkout Range**: -10 to 0 (inclusive) to win
6. **Game ends**: checkout, bust-out (no valid moves remain), or forfeit

### Daily Challenge Mode

Daily Challenges are Wordle-style: one question per category per day, same starting score for everyone, one trust-based attempt, shareable emoji-grid results.

**Key design decisions**:
- **One challenge per category per day** — `UNIQUE(challenge_date, category_id)` on `daily_challenges` table
- **Variable starting scores** — randomly selected from an expanded curated pool of 20–30 scores (P0: currently 9 values `{501, 401, 351, 301, 251, 201, 167, 125, 101}`); same question pool at different targets = different strategic experience
- **No leaderboards** — too easy to cheat (look up stats); comparing with friends via share links is the social mechanic
- **No replay enforcement** — trust-based; the `daily_challenge_results` table was intentionally omitted
- **Lazy creation + midnight cron** — `DailyChallengeScheduler` pre-selects at 00:00 UTC; `DailyChallengeService.getTodaysChallenge()` creates lazily if the cron missed

**Schema**:
- `daily_challenges` — one row per category per day (challenge_date, category_id, question_id, starting_score)
- `suitable_for_daily BOOLEAN` on `questions` — admin-curated pool flag (V19); backfilled for viable easy/medium questions (V20)
- `game_mode VARCHAR` on `matches` — set to `'DAILY_CHALLENGE'` for daily games; defaults to `'STANDARD'`

**API** (`/api/daily-challenge`):
- `GET /status` — all categories with today's challenge info
- `GET /{categorySlug}` — single category status (triggers lazy creation)
- `POST /{categorySlug}/start` — start a daily challenge game
- `POST /games/{id}/submit` — submit answer (reuses GameService)
- `GET /games/{id}` — game state
- `GET /share/{gameId}` — share data with move emojis (VALID→🟩, BUST→🟥, INVALID→⬜, CHECKOUT→🎯)

**Frontend**:
- `/daily` — overview of all today's challenges
- `/daily/[category]` — deep link target for share URLs
- LobbyView shows daily challenge cards above the category grid
- MatchView shows "SHARE RESULT" button in win overlay (daily mode only)

### Question Types

All questions must be pre-populated with answers from API-Football:

1. **Team League Appearances**: "Appearances for [Team] in [League]"
2. **Combined Stats**: "Appearances + Goals for [Team] in [League]" (score = sum)
3. **Goalkeeper Stats**: "Appearances + Clean Sheets for [Team] in [League]"
4. **International**: "Appearances for [Country] in [Competition]"
5. **Nationality Filter**: "Appearances in [League] by players from [Country]"

### Turn Rules

- **Daily Challenge**: No turn timer — play at your own pace. Single attempt per category per day.
- **Free Play**: Optional turn timer (45s default, can be disabled). Client display built; server enforcement is P1.
- Consecutive timeouts reduce timer (45s → 30s → 15s → forfeit). Non-consecutive timeouts reset to default.
- Invalid answers allow instant retry (timer keeps running if enabled)
- Fuzzy matching for player names (e.g., "Aguero" matches "Agüero")

## Database Schema

Key tables to understand:

### Core Tables
- `users`: User accounts (anonymous, registered via Google OAuth)
- `player_profiles`: Personal stats dashboard (game history, daily challenge streaks, personal bests) — no competitive ranking
- `questions`: Question definitions with API-Football metadata
- `answers`: Pre-cached player statistics (NEVER queried from API during matches)

### Match Tables
- `matches`: Match metadata (type, format, players, status)
- `games`: Individual games within a match (best-of-3/5)
- `game_moves`: Turn-by-turn history (answer, score, validity)

### Special Tables
- `daily_challenges`: Daily challenge questions
- `game_moves`: Turn-by-turn history — also used for emoji-grid share generation

**Critical Indexes**:
- `answers` table uses `gin_trgm_ops` index for fuzzy player name matching
- Full-text search enabled on player names

## Data Source Integration (ScraperFC)

### Critical Rules

1. **Python Microservice**: All scraping logic resides in `trivia-501-scraper/`.
2. **Batch Population**: Questions are populated in advance via scheduled jobs in the Python service.
3. **Zero Live Calls**: The main Spring Boot backend NEVER calls external APIs. It only reads from the `answers` table.
4. **Weekly Updates**: Automated batch refresh of current season stats.

### Data Flow

```
1. Admin creates question (or auto-generated)
2. Python Service (ScraperFC) fetches player stats from FBref
3. Service transforms data and populates `answers` table:
   - Player names (normalized)
   - Statistics (value)
   - Validity flags (isValidDartsScore, isBust)
4. Spring Boot Backend reads from `answers` table during gameplay
```

### Schema Mapping

ScraperFC data maps to the `answers` table:
- Player Name → `player_name`
- Statistic (e.g., Appearances) → `statistic_value`
- Validation (1-180, no invalid darts) → `is_valid_darts`
- Bust Check (>180) → `is_bust`

## WebSocket Protocol

**Status**: Deferred indefinitely with multiplayer (2026-06-08). The protocol design below is retained for reference if multiplayer returns. Current single-player modes (Daily Challenge, Free Play) use REST only.

### Connection
- Endpoint: `wss://api.trivia501.com/ws?token={JWT_TOKEN}`
- Protocol: STOMP over WebSocket
- Heartbeat: 30-second ping/pong

### Message Format
```json
{
  "type": "MESSAGE_TYPE",
  "gameId": "uuid",
  "payload": { /* message-specific data */ }
}
```

### Critical Messages (designed, not implemented)

**Client → Server**:
- `SUBMIT_ANSWER`: Submit player name for validation
- `REQUEST_REFRESH`: Request new question (only at game start)
- `VOTE_REFRESH`: Vote on opponent's refresh request

**Server → Client**:
- `GAME_STATE`: Full game state update (scores, turn, timer)
- `ANSWER_RESULT`: Validation result (valid/bust/invalid)
- `TURN_TIMEOUT`: Timeout event (timer reduction)
- `GAME_OVER`: Game complete (winner, final scores)

### Reconnection Handling
- 30-second grace period on disconnect
- Server maintains game state during grace period
- Full game state sync on reconnect

## Security

### Current Security Architecture (Dev-Mode Permissive)

Spring Security is fully wired (`SecurityConfig.java`, `DevModeAuthFilter.java`) but operates in a permissive dev-mode posture. See `docs/SECURITY_ARCHITECTURE.md` for the complete model.

**How identity works today**:
- `DevModeAuthFilter` (`@Profile("!prod")`) runs on every non-production profile and injects a fixed authenticated principal (`DEV_PLAYER_ID = "00000000-0000-0000-0000-000000000001"`) with `ROLE_USER` and `ROLE_ADMIN`.
- `PracticeGameController` reads player identity from `Principal.getName()` — never from a request parameter. Identity spoofing via `@RequestParam playerId` has been removed.
- All five admin controllers carry `@PreAuthorize("hasRole('ADMIN')")` at class level; `@EnableMethodSecurity` activates these annotations.

**URL-level access policy** (enforced by `SecurityConfig`):
- `/api/entities/**` and `/api/categories/**` — `permitAll` (public autocomplete and listing)
- `/actuator/health` — `permitAll` (liveness probe)
- `GET /api/daily-challenge/**` — `permitAll` (public browsing of daily challenges; share URLs)
- `POST /api/daily-challenge/**` — `permitAll` (game start, submit, abandon — anonymous play is the core flow)
- `/api/practice/**` — `permitAll` (Free Play; anonymous play is the core flow)
- `/api/admin/**` — `ROLE_ADMIN`
- everything else — `authenticated`

**What is deferred to production**:
- Real OAuth 2.0 / JWT validation filter (replaces `DevModeAuthFilter`). When this ships: set `SPRING_PROFILES_ACTIVE=prod` AND `SUPABASE_JWT_ISSUER` on Fly.io together — never one without the other (see Deployment & Hosting > Fly.io).
- CSRF protection (currently disabled for stateless REST; re-evaluate when JWT cookies are introduced)
- Content Security Policy (add in Next.js `middleware.ts`)

### Anti-Cheat Measures
- All game logic validated server-side
- Answer validation against cached database only (zero external API calls during gameplay)
- Annually rotated anonymous session cookies prevent cross-game tracking
- Daily challenge is trust-based by design — no leaderboard eliminates the incentive to cheat for rank

### Rate Limiting
- Authenticated users: 100 req/min
- Unauthenticated: 10 req/min per IP

## Ranking System

**Status**: Dropped (2026-06-08). The 9-tier MMR/league system was designed for competitive multiplayer and has no role in a single-player daily challenge product. Player profiles will be a personal dashboard (stats, history, streaks) without competitive ranking. The `player_profiles` table (V23 migration) is repurposed as a personal stats store. The `league_tier` and `league_subtier` columns may be dropped or left unused.

## Development Workflow

### Project Setup

**Frontend (React — active)**:
```bash
cd frontend-react
npm install
npm run dev  # Dev server on http://localhost:3000
# API calls proxied to http://localhost:8080 via next.config.ts
```

**Backend**:
```bash
cd backend
# Requires Java 25 (set via JAVA_HOME or .mvn/jvm.config)
mvn spring-boot:run
# Server runs on http://localhost:8080
```

**Database**:
```bash
# Using Docker for local PostgreSQL
docker run -d \
  --name trivia501-postgres \
  -e POSTGRES_DB=trivia501 \
  -e POSTGRES_USER=trivia501 \
  -e POSTGRES_PASSWORD=dev_password \
  -p 5432:5432 \
  postgres:15
```

### Testing Strategy

**Frontend**:
- Unit tests: Vitest (component logic, stores, utilities)
- E2E tests: Playwright (critical user flows)

**Backend**:
- Unit tests: JUnit 5 (service layer, game engine rules)
- Integration tests: Spring Boot Test + TestContainers (PostgreSQL)
- E2E tests: Full match flow (REST + WebSocket)

**Coverage Target**: > 80% for unit tests

## Deployment & Hosting

### Infrastructure Layout

```
[Vercel]                         [Fly.io]                        [Supabase]
frontend-react/  ──/api/*──▶  backend-rosy-cloud-4618  ───▶  PostgreSQL
Next.js 16                     Spring Boot 4.0.6              pgBouncer :6543
                                Java 25
```

The Vercel frontend proxies all `/api/*` requests to the Fly.io backend via `next.config.ts` rewrites. There is no direct database access from the frontend.

### Vercel (Frontend)

- **Project**: `trivia-501` (team: `fanaticpurifiers-projects`)
- **Root Directory**: `frontend-react` — **must be set in the Vercel dashboard** (Settings > General > Root Directory). If this is `.` the build fails with "Couldn't find any `pages` or `app` directory."
- **Framework**: Next.js (auto-detected)
- **Build**: triggers automatically on push to `master`. Manual: `vercel deploy --prod` from `frontend-react/`.

**Environment variables** (set via `vercel env add` or the dashboard):

| Variable | Purpose |
|---|---|
| `API_URL` | Backend base URL (`https://backend-rosy-cloud-4618.fly.dev`) |
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | Supabase anon key |

**How the API proxy works**: `next.config.ts` uses `rewrites()` to map `/api/:path*` → `${API_URL}/api/:path*`. In dev, `API_URL` defaults to `http://localhost:8080`. **The `API_URL` env var must be set on Vercel** — without it, all API calls fail in production.

**Critical rule**: after changing `next.config.ts`, commit the change — Vercel builds from the committed code, not your local filesystem.

### Fly.io (Backend)

- **App**: `backend-rosy-cloud-4618`
- **Config**: `backend/fly.toml` (committed to repo)
- **Region**: `sjc`; internal port `8080`; 1 GB RAM, 1 CPU
- **Deploy**: `fly deploy` from `backend/`.

**Secrets** (set via `fly secrets set KEY=VALUE`):

| Secret | Purpose |
|---|---|
| `DB_URL` | Supabase PostgreSQL JDBC URL |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `FOOTBALL501_FRONTEND_ORIGIN` | CORS allowed origin (`https://trivia-501.vercel.app`) |

**Critical — Spring profile**: **Do NOT set `SPRING_PROFILES_ACTIVE=prod`** until real JWT authentication is implemented and the frontend sends Bearer tokens with API calls. The `prod` profile disables `DevModeAuthFilter` (which injects a fixed authenticated principal for dev-mode permissive auth). If `prod` is active without a configured `SUPABASE_JWT_ISSUER` and JWT decoder, there is **no authentication mechanism at all** — every endpoint requiring `authenticated()` returns 403. When real auth ships, remove `DevModeAuthFilter` entirely, or keep it behind `@Profile("!prod")` and set both `SPRING_PROFILES_ACTIVE=prod` and `SUPABASE_JWT_ISSUER` together.

**Verification**: after deploying, check `GET /api/daily-challenge/status` returns 200 (not 403). If it returns 403, check whether `SPRING_PROFILES_ACTIVE` is set to `prod` without JWT config.

### Supabase (Database)

- PostgreSQL 15 with pgBouncer on port 6543 (transaction mode)
- Connection requires `prepareThreshold=0` in the JDBC URL for pgBouncer compatibility
- Direct connection on port 5432 available for operations needing prepared-statement caching (e.g. batch migrations)
- DB credentials are stored as Fly.io secrets, not in application properties

### Health Check URLs

| Environment | URL |
|---|---|
| Frontend (prod) | `https://trivia-501.vercel.app` |
| Backend (prod) | `https://backend-rosy-cloud-4618.fly.dev` |
| Backend health | `https://backend-rosy-cloud-4618.fly.dev/actuator/health` |
| Backend API categories | `https://backend-rosy-cloud-4618.fly.dev/api/categories` |

## Darts Score Validation

Critical utility for game engine - validates if a score is achievable in standard 501 darts:

**Invalid Scores**: 163, 166, 169, 172, 173, 175, 176, 178, 179

All other scores 1-180 are valid. Implementation should pre-compute this during answer population and store in `is_valid_darts` column.

## Environment Variables

Required environment variables:

```bash
# API-Football
FOOTBALL_API_KEY=your_api_key_here

# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=trivia501
DB_USER=trivia501
DB_PASSWORD=your_password_here

# JWT
JWT_SECRET=your_secret_key_here
JWT_EXPIRATION=86400000  # 24 hours in ms

# OAuth (when implemented)
OAUTH_GOOGLE_CLIENT_ID=
OAUTH_GOOGLE_CLIENT_SECRET=
OAUTH_APPLE_CLIENT_ID=
OAUTH_APPLE_CLIENT_SECRET=
OAUTH_FACEBOOK_CLIENT_ID=
OAUTH_FACEBOOK_CLIENT_SECRET=
```

## Performance Targets

| Metric | Target |
|--------|--------|
| API Response Time (p95) | < 200ms |
| PWA Load Time (3G) | < 3s |
| Database Query Time | < 50ms |

## Common Pitfalls to Avoid

1. **Never call API-Football during match validation** - Always use cached `answers` table
2. **Always validate darts scores** - Scores like 179 are invalid and must be treated as bust
3. **Server-side validation required** - Client optimistic updates must be confirmed by server
4. **Respect API rate limits** - Free tier is 100 req/day, plan batch operations carefully
5. **Fuzzy matching complexity** - Use PostgreSQL trigram indexes (`gin_trgm_ops`) for player name matching
6. **Consecutive timeout tracking** - Non-consecutive timeouts reset the timer back to default
7. **Never use `answers` as the autocomplete source** - It would reveal valid answers for the current question. Autocomplete must query the `entities` table only.
8. **Always populate `entities` when adding answers** - `AdminAnswerService` does this automatically via `EntitySearchService.upsertEntity()`. If running a manual SQL backfill, see `docs/design/AUTOCOMPLETE_ENTITY_DESIGN.md`.
9. **Match `entity_type` slug in question config to the `entities` pool** - If `config` says `"entity_type": "city"` but no city rows exist in `entities`, the autocomplete will silently return nothing. Seed the pool before activating the question type.
10. **Use `EntityType` constants, never bare strings** - Use `EntityType.FOOTBALLER`, `EntityType.CITY`, etc. (in `com.trivia501.model.EntityType`). Bare `"footballer"` literals were a Phase 5 audit finding; compile-time constants prevent silent slug drift.
11. **Do not add local `@ExceptionHandler` to controllers** - `GlobalExceptionHandler` (`com.trivia501.exception`) owns all error formatting. Local handlers produce inconsistent JSON shapes and were eliminated in Phase 5.
12. **Do not read `playerId` from request parameters in game controllers** - Identity comes from `Principal.getName()` parsed as a UUID. Adding a `@RequestParam UUID playerId` re-opens the identity spoofing vulnerability fixed in Phase 1.
13. **New model classes must use `@EntityListeners(AuditingEntityListener.class)`** - `@CreatedDate` and `@LastModifiedDate` only populate when this listener is registered. Manual `LocalDateTime.now()` in `@PrePersist` is the old pattern; do not reintroduce it.
14. **Seed data for new categories goes in CSV files, not inline SQL** — Large `INSERT`-heavy Flyway migrations are a code smell in portfolio review. Store seed data in `src/main/resources/db/data/<category>_answers.csv` and write a Java migration (`extends BaseJavaMigration`) that reads the CSV and batch-inserts. The CSV is reviewable, diffable, and trivially regeneratable from the source script. See V21/V22 for the pattern. Data CSVs live under `db/data/` — the `.gitignore` has an exception for `!**/db/data/*.csv`.
15. **Use `ON CONFLICT (slug) DO UPDATE SET id = EXCLUDED.id` in seed category inserts** — `DO NOTHING` silently skips when a category already exists with a different UUID, causing subsequent question inserts to fail with FK violations. `DO UPDATE` ensures the expected UUID is always present.
16. **Never set `SPRING_PROFILES_ACTIVE=prod` on Fly.io without also setting `SUPABASE_JWT_ISSUER`** — `prod` disables `DevModeAuthFilter`, and without JWT config there is zero auth mechanism. Every authenticated endpoint returns 403. These two must ship together when real auth is implemented.
18. **Vercel root directory must be `frontend-react`** — if someone resets it to `.` in the Vercel dashboard, the build fails. The setting is only visible in the dashboard (or via API); it's not stored in any file in the repo.
19. **After changing `next.config.ts`, commit and push** — Vercel builds from committed code. Local-only changes to the proxy config have no effect on production.

## Key Design Decisions

### Why Next.js + React?
- Migrated from SvelteKit (May 2026) for broader ecosystem and hiring pool
- App Router with `"use client"` pages matches the SPA mental model from SvelteKit
- Tailwind v4 `@theme` tokens map cleanly onto the existing CSS variable system
- React Context replaces per-page Svelte store patterns with minimal overhead

### Why PostgreSQL?
- ACID compliance for critical operations (game state, daily challenges)
- Full-text search with trigram matching for fuzzy player names
- JSON support for flexible data storage (question configs, custom rules)
- Free and open-source

### Why Aggressive Caching?
- API-Football free tier limits (100 req/day)
- Match validation must be instant (< 200ms)
- Offline capability for cached questions
- Cost optimization for MVP

### Why Server-Side Game Logic?
- Prevent cheating (client-side validation can be bypassed)
- Consistent rule enforcement across all clients
- Single source of truth for game state
- Easier to update rules without client updates

## Documentation References

- Product Requirements: `docs/PRD.md`
- Game Rules: `docs/GAME_RULES.md`
- **Backlog & Future Work: `docs/BACKLOG.md`** ← check this before starting any task; update it when you defer something or complete a backlog item
- Technical Design: `docs/design/TECHNICAL_DESIGN.md`
- API Integration: `docs/api/API_INTEGRATION.md`
- Autocomplete & Entity Architecture: `docs/design/AUTOCOMPLETE_ENTITY_DESIGN.md`
- Game Modes & Stretch Goals: `docs/design/GAME_MODES_STRETCH_GOALS.md`
- Question Difficulty Scoring: `docs/design/DIFFICULTY_SCORING.md`

## Question Difficulty Scoring

Questions use a **continuous `difficulty_score` (NUMERIC 0.00–10.00)** rather than a discrete enum. See `docs/design/DIFFICULTY_SCORING.md` for the full design.

**Key points for development**:
- Score is computed at materialisation time from four stored counts: `high_value_count`, `mid_range_count`, `checkout_count`, `total_valid_count`
- Storing the counts (not just the score) means formula tuning is a single SQL UPDATE — no re-materialisation needed
- `difficulty_locked BOOLEAN` allows admin override of computed scores
- `single_question_viable BOOLEAN` derived from `total_score_pool >= 501` — questions failing this are excluded from standard single-question mode
- The old `difficulty INTEGER` (1/2/3 scale, V4 migration) is deprecated but retained until all callers migrate

**Formula constants all live in `DifficultyConstants.java`** — never hardcode zone boundaries or weights elsewhere.

## Game Modes — What's Active & What's Parked

**Active**: Daily Challenge (core), Free Play (secondary). Both share the same game engine (`GameStateMachine`, `ScoringService`, `AnswerEvaluator`).

**Parked** (deferred indefinitely with multiplayer — 2026-06-08): Rapid Fire H2H, Draft Mode, Category Lock, Blind Mode, Tournament/League. See `docs/design/GAME_MODES_STRETCH_GOALS.md` for the original designs, retained for reference.

**Core principle to carry through all development**: The game's difficulty is strategic, not just knowledge-based. Knowing *when* to play an answer and *which score to target* is the depth — not just knowing valid names. Easy/medium questions can produce deeply engaging games.

**Active guardrails**:
- `game_mode VARCHAR` on `matches`, default `'STANDARD'` ✅ Done (V19)
- `suitable_for_daily BOOLEAN` on `questions` ✅ Done (V19)
- `difficulty_score NUMERIC(4,2)` on `questions` (continuous 0–10 scale)
- Question draw logic in a dedicated service method — never inline in game start code

**Parked guardrails** (multiplayer-only, no urgency):
- `question_id` on `game_moves` — Rapid Fire guardrail; not needed for current modes

## Current Development Phase

**Phase**: Single-Player Focus — Polishing Daily Challenge + Free Play

The five-phase audit campaign is complete. The product is now focused on single-player daily challenges with social sharing (Wordle-style) and Free Play mode. Multiplayer, competitive ranking, and MMR have been deferred indefinitely (2026-06-08).

**What was completed in the audit campaign**:
- Phase 1: Spring Security plumbing, identity spoofing fix, `@PreAuthorize` on admin controllers
- Phase 2: Backfill upsert (single SQL `INSERT … ON CONFLICT`), JPA auditing, normalization contract test
- Phase 3: `GameStateMachine` coordinator, `useGameLoop` hook, admin page decomposition
- Phase 4: `DifficultyCalculator`, viability gate, V13 migration, recalibration endpoint
- Phase 5: MapStruct mappers, `GlobalExceptionHandler`, `EntityType` constants, SvelteKit frontend deleted

**Recently completed**:
- Daily Challenge mode: V19 schema, V20 data backfill, DailyChallengeService, DailyChallengeScheduler, DailyChallengeController, frontend daily cards + /daily pages + emoji-grid sharing
- Free Play mode (formerly "practice"): lobby drill-down nav (V24–V26), league question scoping (V28, V30), entity autocomplete caching, staged answer flow with THROW DART button
- Geography + Film categories: CSV-based Java Flyway migrations (V21, V22)
- Supabase JWT auth + Google OAuth: social login end-to-end, HTTPOnly cookie sessions

**Current P0 priorities** (see `docs/BACKLOG.md` for full details):
1. Remove test question from daily challenge pool
2. Guest/anonymous play for core experience (no sign-in required)
3. Expand daily challenge starting score pool from 9 to 20–30
4. Reframe practice mode as Free Play (rename, remove practice language)
5. Frontend test suite
6. Error boundaries
7. Data population (run scraper against live DB)
8. Solo forfeit game completion fix

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

<!-- dial-settings:start -->
<!-- dials: Detail=Standard; Presentation=Heavy structure; Tone=Casual; Rigor=Adversarial; Autonomy=Check key decisions -->
**Interaction calibration (set via /calibrate). Follow these directives in this project:**
- Detail = Standard: Give a thorough but tightly bounded answer: cover only the main points and key caveats, then stop — no exhaustive edge cases, tangents, or deep-dives. Keep the total to roughly two short paragraphs' worth of content, and hold that as a hard limit even if rigor, autonomy, or thoroughness elsewhere pushes for more: say less, not more. This caps the AMOUNT of content, not its shape — present that small amount in whatever format is requested (a few bullets, a small table, or headings are all fine, as long as the content stays within the cap).
- Presentation = Heavy structure: Organize the answer as a scannable, structured document: use section headings, bullet or numbered lists, and a table where it fits. Lead with structure rather than long prose paragraphs.
- Tone = Casual: Write in a relaxed, casual register, like texting a friend who knows the subject. Use contractions freely, everyday and colloquial words, a light aside or bit of humor, and natural punctuation including the occasional exclamation point or sentence fragment. Keep it warm and chatty rather than buttoned-up; do not slip into a formal, reserved tone.
- Rigor = Adversarial: Stress-test the idea adversarially: actively try to break it, attacking its weakest assumptions and strongest failure modes.
- Autonomy = Check key decisions: Identify the single most consequential ambiguity in the request — the one decision that most changes the result — and ask the user about THAT one before producing the main deliverable, waiting on their answer. State any minor assumptions explicitly but proceed on them. Do NOT interrogate every detail or propose a stage-by-stage sign-off plan (that is a more hands-on setting), and do NOT just deliver the whole thing on your own assumptions (that is a more autonomous setting): hold back the bulk of the work pending that one key answer.
<!-- dial-settings:end -->
