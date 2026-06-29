# 🎯 Trivia 501

> A daily football trivia puzzle with darts scoring mechanics — play at **[trivia-501.vercel.app](https://trivia-501.vercel.app/)**

---

## What I Built

**Trivia 501** is a Wordle-style daily puzzle game that mashes up football trivia with the scoring system of darts 501. Each day you get a question (e.g. "Appearances for Arsenal in the Premier League") and a target score. You name players whose real-world stats match the question, and their numbers get deducted from your score. The goal? Hit exactly zero — just like finishing a leg of darts.

The game has two modes:

- **Daily Challenge** — one question per category per day, same starting score for everyone, one trust-based attempt. When you finish, you get an emoji grid (🟩 valid throw, 🟥 bust, 🎯 checkout) to share with friends. No leaderboards — the social mechanic is comparing results, not competing for rank.

- **Free Play** — pick any category and question, set your own target score, and play on your own terms. No daily limit, no pressure.

The entire thing runs as a single-player experience. Real-time multiplayer, MMR ranking, league tiers, and matchmaking were all designed, partially scaffolded, and then **deliberately cut** when it became clear they didn't serve the core product. The game is better as a daily puzzle you play and share than as a competitive ladder — and the code is cleaner for it.

---

## How I Built It

```
[Vercel]                              [Fly.io]                           [Supabase]
Next.js 16 + React 19  ── /api/* ──▶  Spring Boot 4.0.6  ──────────▶  PostgreSQL 15
                                      Java 25                           pgBouncer :6543
```

### Tech Stack

| Layer | Technology | Why |
|---|---|---|
| **Frontend** | Next.js 16 (App Router) + React 19 + TypeScript | Migrated from SvelteKit mid-project for broader ecosystem and hiring-pool alignment. App Router with `"use client"` pages keeps the SPA feel. |
| **Styling** | Tailwind CSS v4 | Utility-first, with semantic CSS custom properties (`--color-surface`, `--color-ink`, etc.) for dark/light theme switching via `data-theme`. No config file — everything in `globals.css` with `@theme inline`. |
| **State** | React Context + `useState` | No Redux, no Zustand. The app has two contexts (auth + toasts) and local state in custom hooks. Simple enough that a state library would be overkill. |
| **Backend** | Spring Boot 4.0.6 + Java 25 | Chosen for its mature ecosystem (security, JPA, scheduling, Flyway) and because Java's type system catches the kind of bugs that game logic is prone to. |
| **Database** | PostgreSQL 15 (Supabase) | ACID compliance for game state, GIN trigram indexes for fuzzy player-name matching, and JSONB for flexible question configs. |
| **Auth** | Supabase Auth (Google OAuth) + anonymous guest sessions | `OptionalJwtFilter` creates a UUID cookie for guest players with a 24-hour sliding expiry. No sign-in wall anywhere in the core game loop. Social login uses HTTPOnly cookies via `@supabase/ssr`. |
| **Data** | ScraperFC (Python microservice) | A separate Python service populates the database via batch jobs. The backend never calls external APIs during gameplay — all answer validation reads from cached database tables. |
| **Deployment** | Vercel (frontend) + Fly.io (backend) | Vercel proxies `/api/*` to Fly.io via `next.config.ts` rewrites. Supabase handles the database with pgBouncer on port 6543. |
| **Testing** | Vitest + Playwright / JUnit 5 + TestContainers | 135 frontend behaviour tests, 252 backend tests. TestContainers spins up real PostgreSQL for integration tests. |

---

## Why I Built It This Way

### Server-side validation for everything

The client updates optimistically — type a name, see it deducted from your score — but the server has the final word. Every move runs through `GameStateMachine`, which validates darts scores, checks for busts, enforces the checkout window, and rejects duplicate answers. The frontend is a remote control, not an authority.

The payoff is that cheating is genuinely hard: you can't fabricate a share result without the server's sign-off, and the answer pool is pre-cached so you can't probe the live API.

### Zero external API calls during gameplay

All player statistics live in the `answers` table, populated in advance by the Python scraper. When you submit a player name, the backend hits a GIN-trigram index on PostgreSQL — not an external football API. This keeps response times predictable (< 200ms p95) and avoids the rate limits and cost of live API calls.

### PostgreSQL for the weird stuff

The game needs accent-insensitive substring matching ("aguero" → "Sergio Agüero"), which PostgreSQL handles natively with `unaccent()` and `gin_trgm_ops` indexes. The `entities` table powers the autocomplete dropdown without revealing which names are correct answers — it's intentionally decoupled from the `answers` table that drives validation.

### Lazy daily challenge creation

Daily challenges are pre-selected by a midnight cron job, but if the cron misses (server restart, deployment), the `GET /{categorySlug}` endpoint lazily creates the challenge on first access. The design means the daily challenge is never "down" — it's either pre-computed or created just-in-time.

### Aggressively cut scope

The single best engineering decision on this project was deleting code. Multiplayer (`player2_*` columns, WebSocket STOMP protocol, matchmaking queue), the 36-rank MMR/league system, freemium subscriptions, and AI opponents were all designed and partially built. When the product direction clarified toward single-player daily puzzles, all of it came out — ~2,000 lines of production code and tests. The engine is now a clean solo-only state machine with no `isSolo` branching.

---

## What I'd Do Differently

- **Start with the frontend framework I'd ship with.** The project began in SvelteKit, which was great to work with, but migrating to React mid-stream cost weeks. If I'd known the hiring landscape better at the start, I'd have picked React from day one.

- **Default to solo until multiplayer is proven.** The original schema was built for 1v1 matches — `player2_id`, `player2_score`, close-finish rules, the works. That scaffolding sat in the codebase for months adding cognitive overhead to every change before I finally stripped it out. When multiplayer comes back, it'll be designed from a real product spec, not a hypothetical one.

- **Ship the cheap version of social features first.** Emoji-grid sharing took an afternoon and gives the game its viral hook. I'd front-load more of those zero-backend social mechanics earlier in the build.

- **CSV seed data from the start.** Early category migrations used inline SQL INSERTs — 12,000-line Flyway files that were unreadable and unmaintainable. Switched to CSV data files + Java migrations; the CSVs are diffable, reviewable, and trivially regeneratable from the scraper.

---

## Quick Start

```bash
# Backend (requires Java 25)
cd backend
./mvnw spring-boot:run          # → http://localhost:8080

# Frontend
cd frontend-react
npm install
npm run dev                      # → http://localhost:3000
                                  # API calls proxy to localhost:8080

# Database (Docker for local dev)
docker run -d \
  --name trivia501-postgres \
  -e POSTGRES_DB=trivia501 \
  -e POSTGRES_USER=trivia501 \
  -e POSTGRES_PASSWORD=dev_password \
  -p 5432:5432 \
  postgres:15
```

Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as environment variables pointing at your database. Flyway migrations run automatically on startup.

The backend health check: [`/actuator/health`](https://backend-rosy-cloud-4618.fly.dev/actuator/health)

---

## Docs

Most of these were written during the planning phase. Some still reference deferred multiplayer features — I've kept them because the design thinking is sound, even if the implementation isn't current.

- **[Game Rules](docs/GAME_RULES.md)** — complete rules, scoring, bust logic, checkout range
- **[Product Requirements](docs/PRD.md)** — original vision (note: multiplayer/freemium sections are parked)
- **[Technical Design](docs/design/TECHNICAL_DESIGN.md)** — system architecture, database schema, API design
- **[Backlog](docs/BACKLOG.md)** — living document of deferred work, stretch goals, and completed items
- **[Autocomplete & Entity Architecture](docs/design/AUTOCOMPLETE_ENTITY_DESIGN.md)** — how the search-as-you-type player name input works
- **[Difficulty Scoring](docs/design/DIFFICULTY_SCORING.md)** — the continuous 0–10 scoring formula for questions
- **[API Integration](docs/api/API_INTEGRATION.md)** — how the ScraperFC Python service feeds data into the backend
- **[Game Modes (Parked)](docs/design/GAME_MODES_STRETCH_GOALS.md)** — designs for Rapid Fire, Draft, and other modes, retained for reference

---

## License

MIT

---

Built by [Paul O'Neill](https://github.com/paul-anthony-oneill) — a portfolio project showing full-stack development, game design, system architecture, and the judgement to delete code that isn't pulling its weight.
