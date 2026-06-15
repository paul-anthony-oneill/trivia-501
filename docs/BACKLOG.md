# Trivia 501 — Backlog & Future Work

**Last updated**: 2026-06-14 (added P2 item: Go Result Integrity Service)  
**Purpose**: Living document capturing all deferred work, stretch goals, and improvement ideas regardless of size or urgency. Update this whenever a decision is made to defer something, or when a backlog item is completed or abandoned.

**Product direction** (2026-06-08): The game is now focused on **single-player daily challenges** as the core experience — Wordle-style social trivia where everyone plays the same question with the same parameters each day and shares results with friends. A separate **Free Play** mode (formerly "practice") lets players pick any category/question to play on their own terms. Async friend challenges (play the same question and compare) are a planned social feature. The ranking/MMR/league-tier system and real-time multiplayer are deferred indefinitely.

---

## How to Read This Document

| Priority | Meaning |
|---|---|
| **P0 — Launch Blocker** | Must ship before any real users can play |
| **P1 — Shortly After Launch** | Important but doesn't block the core game loop |
| **P2 — Stretch Goal** | Designed and documented; not being built until core is stable |
| **Guardrail** | Small cheap schema/code decision to make now so a future item stays open |
| **Parked** | Explicitly deferred; revisit when the reason for deferral no longer applies |

---

## Recently Completed

| Item | Migration/PR | Notes |
|---|---|---|
| Clean per-season football questions from DB | V28 | Deleted all `football.team_competition_season_metric` questions + answers + dependent rows; deactivated V11 templates. |
| Fix league-level question metadata | V28 | Backfilled `q_scope='league'`, `q_league`, `q_stat` on V12 `player_competition_metric_since` questions so `findRandomFootballLeagueQuestion()` can surface them. Set `q_scope='career'` on career questions. |
| Add league-scope Appearances, Goals+Appearances, Assists+Appearances questions | V30 | Seeded 3 new `player_competition_metric_since` templates; created one question per tier-1 domestic league; materialized answers and difficulty metrics inline. |
| Remove test question from daily challenge pool | V31 | V31 migration flips `suitable_for_daily = false` on test-category questions; `DailyChallengeScheduler` skips test category by slug; `DailyChallengeService.createChallenge()` guards with explicit exception. |
| Reframe practice mode as Free Play | — | Renamed `SoloGameController` → `FreePlayController`, `StartSoloGameRequest` → `StartFreePlayRequest`, `/api/solo` → `/api/freeplay`. All frontend `"solo"` game type → `"freeplay"`. Test files and docs updated.
| Expand daily challenge starting score pool | — | 9 → 30 curated scores (101–501); `first-move viability` guard rejects questions where every answer exceeds the starting score + 10pt checkout margin; `anti-consecutive-repeat` avoids yesterday's score per category; shared `DifficultyConstants.DAILY_STARTING_SCORES` + `pickDailyStartingScore()`. |
| Auth: Guest play with 24-hour session timeout | — | `OptionalJwtFilter` already handled anonymous UUID cookie creation; added 24-hour sliding MaxAge — cookie renewed on every request so active players keep their session, abandoned sessions auto-expire after a day. No sign-in walls exist anywhere in the core game loop. |
| Error boundaries | — | `ErrorBoundary` class component catches render errors; wrapped around lobby, game, and admin sections with per-section recovery UI + reload button. Admin sidebar lives outside the boundary so navigation survives page crashes. |
| Solo forfeit game completion | — | Wired `handleGameCompletion` into `processPlayerMove` and `handleTimeout` (was previously only called from tests). Solo forfeit (null winner) → match ABANDONED instead of stuck IN_PROGRESS. Uses `@Lazy MatchService` to break the circular dep. |
| UI/UX redesign — unified design system with dark/light themes | — | Replaced the dual editorial/teletext aesthetic with one token-based design system (`globals.css`): semantic CSS variables (bg/surface/ink/muted/line/accent/ok/gold/danger) for dark + light themes, toggled via `data-theme` on `<html>` (ThemeToggle component, localStorage + system-preference default, no-FOUC inline script in `layout.tsx`). Monochrome chrome — colour reserved for game state (green=valid, red=bust, gold=checkout) + bullseye-red brand accent. VT323/teletext retired; fonts now Bricolage (display), Hanken (body), Plex Mono (labels). Redesigned: LobbyView (dartboard-ring motif), MatchView (score hero + checkout progress track + win overlay with ring-burst), EntitySearch dropdown, HowToPlayPanel, AnimatedScorePopup, daily pages, toasts, ConfirmDialog, DebugPanel. Deleted dead `CategoryPopup.tsx`. Admin pages out of scope (re-pinned `--color-surface` in admin layout). Verified in browser: both themes, mobile 375px + desktop, full game flow incl. bust/checkout. |
| Flyway V31 version collision fix | V36 | `V31__unmark_test_questions_daily.sql` collided with `V31__activate_geography_and_film_questions.sql` — backend could not boot (FlywayException). Renamed to `V36__unmark_test_questions_daily.sql`; safe because the duplicate version meant it had never been applied anywhere. |
| MatchView 30s clock re-render | — | Resolved by the UI redesign: the teletext header status line was deleted and the `setInterval`/`setNow` state went with it. Was an Architecture & Code Quality finding (2026-06-09 review). |
| Delete dead `questionHierarchy.ts` | — | Orphaned when the lobby drill-down nav replaced `CategoryPopup.tsx` (deleted in the UI redesign); grep confirmed zero remaining imports. Closes the "deferred cleanup" note on the lobby redesign row below. |
| Frontend test suite — Phase 1 | — | 99 behaviour tests across 5 files: share-grid emoji encoding (21 tests, exhaustive), country/flag utilities (18 tests), `apiFetch` auth injection (7 tests), `adminApi` URL construction + error handling (24 tests), `useGameLoop` state transitions + submit + popup + session restore (29 tests). Vitest + React Testing Library + jsdom configured. Extracted `buildShareText` pure utility from `page.tsx` to make share logic testable. `package.json` scripts: `npm test` (vitest run), `npm run test:watch` (vitest). TypeScript compiles clean. |

---

## P0 — Launch Blockers

These items must be complete before real players can use the game.

### Admin link: gate behind real admin role check
- **What**: The Admin link in the LobbyView header is currently hidden client-side using `user?.app_metadata?.role === "admin"`. This is a display-only guard — Supabase `app_metadata` must actually be set to `{"role": "admin"}` on the admin user via the Supabase dashboard or service-role API. Until that metadata is set, the link stays hidden even for real admins. Verify the field is set correctly on the production admin account before launch.
- **Why deferred**: Admin access is backed by `@PreAuthorize("hasRole('ADMIN')")` on the backend, so the link being visible is cosmetic-only and not a security risk. The metadata provisioning step is a deployment/ops task.
- **See**: `LobbyView.tsx` (header), `AuthContext.tsx`, Supabase dashboard → Auth → Users.

### SECURITY: DebugPanel ships the daily challenge answer key to all players
- **What**: 2026-06-11 security review finding (Medium severity). The in-game `DebugPanel` (`[?] DEBUG` button + Ctrl+Shift+D) is mounted unconditionally in `MatchView.tsx` with no `NODE_ENV` guard, and is backed by two ungated endpoints — `GET /api/daily-challenge/games/{gameId}/answers` and `GET /api/freeplay/games/{gameId}/answers` — that return the complete answer key (every valid answer, score, and validity flag). The daily-challenge variant matches the `GET /api/daily-challenge/** → permitAll` rule in `SecurityConfig`, so it is reachable with no authentication at all. Any player can one-click reveal all answers for today's daily challenge, defeating the core Wordle-style mechanic. "Trust-based" design tolerates determined cheaters looking up stats externally; it does not mean shipping a frictionless built-in answer reveal to every user.
- **Fix**: Gate the `DebugPanel` mount behind `process.env.NODE_ENV !== "production"` (or remove it), and gate both `/answers` endpoints behind `@PreAuthorize("hasRole('ADMIN')")` or a `@Profile("!prod")` dev-only controller. See also the related ownership-check item in Architecture & Code Quality.
- **Files**: `frontend-react/src/components/game/match/MatchView.tsx:396`, `frontend-react/src/components/game/DebugPanel.tsx`, `DailyChallengeController.java:295–312`, `FreePlayController.java:306–323`, `SecurityConfig.java:85`

### Frontend test suite — Phase 2 (component & integration tests)
- **What**: Phase 1 (behaviour tests) is complete — 99 tests covering share-grid encoding, country utilities, `apiFetch`, `adminApi`, and `useGameLoop`. Phase 2 adds component tests (answer input flow), integration tests (daily challenge browse → start → play → share), and smoke tests (auth login/logout, guest path). Deferred until visual design stabilises.
- **See**: `__tests__/` directory under `frontend-react/`; Vitest + React Testing Library setup.

### Data population — run the scraper against the live database
- **What**: The Python scraper service (`trivia-501-scraper/`) has never been run against the Supabase production database. Geography and Film categories were only just activated. Question difficulty scores were computed on whatever data existed at the time — the scores are only as good as the underlying answer counts. Without a full data population pass, question pools are thin and difficulty ratings are unreliable.
- **Why deferred**: The scraper is a separate Python microservice that needs its own deployment and scheduling infrastructure. Acceptable for dev; unacceptable for real players.
- **See**: `trivia-501-scraper/`, `QuestionMaterializerService.java`, `DifficultyCalculator.java`.

---

## Deferred Indefinitely — Multiplayer & Competitive Ranking

The following items were previously P0/P1 launch blockers but are now parked. The product focus has shifted to single-player daily challenges and Free Play. These may return as a future phase but have no timeline.

### Real-time multiplayer (WebSocket STOMP)
- **What**: 1v1 real-time match communication over WebSocket (STOMP protocol). `GameStateMachine` and `GameService` have hooks for it, but no WebSocket handler is wired. The two-player turn alternation, opponent tracking, and match completion logic in `GameStateMachine` are designed for this but only tested via solo play.
- **Why parked**: Focus is on single-player daily challenges and Free Play. Multiplayer adds significant complexity (matchmaking, reconnection, timeout arbitration) that doesn't serve the current product direction.
- **What stays**: `GameStateMachine` two-player logic, `MatchFormat` best-of-3/5, `player2Id` fields — these are not being removed, just not built out.
- **See**: `docs/design/TECHNICAL_DESIGN.md`, `CLAUDE.md` — WebSocket Protocol section.

### MMR, league tiers, and ranked play
- **What**: The 9-tier × 4-subtier ranking system (Sunday League → Icon), hidden MMR, Elo-based matchmaking, and ranked/casual mode split described in the PRD.
- **Why parked**: No multiplayer means no ranked play. Player profiles stay as a personal dashboard (stats, history, personal bests) without competitive ranking.
- **What stays**: `player_profiles` table (V23 migration) — repurposed as a personal stats/history store. The `league_tier` and `league_subtier` columns may be dropped or left unused.
- **See**: PRD §4.1, §4.2; `player_profiles` table.

### Matchmaking queue
- **What**: `matchmaking_queue` table, opponent finding, Elo-based pairing.
- **Why parked**: No multiplayer.
- **See**: `docs/design/TECHNICAL_DESIGN.md`.

### Multiplayer game modes (Rapid Fire, Draft, Category Lock, Blind, Tournament)
- **What**: All P2 stretch game modes that require two players.
- **Why parked**: These depend on multiplayer infrastructure that is now deferred indefinitely. The design docs stay for reference; no implementation priority.
- **See**: `docs/design/GAME_MODES_STRETCH_GOALS.md`.

---

## Architecture & Code Quality

Findings from the 2026-06-09 architectural review. Ordered by severity. None are launch blockers but the P1 items should be addressed before the codebase grows further.

### SECURITY: debug `/answers` endpoints compute `playerIdFrom(principal)` then discard it — no game-ownership check
- **Severity**: Medium — missing authorization on a data-exposure endpoint (2026-06-11 security review)
- **What**: Both `getGameAnswers` endpoints resolve the caller's player ID from the principal and then never compare it against the game's owner. Anyone who knows a `gameId` can dump that game's full answer key. UUIDs are unguessable so the blast radius is limited to one's own games in practice, but the pattern is a latent authorization gap — and the daily-challenge variant is `permitAll`, so not even an anonymous session is required. Contrast with the rest of the controllers, where identity from `Principal.getName()` is actually enforced.
- **Fix**: If the endpoints survive the P0 gating fix (see "SECURITY: DebugPanel ships the daily challenge answer key" in P0), enforce ownership: load the game, compare its player ID to `playerIdFrom(principal)`, return 403 on mismatch. Apply the same check in any future per-game debug/inspection endpoint.
- **Files**: `DailyChallengeController.java:295–312` (playerId computed at :300, unused), `FreePlayController.java:306–323` (playerId computed at :311, unused)

### BUG: `useGameLoop.startNewGame` sends to wrong API path when called from a Daily Challenge game
- **Severity**: Bug — silent failure for "Play Again" from Daily Challenge win screen
- **What**: `startNewGame` calls `setGameType("freeplay")` then immediately calls `apiBase()` in the same synchronous function. React state updates are async, so `apiBase()` returns the **old** `gameType` ("daily-challenge"). The game start POST goes to `/api/daily-challenge/start` (non-existent) instead of `/api/freeplay/start`. The user sees "Failed to start game" with no explanation.
- **Fix**: Don't call `apiBase()` inside `startNewGame`. Hardcode `/api/freeplay/start` and derive the abandon path from the captured `gameType` closure variable directly, not via `apiBase()`.
- **Files**: `frontend-react/src/hooks/useGameLoop.ts:234–270`

### N+1 query + repository injected into `DailyChallengeController`
- **Severity**: Performance bug + layering violation
- **What**: `getStatus()` loops through all today's challenges and fires 2 DB queries per challenge (category lookup + question lookup). For 5 challenges: 11 queries per status poll. `CategoryRepository` and `AnswerRepository` are both injected directly into the controller — controllers should never hold repositories.
- **Fix**: Move the response-assembly logic into `DailyChallengeService.getTodaysChallengesStatus()`. The service batch-fetches categories and questions by ID in 2 queries total. Remove `CategoryRepository` from the controller. Both controllers also hold `AnswerRepository` directly for the debug endpoint — move to a service method.
- **Files**: `DailyChallengeController.java:41–43,68–90`, `FreePlayController.java:57`

### Controller duplication: `DailyChallengeController` and `FreePlayController` share ~60% identical code
- **Severity**: High — any change to game state response shape must be made twice
- **What**: Four private helpers are verbatim copy-paste between both controllers (`playerIdFrom`, `buildGameStateResponse` ×2, `toMoveDto`). Four endpoint bodies are near-identical (`submitAnswer`, `getGameState`, `abandonGame`, `getGameAnswers`). `buildGameStateResponse` also inlines business logic (entity type extraction from config, win determination) that belongs in the service layer.
- **Fix**: Extract `GameResponseAssembler` class holding the four helpers. Both controllers inject it. The unique endpoints stay per-controller. Move entity type resolution and win determination into `GameService` or a dedicated assembler service.
- **Files**: `DailyChallengeController.java:316–374`, `FreePlayController.java:332–390`

### `GameService` ↔ `MatchService` circular dependency resolved with `@Lazy`
- **Severity**: High — structural smell, can cause subtle proxy issues
- **What**: `GameService` uses `@Lazy MatchService` to avoid a Spring startup cycle. The root cause: `GameService.processPlayerMove` calls `matchService.handleGameCompletion`. This is documented in the "solo forfeit" completed item but the root cause was never fixed.
- **Fix**: Publish a Spring `ApplicationEvent` (`GameCompletedEvent`) from `GameService` after a game ends. `MatchService` listens with `@EventListener`. This breaks the cycle without `@Lazy` and decouples the two services cleanly.
- **Files**: `GameService.java:60`, `MatchService.java`

### `abandonActiveGamesForPlayer` and `abandonStaleGames` duplicate the same loop body
- **Severity**: Medium
- **What**: The "set game ABANDONED + set parent match ABANDONED" logic is written verbatim twice in `GameService` (and a third time in `abandonGame`). A bug fix to one site is never applied to the others.
- **Fix**: Extract private `abandonGameAndMatch(Game game)` helper; all three call sites delegate to it.
- **Files**: `GameService.java:182–207,285–320`

### `Move` and `GameHints` types defined twice in the frontend
- **Severity**: Medium
- **What**: Both interfaces are defined identically in `useGameLoop.ts` and `MatchView.tsx`. `MatchView` should import from `useGameLoop` (they're already exported there).
- **Files**: `useGameLoop.ts:11–29`, `MatchView.tsx:16–29`

### `useGameLoop` does too much — theme manipulation and session persistence mixed with game logic
- **Severity**: Medium
- **What**: The 430-line hook owns game state, API calls, session persistence, popup coordination, restore-on-mount, and **DOM body class manipulation** (`document.body.classList.remove/add("theme-*")`) — 4 call sites inside a game-loop hook. **Note (2026-06-11 redesign): the `theme-home`/`theme-teletext` classes are now pure dead code** — they have zero occurrences in `globals.css` (the unified design system themes via `data-theme` on `<html>` instead) and are held alive only by assertions in `useGameLoop.test.ts`. Deleting the `classList` calls and the test assertions together is a trivial standalone cleanup that doesn't need to wait for the larger hook decomposition. Session persistence helpers (`saveGameState`, `loadSavedGameState`, `clearSavedGameState`) should be extracted to a separate utility module.
- **Files**: `useGameLoop.ts:214,270,315,402`

### `DailyChallengeController.getShareData()` linear-scans all challenges to find one by question ID
- **Severity**: Medium
- **What**: `getTodaysChallenges().stream().filter(dc -> dc.getQuestionId().equals(...))` fetches all daily challenges to find one specific row. Add `findByChallengeDateAndQuestionId()` to `DailyChallengeRepository` instead.
- **Files**: `DailyChallengeController.java:247–250`, `DailyChallengeRepository.java`

### `AnswerEvaluator.getTopAnswers` fetches all rows then limits in Java
- **Severity**: Medium
- **What**: `findTopAnswers()` returns the full answer list; `.stream().limit(limit)` discards the excess. The limit should be pushed into the JPQL query via `Pageable` or a `LIMIT` clause.
- **Files**: `AnswerEvaluator.java:225–229`

### `PlayerProfileService` referenced by FQCN in both controllers (missing import)
- **Severity**: Low — cosmetic but signals a hidden name conflict
- **What**: Both controllers use `com.trivia501.service.PlayerProfileService` as a fully-qualified name in the field declaration. Add the import and resolve whatever conflict caused this.
- **Files**: `DailyChallengeController.java:42`, `FreePlayController.java:56`

### `LobbyView` leagues and stat types are hardcoded; `resolveTarget("random")` pool is narrower than backend
- **Severity**: Low
- **What**: `LEAGUES` and `STAT_TYPES` are baked into the component. If the backend adds a league, the frontend silently omits it. Extract to `src/lib/constants/lobbyOptions.ts`. Separately: the "RND" target score button picks from `[501, 301, 101]` only; the backend pool now has 30 values. Either defer to backend for random selection or expand the client pool.
- **Files**: `LobbyView.tsx:16–46`

### `ThemeToggle` placement is inconsistent across pages (2026-06-11 redesign)
- **Severity**: Medium — UX gap on the share deep-link page
- **What**: The dark/light toggle is rendered ad-hoc in three places (`LobbyView` header, `MatchView` header, `/daily` page) but is **missing from `/daily/[category]`** — the deep-link target for shared results, i.e. the first page a new player arriving via a share URL ever sees. That player has no way to switch theme. The structural fix is hoisting the toggle into a shared header component (or `layout.tsx`) instead of duplicating it per-page; a shared header would also give the "mode label/badge component" frontend guardrail a natural home.
- **Files**: `frontend-react/src/components/ui/ThemeToggle.tsx`, `frontend-react/src/app/daily/[category]/page.tsx`, `LobbyView.tsx`, `MatchView.tsx`, `frontend-react/src/app/daily/page.tsx`

### Admin pages opted out of the unified design system (2026-06-11 redesign)
- **Severity**: Low — deliberate stopgap, will rot as the design system evolves
- **What**: The redesign scoped admin pages out. `admin/layout.tsx` re-pins `--color-surface: #1a1a1a` inline so admin keeps its original dark palette regardless of the `data-theme` toggle. This works but means admin diverges from the token system — it ignores light theme, and any future token rename or removal silently breaks the pin. Bring admin pages into the unified design system (semantic tokens, both themes) and remove the `--color-surface` re-pin.
- **Files**: `frontend-react/src/app/admin/layout.tsx:14–20`, admin components under `frontend-react/src/components/admin/`

---

### 2026-06-11 Usability Audit — Hand-Coding Practice Set

Findings from the 2026-06-11 frontend design audit (15-principle heuristic evaluation). These are deliberately documented as a **learning backlog**: each item names the usability principle violated, explains *why* it matters to users, gives a **Direction** hint (where to look and what concept applies — not the code itself), a **Verify** step to confirm the fix by hand, and **Learning notes** mapping the React/frontend concept to Java/Spring equivalents for interview framing.

**How to use this set**: work items in any order — each has a **Severity** (3 = users struggle, 2 = users notice, 1 = cosmetic) and an **Area** tag (Frontend-React / Frontend-CSS / Cross-stack) so you can go top-down by impact or alternate areas. After each change: `cd frontend-react && npx tsc --noEmit && npm test`, then check the behaviour in the browser (`npm run dev`).

**Not in this list**: the Severity-4 finding (DebugPanel ships the answer key) is already a P0 launch blocker — see "SECURITY: DebugPanel ships the daily challenge answer key to all players" above. Items overlapping the P1 "Loading, error, and empty state consistency" item are listed individually here because they are concrete, self-contained exercises.

### UX: One click on a daily challenge card consumes the day's only attempt — no confirmation
- **Severity**: 3 — Error Prevention. Irreversible action (the daily is one-attempt-per-day) triggered by a single tap, with the only warning in a 10px label that is `hidden sm:block` (invisible on mobile).
- **Area**: Frontend-React (conditional rendering, component reuse)
- **What**: Clicking a daily card in `LobbyView` calls `onStartDailyChallenge` immediately. A player tapping a card just to *see* the challenge has spent their attempt. For a Wordle-style game, accidentally burning the daily is the most rage-inducing failure mode possible.
- **Direction**: The codebase already has two ready-made solutions — a `ConfirmDialog` component (see how `MatchView` wires `showExitConfirm` state to it), and an interstitial page at `/daily/[category]` with a deliberate "Play now" button. Pick one. If you choose the dialog, think about what state the lobby needs to remember between "card clicked" and "confirmed" (which challenge was it?). Also fix the mobile-hidden warning text.
- **Verify**: On a 375px viewport, tap a daily card — you should get a chance to back out, and the "one attempt" message should be visible.
- **Files**: `frontend-react/src/components/game/lobby/LobbyView.tsx:198–232`, `frontend-react/src/components/ui/ConfirmDialog.tsx`
- **Learning notes**: "Hold a pending value in state, act on confirm" is the React equivalent of a two-phase commit in a service method. Interview angle: confirmation friction vs error prevention trade-off — when is a confirm dialog justified? (Answer: when the action is irreversible and accidental triggering is plausible.)

### UX: No pending state when starting a game from the lobby — double-click fires duplicate starts
- **Severity**: 3 — Visibility of System Status + Error Prevention. The start flow is two network calls (abandon + POST start) with zero feedback; nothing disables, so users click again.
- **Area**: Frontend-React (async state tracking, disabled patterns)
- **What**: `LobbyView`'s `startGame` and the daily cards give no feedback between click and game start. On a slow connection users re-click — possibly a *different* row — firing overlapping starts. The `/daily` page already solves this correctly with a `starting` state; the lobby (the primary entry point) doesn't.
- **Direction**: Look at how `DailyPage` (`app/daily/page.tsx`) tracks `starting: string | null` and uses it to disable the clicked button and change its label. The lobby needs the same idea, but the state must cover *all* rows at once (any in-flight start should disable every row). Think about where that state has to live so both the daily cards and the `NavRow`s can see it.
- **Verify**: Throttle the network in DevTools (Slow 3G), click a category row — the row should show a busy indicator and a second click anywhere should do nothing.
- **Files**: `frontend-react/src/components/game/lobby/LobbyView.tsx:97–102,507–546`, pattern reference: `frontend-react/src/app/daily/page.tsx:14,108–114`
- **Learning notes**: This is the frontend half of idempotency. You know the backend half (unique constraints, `@Transactional`); the UI half is "disable the trigger while the request is in flight." Interview angle: client-side disabling is UX, not a security boundary — the server must still tolerate duplicates.

### UX: The game has a win overlay but no loss state
- **Severity**: 3 — Visibility of System Status / Perceptibility. Bust-out (no valid moves remain) is a legal game ending with no UI; restored `COMPLETED` games always show the *win* screen.
- **Area**: Cross-stack (API contract design + React conditional rendering)
- **What**: `MatchView` renders an end-of-game overlay only when `isWin` is true, and `useGameLoop` only ends the game on `r.isWin`. A player who busts out of a daily sits in a dead game with no terminal feedback. Worse: the restore path maps any server `status === "COMPLETED"` to the win overlay — a restored *lost* game says "Game shot!".
- **Direction**: Start on the backend (your home turf): check what `GameStateMachine`/`GameService` actually return when a solo game ends without a checkout — is there a field distinguishing win from bust-out completion? If not, that's the first change. Then on the frontend, `gameStatus` needs to distinguish "completed-won" from "completed-lost", and `MatchView` needs a second overlay branch (the 🟥 emoji rows in the share grid already support sharing a loss).
- **Verify**: Play a game into a bust-out (use a low target in Free Play); confirm a terminal screen appears. Refresh mid-completed-game and confirm the right overlay shows.
- **Files**: `frontend-react/src/components/game/match/MatchView.tsx:335–381`, `frontend-react/src/hooks/useGameLoop.ts:190–196,390–393`, backend: `GameService.java`, `GameStateMachine.java`
- **Learning notes**: This is an API-contract gap surfacing as a UI bug — a great interview story about why response DTOs should model *all* terminal states, not just the happy one. The React lesson: deriving `isWin` from a status enum collapses two distinct states into one boolean; booleans in props are often a smell when the domain has 3+ states.

### UX: Every throw forces a 2–4.5 second un-skippable animation
- **Severity**: 3 — Flexibility and Efficiency / User Control. Up to ~45s of forced waiting per game, every game, hitting daily players hardest.
- **Area**: Frontend-React (requestAnimationFrame lifecycle, escape hatches)
- **What**: `AnimatedScorePopup` counts up for `2000 + Math.random() * 2000` ms plus hold phases, with input disabled throughout and no way to skip. Suspense is great the first five times; after that it's friction for your most loyal players.
- **Direction**: The component already has a fast path — look at how it handles `prefers-reduced-motion` (it jumps straight to the final phase). A click/Enter skip is the same idea triggered by an event instead of a media query: cancel the animation frame, set the display to the target, advance the phase. Think about which element should receive the click and what happens if the user skips during the BUST flash.
- **Verify**: Throw a dart, click the popup mid-count — it should jump to the result immediately and dismiss on schedule.
- **Files**: `frontend-react/src/components/game/AnimatedScorePopup.tsx:35–90`
- **Learning notes**: `requestAnimationFrame` + cleanup in `useEffect`'s return function is the React idiom for cancellable background work — conceptually like cancelling a `Future`. Interview angle: "celebration animations need a skip affordance" is a known game-UX pattern; reduced-motion support proving the short path works is a nice defence.

### UX: Enter-key collision can throw the wrong dart
- **Severity**: 3 — Error Prevention. One keypress performs two actions, one of them irreversible.
- **Area**: Frontend-React (event bubbling, stale closures — the best pure-React lesson in this list)
- **What**: `MatchView` listens for Enter on the wrapper div to fire `handleThrowDart()`; `EntitySearch` handles Enter to select a suggestion and calls `preventDefault()` but **not** `stopPropagation()`. Keydown events bubble. If an answer is already staged and the player presses Enter to select a *different* suggestion, the child stages the new name **and** the bubbled event reaches the parent — which throws the dart using the **old** staged value still captured in that render's closure.
- **Direction**: Two concepts to understand before touching code: (1) `preventDefault` vs `stopPropagation` — they do completely different things; (2) why the parent handler sees the *old* `staged` value (what does a closure capture in a function component render?). The fix is one well-placed line, but be able to explain *why* it's needed and why the "Enter again on an empty input throws the staged dart" flow must keep working.
- **Verify**: Stage an answer, type a new name, press Enter on a suggestion — the old answer must NOT be thrown. Then: stage an answer, input empty, press Enter — the dart SHOULD throw (intended fast path).
- **Files**: `frontend-react/src/components/game/EntitySearch.tsx:102–122`, `frontend-react/src/components/game/match/MatchView.tsx:113–117`
- **Learning notes**: Event bubbling is the DOM's version of an exception propagating up the call stack until something catches it. Stale closures are *the* React gotcha for backend developers — state reads inside handlers are snapshots from the render that created the handler, not live references. If an interviewer asks "what's tricky about React state?", this bug is a concrete, war-story answer.

### UX: Daily challenges silently disappear from the lobby when the API fails
- **Severity**: 3 — Error Recovery. The product's core feature vanishes without a trace on a failed fetch.
- **Area**: Frontend-React (error propagation through hooks and props)
- **What**: `useDailyChallenge` exposes `error` and `refresh()`, but `page.tsx` only passes `challenges` and `loading` down to `LobbyView`, whose render condition (`!dailyLoading && dailyChallenges.length > 0`) hides the entire section. A player arriving from a share link on flaky wifi sees no dailies, assumes there's no challenge today, and leaves — and nobody ever files a bug because nothing looks broken.
- **Direction**: Trace the data path: hook → page → `LobbyView` props. Two values aren't making the journey. Then decide what the section renders in the error case — the section header with a short message and a retry button beats hiding it. The hook's `refresh()` already exists, unused.
- **Verify**: Block `/api/daily-challenge/status` in DevTools (Network → block request URL), reload — the lobby should show the dailies section with an error message and a working Retry.
- **Files**: `frontend-react/src/app/page.tsx:72`, `frontend-react/src/components/game/lobby/LobbyView.tsx:59–64,198`, `frontend-react/src/hooks/useDailyChallenge.ts:51`
- **Learning notes**: Custom hooks are the React analogue of an injected service — and this is a textbook "service returns a Result, caller discards the error field" bug, identical to swallowing an exception in a controller. Related: P1 item "Loading, error, and empty state consistency".

### UX: Autocomplete suggestions are invisible to screen readers
- **Severity**: 3 — Accessibility. The core game interaction has working keyboard support but zero announced semantics.
- **Area**: Frontend-React (ARIA combobox pattern)
- **What**: `EntitySearch`'s input has no `role="combobox"`, `aria-expanded`, `aria-controls`, or `aria-activedescendant`; the suggestion list isn't a `listbox`; the "Keep typing…" / "Loading…" / "No match" messages have no live region. A screen-reader user types and hears nothing — the game is unplayable non-visually even though the keyboard plumbing is 90% done.
- **Direction**: Read the WAI-ARIA Authoring Practices "Combobox with Listbox Popup" pattern first — it specifies exactly which attribute goes on which element and how `aria-activedescendant` tracks the highlighted option (the component's `activeIndex` state maps to it directly). The status messages want `role="status"` or a polite live region. This is attribute work, not logic work — the state you need already exists.
- **Verify**: Use macOS VoiceOver (Cmd+F5): typing 4+ chars should announce suggestions appearing; arrow keys should announce each option; "No match" should be spoken.
- **Files**: `frontend-react/src/components/game/EntitySearch.tsx:139–196`
- **Learning notes**: ARIA roles/states are a published interface contract, like a Swagger spec for assistive tech — implementing a pattern from the authoring practices doc is the accepted approach, not invention. Strong interview material: most candidates can say "accessibility matters"; few can name the combobox pattern and `aria-activedescendant`.

### UX: Share button says "Copied!" before anything is copied
- **Severity**: 2 — Visibility of System Status. The label is backwards: it reads "Copied!" *during* the async work and reverts to "Share result" the moment the copy actually succeeds.
- **Area**: Frontend-React (async state phases)
- **What**: `MatchView` renders `{sharing ? "Copied!" : "Share result"}`, and `sharing` is true while the fetch + clipboard write are in flight, reset in `finally`. Users glance at the button at the exact moment they're about to paste into a group chat and can't tell if it worked.
- **Direction**: Two states aren't enough to model three phases (idle → working → succeeded). Where does the third one come from, who sets it, and how does it return to idle? (A timeout is acceptable; check how `ToastItem` schedules its own dismissal for a pattern.)
- **Verify**: Click Share — button should read "Sharing…" then hold a visible "Copied ✓" for a beat before returning to normal.
- **Files**: `frontend-react/src/components/game/match/MatchView.tsx:358–366`, state set in `frontend-react/src/app/page.tsx:101–126`
- **Learning notes**: Modelling async UI as a tiny state machine (idle/pending/success/error) instead of booleans is the same instinct as modelling order status as an enum instead of `isPaid`/`isShipped` flags.

### UX: Winning, then exiting, warns "your progress will be lost"
- **Severity**: 2 — Match Between System and Real World. A danger-styled confirmation fires after the game is already complete.
- **Area**: Frontend-React (conditional behaviour)
- **What**: The win overlay's "Exit to lobby" button opens the same mid-game exit confirm ("Your progress in this game will be lost") used by the header Exit button. There is no progress to lose; some players will cancel out, unsure if exiting voids their result.
- **Direction**: The component already knows whether the game is won. One of the two exit paths shouldn't ask.
- **Verify**: Win a game, click "Exit to lobby" — straight to the lobby, no dialog. Mid-game Exit must still confirm.
- **Files**: `frontend-react/src/components/game/match/MatchView.tsx:373–378,384–393`
- **Learning notes**: Smallest item in the set — good warm-up. The lesson is about reuse going one step too far: shared components are good; sharing *behaviour* across contexts with different stakes is not.

### UX: Browser back button doesn't navigate the lobby drill-down
- **Severity**: 2 — User Control / platform conventions. Back exits the site instead of popping Football → League → Club.
- **Area**: Frontend-React (History API integration with component state)
- **What**: The drill-down nav is a pure `useState` stack with no browser-history integration. Mobile users three levels deep swipe back and are dumped out of the app.
- **Direction**: The browser History API (`history.pushState` + the `popstate` event) lets you mirror your in-memory stack into session history. Each `push` of a screen should create a history entry; a `popstate` should call your existing `pop`. Subscribe/unsubscribe with `useEffect` (event listener cleanup is the part to get right). An alternative is encoding the screen in a search param — weigh the trade-off.
- **Verify**: Drill into Football → Premier League, press browser back — you should land on Football, not leave the site.
- **Files**: `frontend-react/src/components/game/lobby/LobbyView.tsx:78–95`
- **Learning notes**: This is client-side state vs addressable state — the SPA equivalent of "should this be in the URL?" Interview angle: knowing *when* component state should sync with browser history (navigation-like state: yes; ephemeral UI state: no).

### UX: No indication of which daily challenges you've already played
- **Severity**: 2 — Recognition Over Recall. Cards look identical before and after your attempt; returning players must remember what they did this morning.
- **Area**: Frontend-React (localStorage persistence, derived rendering)
- **What**: Neither the lobby cards nor `/daily` show a played state. Replay is trust-based by design, but the UI doesn't even *inform*. Wordle's greyed tile + "see result" solved this years ago.
- **Direction**: No backend change needed for v1: when a daily completes (where in `useGameLoop` do you know that?), record category+date locally — look at how the theme (`t501-theme`) and game session (`activeGameState`) are persisted for the two storage flavours and pick the right one (which survives a browser restart?). Cards then derive a played state. Date handling matters: "today" must roll over at the same boundary the backend uses (midnight UTC).
- **Verify**: Complete a daily, return to the lobby — that card should show a played state and not re-enter the game. Next day it should reset.
- **Files**: `frontend-react/src/components/game/lobby/LobbyView.tsx:207–230`, `frontend-react/src/app/daily/page.tsx:90–117`, `frontend-react/src/hooks/useGameLoop.ts:390–393`
- **Learning notes**: localStorage vs sessionStorage is scope/lifetime — like session-scoped vs application-scoped beans. Also a nice "ship the cheap client-side version, note the server-backed upgrade path" product judgement story.

### UX: Four-character minimum blocks short player names ("Son")
- **Severity**: 2 — Tolerance and Forgiveness. Typing "son" shows "Keep typing for suggestions…" forever; reads as "the game doesn't have Son".
- **Area**: Frontend-React (understanding why a constraint exists before changing it)
- **What**: `EntitySearch` requires 4+ characters before searching. Football is full of ≤3-char names (Son, Ba, Kun); Film/Geography add "Up", "Rio".
- **Direction**: First understand why 4 exists: the threshold predates the client-side entity cache — when every keystroke was an API call, it was rate-limit protection. Check `entityCache.ts`: searches are now in-memory, so what does the threshold still protect? (Hint: result-list size at 1–2 chars.) Then decide the new number and check every place that hardcodes the old one — the hint messages and the Enter handler also compare against 4.
- **Verify**: Type "son" — suggestions should appear, including Heung-min Son.
- **Files**: `frontend-react/src/components/game/EntitySearch.tsx:81,109,155–171`, `frontend-react/src/lib/api/entityCache.ts`
- **Learning notes**: Classic "constraint outlives its rationale" — like a connection-pool cap tuned for hardware you decommissioned. Interview angle: always state *why* a magic number exists before defending or changing it.

### UX: Functional labels set in 9–10px fixed-pixel type
- **Severity**: 2 — Accessibility. The `.kicker` micro-label style carries functional content (hint labels, "Sign out", the one-attempt warning) at 9–10px in `px` units, which ignore browser font-size preferences.
- **Area**: Frontend-CSS (relative units, type hierarchy)
- **What**: The uppercase-mono kicker is a deliberate editorial style — fine for decoration, wrong for the labels explaining the game's most strategic info (checkouts remaining) and for action links.
- **Direction**: Separate the *style* from the *size*: which kicker instances are decorative and which are functional? Functional ones want ≥11–12px equivalents in `rem`. Understand why `text-[10px]` defeats a user's browser font-size setting but `rem` doesn't. The 9px instances (`DAILY`, `PLAY NOW` badges) are the worst offenders.
- **Verify**: Set browser default font size to 20px — functional labels should grow; check both themes for contrast at the new sizes.
- **Files**: `frontend-react/src/app/globals.css:115–147` (`.kicker`, `.hint`), 9px instances in `LobbyView.tsx:216,225`, `daily/page.tsx:100`
- **Learning notes**: `px` vs `rem` is the CSS lesson every backend dev should be able to articulate: `rem` scales with the user's root font size; `px` doesn't. WCAG 1.4.4 (resize text to 200%) is the requirement to cite.

### UX: Three different brand names — TRIVIA 501, FOOTBALL 501, F501
- **Severity**: 2 — Consistency. The share text (the growth loop!) says "⚽ FOOTBALL 501", the app says "TRIVIA 501", the PWA manifest says "F501", and the share-page footer prints "trivia501.com" which isn't the deployed domain.
- **Area**: Frontend (cross-file consistency; **this one has tests** — share-grid encoding has 21 of them)
- **What**: A friend receives "FOOTBALL 501", taps through to "TRIVIA 501", and wonders if they're in the right place. The football emoji also appears on Film/Geography shares.
- **Direction**: `buildShareText` is a pure function with exhaustive tests — change behaviour *and* tests together (run `npm test` before and after; watch them fail for the right reason first). Consider deriving the emoji from the category. Then sweep the other two spots: manifest `short_name`, and the footer label in `daily/[category]/page.tsx` (the href is fine; the visible text lies).
- **Verify**: `npm test` green; share a Geography daily and read the actual clipboard text.
- **Files**: `frontend-react/src/utils/share.ts:49`, share tests under `__tests__/`, `frontend-react/public/manifest.json`, `frontend-react/src/app/daily/[category]/page.tsx:131–136`
- **Learning notes**: Your first test-driven frontend change — the red/green workflow is identical to JUnit. Pure functions extracted from components (this one was pulled out of `page.tsx` precisely to be testable) are the React analogue of extracting logic from controllers into services.

### UX: ErrorBoundary ignores the design system and shows raw error messages
- **Severity**: 2 — Consistency + Error Recovery. Hardcoded `bg-gray-950`/`text-red-500` palette breaks light mode, and `error.message` (developer text) is shown to players.
- **Area**: Frontend-CSS (design tokens) + React (class components — the only one in the codebase)
- **What**: In light mode an error drops a near-black panel into a cream page — the moment things break is when the UI looks most broken. The raw message means nothing to users (it's already in `console.error` for you).
- **Direction**: Every other component uses semantic tokens (`bg-bg`, `text-ink`, `text-danger` — see `globals.css` for the vocabulary). Swap the grays for tokens and replace the message with friendly copy. While you're in the file, note *why* this is a class component — error boundaries are the one thing function components still can't do.
- **Verify**: Temporarily `throw new Error("boom")` inside `LobbyView`, check the boundary renders correctly in **both** themes, then remove the throw.
- **Files**: `frontend-react/src/components/ErrorBoundary.tsx:43–64`, token vocabulary in `frontend-react/src/app/globals.css:13–85`
- **Learning notes**: Error boundaries are React's `@ControllerAdvice` — a declarative catch-all for render-time exceptions, and the canonical answer to "when do you still need a class component?"

### UX: Raw server error strings surfaced in toasts
- **Severity**: 2 — Error Recovery / Match with Real World. Failure paths toast `parsed.error || parsed.message || text` verbatim — whatever the backend or a proxy emits.
- **Area**: Cross-stack (error message contract)
- **What**: Players see messages written for developers ("Validation failed", raw 403 bodies) with no guidance. Four near-identical parse-and-toast blocks exist in `useGameLoop` plus copies in the daily pages — itself a duplication smell.
- **Direction**: Two-part exercise. (1) Extract the repeated "parse error body" logic into one helper (where do shared frontend utilities live in this repo?). (2) Decide the trust policy: which backend messages are user-safe? You control the backend — `GlobalExceptionHandler` defines the error shape, so you can mark/whitelist user-facing messages there and have the frontend default to a friendly fallback for everything else. That's a nicer contract than frontend-side guessing.
- **Verify**: Stop the backend, try starting a game — the toast should be human ("Couldn't start the game — check your connection"), not a dump.
- **Files**: `frontend-react/src/hooks/useGameLoop.ts:245–254,290–299,334–343`, `frontend-react/src/app/daily/page.tsx:22–26`, `frontend-react/src/app/daily/[category]/page.tsx:53–57`, backend: `GlobalExceptionHandler.java`
- **Learning notes**: You own both sides of this contract — rare in tutorials, common in jobs. Interview angle: error-message taxonomy (user-actionable vs developer-diagnostic) and why the boundary belongs in the API contract, not in frontend string-matching.

### UX: /daily error state offers no retry
- **Severity**: 2 — Error Recovery. One line of red text, dead end — while the hook's `refresh()` sits unused.
- **Area**: Frontend-React (smallest hook-wiring exercise in the set)
- **What**: On fetch failure `/daily` renders the error string and nothing else. The retry function already exists in `useDailyChallenge`.
- **Direction**: Destructure one more value from the hook, render one button. Good first item if you want a confidence builder before the bigger ones.
- **Verify**: Block the status endpoint in DevTools, load `/daily`, unblock, click Try again — challenges appear without a full page reload.
- **Files**: `frontend-react/src/app/daily/page.tsx:79–81`, `frontend-react/src/hooks/useDailyChallenge.ts:51`
- **Learning notes**: Pairs with the lobby silent-failure item — same root cause (hook capabilities ignored by the consumer), two different presentations.

### UX: Win overlay is not a dialog — background stays keyboard-reachable
- **Severity**: 2 — Accessibility. No `role="dialog"`, no focus management; Tab moves "through" the celebration into the controls behind it.
- **Area**: Frontend-React (custom hook reuse, focus management)
- **What**: Keyboard users tab into the invisible match UI behind the overlay; screen readers never hear that the game ended.
- **Direction**: Everything you need exists: `useFocusTrap` (read it — note what it does on mount, on Tab, on Escape, and on cleanup) is already used by `ConfirmDialog`. The win overlay needs the same treatment: a ref, the hook, and the dialog ARIA attributes. Think about whether Escape should do anything here (the overlay has no cancel concept).
- **Verify**: Win a game, press Tab repeatedly — focus must cycle within the overlay's three buttons only.
- **Files**: `frontend-react/src/components/game/match/MatchView.tsx:335–381`, `frontend-react/src/hooks/useFocusTrap.ts`, usage reference: `frontend-react/src/components/ui/ConfirmDialog.tsx:27–28`
- **Learning notes**: Custom hooks as reusable behaviour (not just data-fetching) — the closest React gets to an aspect/interceptor. Reading `useFocusTrap` end-to-end is worth it: refs, effect cleanup, and DOM querying in ~60 lines.

### UX: Identical lobby rows either drill down or instantly start a game
- **Severity**: 2 — Affordances. Clicking "Football" opens a submenu; clicking the identically-styled "Geography" row immediately starts a game. The only differentiator is a `↵` vs `→` glyph nobody will decode.
- **Area**: Frontend-React (props-driven variants) + design judgement
- **What**: Players tap "Film" expecting to browse and are abruptly in a game with whatever target score happened to be selected.
- **Direction**: `NavRow` already takes `hasChildren` — the information needed to differentiate is in the props; the rendering just doesn't use it strongly enough. The daily cards' "PLAY NOW" tag is an existing visual vocabulary for "this commits you". Design question before code: should commit rows look different, or should those categories get a one-screen confirm like football's drill-down? (This interacts with the daily-confirmation item — aim for one consistent "point of no return" pattern.)
- **Verify**: Show the lobby to someone who hasn't seen it; ask which rows start a game immediately. They should get it right.
- **Files**: `frontend-react/src/components/game/lobby/LobbyView.tsx:298–325,507–546`
- **Learning notes**: Affordances (Norman): controls with different consequences must look different. Component-API angle: a `variant` prop is more honest than overloading the presence of `sub`/glyph props to imply behaviour.

### UX: Theme-aware dialogs render mixed themes inside the pinned-dark admin
- **Severity**: 2 — Consistency. In light mode, `ConfirmDialog` (player tokens, theme-following) renders cream-on-charcoal inside the dark-pinned admin — right before destructive actions like "Delete question".
- **Area**: Frontend-CSS (CSS custom property cascade and scoping)
- **What**: `admin/layout.tsx` re-pins `--color-surface` (the *admin* token) but `ConfirmDialog` consumes the *player* tokens (`--surface`, `--ink`), which still follow `[data-theme]` on `<html>`.
- **Direction**: Understand the cascade first: where is each token defined, what does `[data-theme="light"]` override, and what would happen if the admin root carried its own `data-theme` attribute? (That's the cheapest correct fix, given the existing "Admin pages opted out of the unified design system" item plans the real convergence.) Trace why the inline `--color-surface` pin doesn't help the dialog.
- **Verify**: Light mode → admin → trigger a delete confirm — the dialog should match the admin's dark chrome.
- **Files**: `frontend-react/src/app/admin/layout.tsx:14–20`, `frontend-react/src/components/ui/ConfirmDialog.tsx`, token definitions in `frontend-react/src/app/globals.css:13–52`
- **Learning notes**: CSS custom properties cascade like scoped configuration — a child scope can re-pin a value for its subtree, which is exactly what `data-theme` on a subtree root does. Related backlog item: "Admin pages opted out of the unified design system".

### UX: Tiny touch targets on text-link actions
- **Severity**: 2 — Affordances (mobile). "Sign out", "Admin", "Exit to lobby", and the staged-answer ✕ are 10px-text links well under the 44×44px touch minimum.
- **Area**: Frontend-CSS (hit areas vs visual size)
- **What**: Mobile players mis-tap or peck — worst on "Exit to lobby" inside the win overlay.
- **Direction**: The visual size can stay; the *hit area* can't. Padding extends the target without changing the look (negative margin can compensate for layout shift if needed). Audit each instance against ~44px.
- **Verify**: DevTools mobile emulation — tap each link with the touch cursor; no precision needed.
- **Files**: `frontend-react/src/components/auth/LoginButton.tsx:39`, `frontend-react/src/components/game/lobby/LobbyView.tsx:151–154`, `frontend-react/src/components/game/match/MatchView.tsx:245–251,373–378`
- **Learning notes**: Apple HIG and WCAG 2.5.8 both specify minimum target sizes — citable standards, not taste.

### UX: Cosmetic batch — four small finishes
- **Severity**: 1 — each is a one-or-two-line change; do them as a single sweep.
- **Area**: Frontend (mixed)
- **What & Direction**:
  1. **Autocomplete `aria-label` hardcodes "Search player name"** for every category — derive from the `entityType` prop. (`EntitySearch.tsx:152`)
  2. **Manifest `theme_color` is stale** (`#18171a` vs the actual `--bg` `#0d0f13`) — installed-PWA chrome is subtly off. (`public/manifest.json`)
  3. **Daily cards pop in after load** with no reserved space, shifting layout — render fixed-size skeleton placeholders while `dailyLoading`. (`LobbyView.tsx:198`)
  4. **Mixed icon languages** — emoji in the admin sidebar vs stroke SVGs in the player UI; align when admin is next touched (don't do it standalone). (`components/admin/Sidebar.tsx:8–12`)
- **Verify**: Visual check per item; for #3, throttle the network and watch the lobby not jump.
- **Learning notes**: #3 introduces skeleton screens / layout-shift (CLS) — worth knowing the vocabulary even at severity 1.

---

## P1 — Shortly After Launch

These don't block the first players but should follow quickly.

### Security: Re-enable CSRF protection
- **What**: CSRF is currently disabled for stateless REST. Re-enable with SameSite cookies when JWT cookies are introduced.
- **Why deferred**: Not applicable until HTTPOnly cookie auth is in place.
- **See**: `docs/SECURITY_ARCHITECTURE.md`.

### Security: Content Security Policy
- **What**: Add a strict CSP in Next.js `middleware.ts`.
- **Why deferred**: Low risk during development; important before public launch.
- **See**: `docs/SECURITY_ARCHITECTURE.md`.

### Data: Remove legacy `difficulty INTEGER` field
- **What**: Drop the old 1/2/3 difficulty column once all callers of `findByCategoryIdAndDifficultyAndStatus` are migrated to the new range-based query (`difficulty_score NUMERIC`).
- **Why deferred**: Needs a dedicated cleanup migration; low risk to leave in place short-term.
- **See**: `docs/design/DIFFICULTY_SCORING.md` — Remaining / Future Work.

### Match history
- **What**: Players can't see past games or results after the match ends. A personal game log showing past daily challenges (date, category, score, emoji grid), Free Play games (category, question, final score), and personal bests is expected from any game that asks people to come back daily.
- **Why deferred**: Player profiles need to land first so history has a place to live. The `matches` and `games` tables already exist; a read endpoint and simple history page are the main work.
- **See**: New endpoint + page; `matches` and `games` tables, `player_profiles` table (V23).

### Server-side turn timer
- **What**: The 45s→30s→15s→forfeit timer ladder is displayed client-side but not enforced server-side. For solo play this is a UX feature (adds pressure/drama) rather than a competitive integrity concern. The timer should eventually be enforced server-side so the client display is authoritative, and so Free Play optionally supports timed games for players who want the pressure.
- **Why deferred**: Not a launch blocker without multiplayer. The client timer display works; server enforcement is a polish pass.
- **See**: `GameStateMachine.java`, `GameService.java`.

### Loading, error, and empty state consistency
- **What**: Around 60 references to loading states exist across components but there's no consistent pattern (some use inline conditionals, some use a `LoadingOverlay`, some don't handle loading at all). Certain flows lack error handling for API failures; others don't handle the empty/zero-results case (no autocomplete matches, no leagues found, no daily challenge available, no search results on admin pages). Every user-facing component must handle all three non-happy-path states.
- **Why deferred**: Individual feature work is still adding new components; standardising patterns now would create churn. Better to audit and fix once the component landscape stabilises.
- **See**: Audit `frontend-react/src/components/` for missing `isLoading`, `error`, and empty-state branches. Reference: `LoadingOverlay.tsx`.

### Daily challenge `/status` endpoint does not trigger lazy creation

- **What**: `GET /api/daily-challenge/status` is a plain DB read (`findByChallengeDate(today)`). Lazy creation only fires when something calls `GET /api/daily-challenge/{categorySlug}` — which the `/daily` page never does. If the midnight cron misses (server restart, Fly.io deployment after midnight, dev machine off), the status endpoint returns `{"challenges":[]}` and the page shows "No challenges today" even though lazy creation could produce valid challenges for every category.
- **Why deferred**: The cron is reliable in production under normal operation. Fly.io deployments happen infrequently and usually not in the midnight UTC window. In dev the workaround is to hit `GET /api/daily-challenge/{slug}` directly to trigger lazy creation, or restart the backend before midnight.
- **Fix options** (pick one): (1) Make `GET /status` call `getTodaysChallenge(categoryId)` for each eligible category before assembling the response — lazy creation fires as a side-effect. (2) Change the frontend `useDailyChallenge` hook to call per-category endpoints in parallel instead of `/status`. (3) Add a Spring `ApplicationReadyEvent` listener that runs `selectDailyChallenges()` if no challenges exist for today (idempotent — the scheduler's skip-if-exists guard makes this safe to call on every startup).
- **See**: `DailyChallengeController.java:68–90`, `DailyChallengeService.java:54–56,63–67`, `useDailyChallenge.ts:31–44`.

### Open Graph metadata for daily challenge shares
- **What**: Emoji-grid sharing exists and works end-to-end, but shared URLs render as raw links with no title, description, or image preview in messaging apps. The Wordle comparison sets a high bar — shared results should show a rich card with the category name, final score, and emoji grid. This is a small static metadata change with outsized social reach.
- **Why deferred**: Share mechanics work; OG metadata is a polish layer that can be added without touching game logic.
- **See**: `frontend-react/src/app/daily/[category]/page.tsx`, `frontend-react/src/app/layout.tsx` (add `<meta>` tags).

### PWA offline capability validation
- **What**: The manifest and service worker files exist but haven't been tested on spotty/missing connections. The PRD promises PWA installability and sub-3s load on 3G. The game can't work fully offline (server-side validation), but the app shell, lobby, and daily challenge browse pages should cache and render without network.
- **Why deferred**: Core game mechanics are still being built; PWA optimisation matters once the shell is stable.
- **See**: `frontend-react/public/manifest.json`, `frontend-react/public/sw.js`, `frontend-react/next.config.ts`.

### Difficulty score recalibration after data population
- **What**: Difficulty scores were backfilled from existing answer counts. Once the scraper populates more answers, scores shift — some "hard" questions become easier, some "easy" questions show their true range. Re-run the backfill after a full scraper population pass, then review daily challenge question quality with real scores.
- **Why deferred**: Depends on data population (P0) being done first.
- **See**: `DifficultyCalculator.java`, `backfill_difficulty_scores.sql`.

---

## Backend Hardening

Stability and correctness improvements that are not urgently needed but should be done before the game sees meaningful load.

### Duplicate answer protection in `game_moves`
- **What**: Two guards against a player submitting the same answer twice (e.g. via double-tap or a network retry racing with the first request):
  1. **Unique constraint** on `(game_id, matched_answer_id)` in `game_moves` — DB rejects the second insert regardless of application logic.
  2. **`@Version` optimistic locking** on the `Game` entity — a version column that increments on every save; a concurrent second transaction throws `OptimisticLockException`, which `GlobalExceptionHandler` can return as a clean error.
- **Why deferred**: Design is still evolving; don't want an unnecessary Flyway migration that needs reversing. Current `@Transactional` wrapper prevents partial saves, so this is a hardening step, not a correctness emergency.
- **When to pick up**: Once the game schema is stable. Both changes together close the race condition and the duplicate submission gap.
- **Files**: `GameService.java:77`, `Game.java`, `GameMove.java`, `GlobalExceptionHandler.java`.

### Toggle on/off and customise turn timer
- **What**: Allow players to disable the turn timer entirely or set a custom duration before starting a Free Play game. The default 45s→30s→15s→forfeit ladder would remain the default for timed games.
- **Why deferred**: Requires server-side timer enforcement (P1) to be complete first. Customisation only makes sense once the baseline timer is solid.
- **When to pick up**: After the server-side turn timer P1 item is complete. Timer settings would live on the `matches` row (e.g. `timer_enabled BOOLEAN`, `turn_timer_override_seconds INTEGER`) so `GameStateMachine` can read them without changing its interface.
- **Files**: `GameStateMachine.java`, `Game.java`, `GameService.java`.

### Solo forfeit does not complete the match
- **Status**: Promoted to P0 launch blocker (see above). This item remains here as the original analysis with full context.
- **Files**: `MatchService.java:141`, `GameStateMachine.java:138`, `GameService.java:139`.

### Solo vs multiplayer: null-player2 design tension
- **What**: Solo (Free Play) games reuse the two-player `Match`/`Game` models with `player2Id = null`. This works but creates scattered `isSolo` branching in `GameStateMachine` (4+ sites) and `GameService` (2 sites). Fields like `player2Score`, `player2GamesWon`, and `MatchFormat.BEST_OF_1` are meaningless in solo context. With multiplayer deferred indefinitely, the null-player2 approach carries dead weight — but ripping it out would be a large refactor for no user-facing gain.
- **Verdict**: Keep the current null-based approach. The modes share ~80% of their logic and there's no third mode on the horizon. If the branching becomes a maintenance tax during Free Play work, extract it into a `TurnPolicy` strategy (SoloPolicy / TwoPlayerPolicy) that lives in one place.
- **Files**: `GameStateMachine.java:67,123,253`, `GameService.java:209,284`, `MatchService.java:78,148`.

### Per-turn used-answer query grows with game length
- **What**: `gameMoveRepository.findUsedAnswerIdsByGameId(gameId)` (called on every turn at `GameService.java:87`) fetches all used answer IDs from the start of the game. As a game progresses this set grows. Under load this is the query most likely to degrade first.
- **Why deferred**: Not a problem at current scale. The set is bounded by the number of valid answers for the question (~20–100 rows).
- **When to pick up**: If profiling shows this query becoming a bottleneck, consider caching the set in a `ConcurrentHashMap` keyed by `gameId` for the lifetime of the game (invalidated on game completion).

---

## Architectural Guardrails

Small, cheap decisions to make in the core game now that keep future modes open. These are not stretch goals — they are hygiene. Reference `docs/design/GAME_MODES_STRETCH_GOALS.md` when making schema or service boundary decisions.

With multiplayer deferred indefinitely, guardrails that only serve multiplayer modes are parked (see Deferred Indefinitely section above). Guardrails that serve Daily Challenge and Free Play remain active.

### Database

| Guardrail | Status | Why it matters |
|---|---|---|
| ~~Add `game_mode VARCHAR` to `matches`, default `'STANDARD'`~~ | ✅ Done (V19) | Every non-standard mode needs this column. Added in V19 with Daily Challenge support. |
| Store `question_id` on `game_moves` (not just on `games`) | 🅿️ Parked | Rapid Fire needs per-move question tracking. Parked with multiplayer. |
| ~~Add `suitable_for_daily BOOLEAN DEFAULT false` to `questions`~~ | ✅ Done (V19) | Explicit Daily Challenge pool flag. Added in V19; V20 backfills viable easy/medium questions. |
| Add `suitable_for_game_modes JSONB` to `question_templates` | 🅿️ Parked | Prevents unsuitable templates being re-enabled for wrong modes in future. Parked with multiplayer. |

### Backend

| Guardrail | Status | Why it matters |
|---|---|---|
| Question draw logic in a dedicated service (`QuestionDrawService`) | 🅿️ Parked | Rapid Fire swaps "draw once per game" for "draw once per turn". Parked with multiplayer. |
| Pass `gameMode` through game engine context from the start | Active | Already in place. Mode-specific rules for Daily/Free Play benefit from this. |

### Frontend

| Guardrail | Status | Why it matters |
|---|---|---|
| Mode label/badge component | Active | Gives the UI a place to display "Daily Challenge" vs "Free Play" mode names in listings and history. |

---

## P2 — Stretch Goals

Features that are designed but not being built until the core Daily Challenge + Free Play experience is stable and has real users.

### Async friend challenges
- **What**: Play the same daily challenge question as a friend and compare results. Not real-time — each player plays independently, then sees the other's result when both have finished. The social mechanic is: "I played today's Football daily, scored 87. Can you beat that?" Friend challenges would be surfaced via share links that deep-link into the challenge. If the recipient plays within 24 hours, the results are compared side-by-side with the same emoji-grid format.
- **Why deferred**: Requires a friend/challenge invitation system, result comparison UI, and push notification infrastructure. Build the single-player daily loop first, get retention data, then layer in social comparison.
- **See**: Daily challenge share links, `DailyChallengeController.java`.

### Expert Challenge
- **What**: Questions with `difficulty_score > 8.5` excluded from the standard daily pool. A separate "Expert Challenge" mode surfaces these for players who want the hardest questions.
- **Why deferred**: The expert question pool doesn't exist yet (needs data population + difficulty recalibration). The standard daily pool must be proven to work at normal difficulty before adding a hard mode.
- **See**: `DifficultyCalculator.java`, `DailyChallengeService.java`.

### Custom game rules (per-game and per-user preferences)
- **What**: Two complementary features:
  1. **Per-game rule overrides** — players choose a rule preset from the Free Play lobby. Rules stored as JSONB on the `games` row.
  2. **Per-user default preferences** — a `player_preferences` table storing preferred rule settings, auto-populated into the lobby selector.
- **Proposed rule knobs**:
  | Setting | Default | Description |
  |---|---|---|
  | Darts validation | Strict | Strict: 169, 176, etc. are invalid 3-dart checkouts → bust. Relaxed: only incorrect answers bust; any score 1–180 is valid. |
  | Checkout zone | -10 to 0 | Widens the win window. Options: -5 ("tight"), -10 ("standard"), -20 ("forgiving"). |
  | Max darts score | 180 | Could be raised (e.g. 200) for easier games. |
  | Turn timer | 45s | Custom duration or disabled entirely. |
- **Why deferred**: The core game rules are still being validated. Adding rule configurability while the base rules are in flux creates a test-matrix explosion. Defer until the strict-rules game loop has seen real play and we have feedback on whether the strict darts rules are causing confusion.
- **Files**: `DartsValidator.java`, `ScoringService.java`, `AnswerEvaluator.java:95-106`, `ScoreResult.java`, `GameHintsService.java:35`, `GameService.java:88`, `AdminAnswerService.java:51-52,157-162`, `QuestionMaterializerService.java:223-224`, `Match.java:75`, `Game.java`.

### Starting score variety for daily challenges
- **What**: Once the expanded curated pool of 20–30 starting scores is live (P0), consider making the score selection smarter — e.g. avoiding consecutive repeats within the same category, weighting toward under-used scores, or showing a "score of the day" reveal animation. The goal is making each day feel like a genuinely different puzzle even when the question category repeats.
- **Why deferred**: Get the expanded pool working first (P0), then iterate on the selection algorithm based on play data.
- **See**: `DailyChallengeScheduler.java`, `DailyChallengeService.java`.

### Custom game rules (per-match and per-user preferences)

- **What**: Two complementary features:
  1. **Per-game rule overrides** — players choose a rule preset from the lobby before starting a game. Rules are stored as JSONB on the `games` row and threaded through `ScoringService` + `DartsValidator` at runtime.
  2. **Per-user default preferences** — a `player_preferences` table storing the player's preferred rule settings, auto-populated into the lobby selector. A `GET/PUT /api/player/preferences` endpoint and a small frontend settings panel.
- **Proposed rule knobs**:
  | Setting | Default | Description |
  |---|---|---|
  | Darts validation | Strict | Strict: 169, 176, etc. are invalid 3-dart checkouts → bust. Relaxed: only incorrect answers bust; any score 1–180 is valid. |
  | Checkout zone | -10 to 0 | Widens the win window. Options: -5 ("tight"), -10 ("standard"), -20 ("forgiving"). |
  | Max darts score | 180 | Could be raised (e.g. 200) for easier games. |
  | Turn timer | 45s | Custom duration or disabled entirely (overlaps with Backend Hardening item above; decide which owns it). |
- **Architecture notes** (from 2026-06-08 exploration):
  - `DartsValidator.isValidDartsScore()` is `private static final` — would need a `GameRules`-taking overload.
  - `ScoringService` constants `CHECKOUT_MIN = -10` / `CHECKOUT_MAX = 0` are `private static final` — would read from `GameRules` instead.
  - `answers.isValidDarts` and `answers.isBust` are pre-computed at import time under strict rules. For relaxed-rules games, `AnswerEvaluator` must **ignore those DB flags** and recompute bust purely at runtime from `ScoringService`. This is the right place — `AnswerEvaluator` already orchestrates the scoring call.
  - `ScoreResult.bust()` was given a `reason` parameter in the 2026-06-08 reason-surfacing pass — the reason text naturally adapts to the active rules.
  - `GameHintsService` has a hardcoded `CHECKOUT_WINDOW = 10` that must become dynamic.
  - `game_mode` on `matches` is already a write-only column (set by `DailyChallengeService`, never read by game logic). Adding a `rules JSONB` column on `games` is cleaner than overloading `game_mode`.
  - There is also an existing `isBust` discrepancy: `AdminAnswerService` computes `isBust = !isValidDartsScore(score)` while `QuestionMaterializerService` uses `isBust = score > 180`. The SQL migrations align with the materializer. Adding custom rules provides a natural moment to resolve this — bake bust logic into one place (`ScoringService`) and stop persisting it on answers.
- **Why deferred**: The core game rules are still being validated. Adding rule configurability while the base rules are in flux creates a test-matrix explosion. Defer until (a) the strict-rules game loop has seen real play, and (b) at least one more game mode is implemented so the configuration surface is better understood.
- **When to pick up**: After Daily Challenge has real users and we have feedback on whether the strict darts rules are causing confusion. The 2026-06-08 reason-surfacing pass (showing why a move was rejected) will give us that signal — if players consistently need the reason explained, relaxed rules become higher priority.
- **Files**: `DartsValidator.java`, `ScoringService.java`, `AnswerEvaluator.java:95-106`, `ScoreResult.java`, `GameHintsService.java:35`, `GameService.java:88`, `AdminAnswerService.java:51-52,157-162`, `QuestionMaterializerService.java:223-224`, `Match.java:75`, `Game.java`.

### Go: Result Integrity Service (ed25519 signing for share links)

- **What**: A standalone Go microservice that cryptographically signs game results using `crypto/ed25519` from the Go standard library. When a player checks out, Java `GameService` calls `POST /sign` on this service with a payload of `{gameId, playerId, finalScore, completedAt}`; the service returns a signed base64 token. The token is stored on the `games` table and embedded in the share link and share data response. Anyone can verify a shared result is genuine via `POST /verify` (or independently, since the public key is exposed at `GET /pubkey`). The net effect: the emoji-grid share feature gains cryptographic authenticity — a shared result is either genuine or provably forged.
- **Why not just use a Java library**: The Java backend could sign tokens using `java.security`; splitting this into a Go service is a deliberate architectural choice for the portfolio. It demonstrates: (1) Go `crypto/ed25519` stdlib — no third-party crypto; (2) a stateless Go HTTP microservice; (3) table-driven tests; (4) Fly.io deployment alongside the existing backend. It is on-message for any security-focused employer. The Go service closing a real gap (share authenticity) gives it a defensible reason to exist beyond "we added Go for the sake of it."
- **The gap it closes**: The `GET /share/{gameId}` endpoint returns an emoji grid anyone can fabricate. Right now nothing distinguishes a real 3-move checkout from a claim invented in a text editor. The signing service makes share results verifiable — the same trust model as a signed JWT.
- **Go service endpoints**:
  ```
  POST /sign    { gameId, playerId, finalScore, completedAt } → { token: "<base64-encoded signed payload>" }
  POST /verify  { token: "<base64>" }                         → { valid: bool, payload: {...} }
  GET  /pubkey                                                → { publicKey: "<base64 DER>" }
  GET  /health                                                → 200 OK
  ```
- **Architecture**:
  - Service is **stateless** — no DB, no cache. Holds only a private key (Fly.io secret). Every instance can sign and verify; scaling is trivial.
  - Java calls it **once on checkout**, off the hot path. Not on the critical move-submission path, so latency from the Go service does not affect gameplay.
  - `GET /pubkey` exposes the public key so the Java backend (or any third party) can verify tokens independently without calling the service at all. Verification is free to scale.
  - **Key rotation**: generate a new keypair via the included `keygen` CLI (`go run keygen/keygen.go`), update the `SIGNING_KEY` Fly.io secret, redeploy. Tokens embed a `kid` (key-ID) so old tokens remain verifiable against the old public key during the overlap window.
- **Java integration points**:
  - `GameService.java` — on CHECKOUT transition (after `GameStateMachine.onMoveSubmitted` returns `CHECKOUT`), call `ResultSignerClient.sign(gameId, playerId, finalScore)` and persist the returned token to `games.result_token`. Failure to sign must not fail the checkout — log the error, leave `result_token` null, game still completes.
  - `DailyChallengeController.java` / `FreePlayController.java` — include `resultToken` in the `GET /share/{gameId}` response DTO so the frontend can embed it in share URLs and share text.
  - `ResultSignerClient` — a thin `RestTemplate` or `WebClient` wrapper; configure the Go service base URL via a `RESULT_SIGNER_URL` Fly.io secret. Mock the client interface in `GameService` unit tests so the checkout test suite does not require a running Go service.
  - **Schema**: add `result_token VARCHAR(512) NULL` to the `games` table (new Flyway migration, V37+). Null is valid — it means the game predates the signing service or the service was unavailable at checkout.
- **File structure** (new top-level directory `go-signer/`):
  ```
  go-signer/
  ├── main.go              # HTTP server, routes, env-var key loading
  ├── signer/
  │   ├── signer.go        # Sign / Verify / PublicKey using crypto/ed25519
  │   └── signer_test.go   # Table-driven: sign→verify roundtrip, tampered payload,
  │                        #   wrong key, expired token, missing fields
  ├── keygen/
  │   └── keygen.go        # CLI: generate ed25519 keypair → stdout (for Fly.io secrets)
  ├── fly.toml             # Fly.io deployment config, port 8090
  └── README.md            # Why this exists, how to generate keys, how to verify a token
  ```
- **Test coverage target**: table-driven tests in `signer_test.go` covering at minimum: sign→verify roundtrip (valid), tampered payload (invalid), token from a different key (invalid), token with a future `completedAt` (implementation choice: accept or reject), empty/missing fields (error). This is the idiomatic Go test pattern to demonstrate.
- **Resume bullet**: "Built a Go microservice that cryptographically signs game-result submissions (ed25519, Go stdlib), preventing fabricated share results; table-driven tests, deployed on Fly.io alongside the Java backend."
- **Why deferred**: Share link authenticity is a "nice to have" — the core game works without it. Building it requires deploying a new Fly.io service and wiring a new HTTP client into `GameService`. Pick up when the game has real users and share links have meaning worth verifying.
- **Dependencies**: Flyway migration for `result_token` column; `RESULT_SIGNER_URL` and `SIGNING_KEY` secrets on Fly.io; `ResultSignerClient` interface + implementation in the backend.
- **See**: `GameService.java`, `DailyChallengeController.java`, `FreePlayController.java`, `games` table, `go-signer/` (not yet created).

---

### Football: league-scope questions for "Random League Question" option
- **What**: All current football questions are `q_scope = 'club'` (tied to a specific team). A "league-scope" question would ask about the whole league (e.g. "Top scorers in the Premier League since 2000" — valid answers are any player from any club in that league). The lobby had a "Random League Question" option that fell back to club questions when none existed, causing confusion. The option has been replaced with a single "Random Question" entry that uses `scope: random_any` until league-scope questions are added.
- **Why deferred**: Requires a new question template (`football.competition_metric_since` without a `team_id` param), a new materializer variant that aggregates across all teams in a competition, and new DB migrations to seed these questions.
- **When to pick up**: After the core game loop is stable and the scraper service is running. The lobby entry in `FootballScreen` can be restored with `scope: "random_league_level"` once `findRandomFootballLeagueQuestion()` returns results.
- **Files**: `LobbyView.tsx` (FootballScreen), `QuestionService.java:206-209`, `QuestionRepository.java:278-285`, `FootballFilter.java`, `V24__add_football_question_template_columns.sql`.

---

## Admin & Data

### Difficulty range presets in admin UI
- **What**: Named bands (Accessible 0–4, Competitive 4–7, Expert 7–10) shown as UI constants at render time. Never stored in the DB.
- **Why deferred**: Needs data populated first to be meaningful.
- **See**: `docs/design/DIFFICULTY_SCORING.md` — Remaining / Future Work.

---

## Post-Launch / Scaling

Items that only become relevant once the game has real traffic.

### Read replicas
- **What**: Add PostgreSQL read replicas for scaling query load.
- **Why deferred**: Not needed until traffic justifies it.
- **See**: `docs/design/TECHNICAL_DESIGN.md`.

---

## Completed (moved here when done)

| Item | Completed | Notes |
|---|---|---|
| Spring Security plumbing + `@PreAuthorize` on admin controllers | Phase 1 (738c13d) | `DevModeAuthFilter` injects dev identity |
| Backfill upsert race-condition fix, JPA auditing, normalisation contract | Phase 2 (972dc5f) | Single `INSERT … ON CONFLICT` |
| `GameStateMachine` coordinator, `useGameLoop` hook | Phase 3 (4ad75ce) | All turn-transition rules in one place |
| `DifficultyCalculator`, viability gate, V13 migration | Phase 4 (932d9d9) | Continuous `difficulty_score NUMERIC(4,2)` |
| MapStruct mappers, `GlobalExceptionHandler`, `EntityType` constants, delete SvelteKit | Phase 5 (93b3fe2) | Audit campaign complete |
| Fix N+1 queries in question materializer | 41ef10e | 20x speedup |
| Supabase JWT validation (replaces `DevModeAuthFilter` on prod) | bf2f57a | Spring Security OAuth2 Resource Server + `SUPABASE_JWT_SECRET`; `DevModeAuthFilter` is `@Profile("!prod")` |
| Google OAuth 2.0 social login via Supabase | bf2f57a | `AuthContext.signInWithGoogle()`, `LoginButton`, callback route — end-to-end |
| Data population — seed questions/answers | bf2f57a | V10–V12 Flyway seed migrations, `seed_entities_dev.sql`, `test-data.sql`, 190K-row backup restore |
| HTTPOnly cookie token storage via Supabase SSR | bf2f57a | `@supabase/ssr` manages sessions in HTTPOnly cookies; no localStorage in any auth path |
| Rate limiting (10/100 req/min per IP) | bf2f57a | `RateLimitFilter.java` registered in `SecurityConfig` |
| Game UI reads question from game state | bf2f57a | `useGameLoop` sets question from API response; `MatchView` receives it as a prop — not hardcoded |
| Autocomplete: cancel debounce on submit, auto-submit on selection, fix focus on click | (current) | `selectEntity` clears debounce + calls `onSelect`; Enter path cancels stale timer; `onMouseDown` uses `preventDefault` to keep focus on input |
| Daily Challenge mode (Wordle-style) | (current) | One challenge per category per day, variable starting scores (101–501), emoji-grid sharing, lazy creation + midnight cron. V19 schema + V20 data backfill. No leaderboards/replay enforcement — trust-based. |
| Lobby redesign: target score toggle + category flow | (current) | Two-column layout; 501/301/101/RND toggle drives `startingScore` POST param; Football 2-step (Random / Select League → 5 leagues); other categories 1-click start. `questionHierarchy.ts` and `CategoryPopup.tsx` are now unused — both deleted 2026-06-11 (CategoryPopup in the UI redesign, questionHierarchy in the post-redesign backlog audit). |
| Football question hierarchy + structured filter | (current) | V24 migration (q_scope/q_league/q_club/q_stat + unique index); FootballFilter DTO; 6 new repo query methods; QuestionService.selectQuestionByFilter; FootballController GET /api/football/clubs; SecurityConfig /api/football/** permitAll; LobbyView 4-screen navStack (root→football→league→club) with slide animations + DB-driven club list + stat type picker (7 stat types). V25 backfill migration for q_* columns on existing questions. V26 seeds 4 combined-stat templates. FootballTeamCompetitionMetricSinceMaterializer extended with goals_assists/goals_appearances/assists_appearances/goals_assists_appearances. QuestionGeneratorService now populates q_* columns on new draft questions. |
| Guardrails: `game_mode` on matches, `suitable_for_daily` on questions | (current) | V19 migration. `game_mode` defaults to `'STANDARD'`; `suitable_for_daily` backfilled for viable easy/medium questions. |
| Geography + Film category seed data (CSV-based Java migrations) | (current) | V21/V22: Replaced ~12K lines of hardcoded SQL INSERTs with CSV data files (`db/data/*.csv`) loaded by `BaseJavaMigration` subclasses. Pattern is now standard for new category seeding. `.gitignore` updated: `!**/db/data/*.csv`. |
