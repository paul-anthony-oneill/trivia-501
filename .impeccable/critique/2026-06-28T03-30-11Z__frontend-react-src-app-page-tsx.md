---
target: /
total_score: 29
p0_count: 0
p1_count: 2
timestamp: 2026-06-28T03-30-11Z
slug: frontend-react-src-app-page-tsx
---
## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Loading skeletons and score animations are strong; no mid-game network-loss indicator |
| 2 | Match System / Real World | 3 | Darts metaphor consistent throughout; "Build Your Own Game" vs "Free Play" naming split |
| 3 | User Control and Freedom | 3 | Exit confirm, cancel on dialogs, staged throw all solid; no undo-last-throw during gameplay |
| 4 | Consistency and Standards | 4 | Same button vocabulary, type hierarchy, color semantics across lobby + game |
| 5 | Error Prevention | 3 | Staged answer prevents misclicks; daily challenge confirm before start; could be more forceful on exit |
| 6 | Recognition Rather Than Recall | 3 | Autocomplete removes recall; hints show remaining options; Free Play undiscoverable below fold on mobile |
| 7 | Flexibility and Efficiency | 2 | Enter-to-throw works; no keyboard shortcuts for power users; acceptable for a game |
| 8 | Aesthetic and Minimalist Design | 4 | Monochrome chrome, color only for game state, every element earns its pixel |
| 9 | Error Recovery | 3 | "No match" in autocomplete, bust feedback clear; network errors during play invisible |
| 10 | Help and Documentation | 1 | No onboarding, no "how to play," darts rules unexplained for first-timers |
| **Total** | | **29/40** | **Good — address weak areas, solid foundation** |

## Anti-Patterns Verdict

**LLM assessment**: Does NOT look AI-generated. No cream/sand body bg, no gradient text, no side-stripe borders, no glassmorphism, no hero-metric template, no eyebrow-on-every-section, no numbered section markers. The dark-native palette, three-typeface system, bullseye motif, and game-state-only color strategy are cohesive and purposeful. The staged-answer flow (pick → "Lined up" → throw) is a real UX choice, not a template. The checkout progress bar with dimmed/faded contextual hints is clever progressive disclosure.

**Deterministic scan**: The detector (`detect.mjs`) returned zero findings across `page.tsx`, all lobby components, MatchView, UI components, auth components, and `globals.css`. Clean bill of health from the automated scan.

**Visual overlays**: Not available — no browser automation in this session. Skipped.

## Overall Impression

A confident, well-crafted game UI. The dark-native posture, game-state color discipline, and staged-answer interaction are all real design decisions executed consistently. The single biggest opportunity: **a first-time player has no idea how darts scoring works and no path to learn it.**

## What's Working

1. **The staged answer flow** (pick → "Lined up" in green soft-bg → "Throw dart"). Prevents misclicks, gives a confirmation moment, and the green-tinted staging area is a great micro-interaction. Enter-key support makes it feel responsive.

2. **Contextual progressive disclosure in the hint stats**. The "180s left" counter dims when score ≤ 180 (irrelevant), while "Checkouts" brightens (now relevant). The checkout progress bar with "◎ 0" target turns a numeric goal into spatial progress. This is the kind of detail that separates real design from templated work.

3. **The dark-native, monochrome-chrome commitment**. The three-typeface system (Bricolage for scores, Hanken for body, Plex Mono for metadata) creates clear information hierarchy without decoration. Game-state colors (green/gold/red) pop because the chrome stays quiet.

## Priority Issues

### [P1] No first-time onboarding
**What**: A new player arriving at the lobby sees "501" in giant numerals with no explanation of darts scoring. There's no "How to Play" link, no tooltip explaining why some scores are bust, and no context for what "checkout" means. The confirmation dialog ("You only get one attempt per day") reads as scary rather than exciting without context.
**Why it matters**: Jordan (First-Timer) will abandon before starting. The game rules are genuinely novel (darts 501 + football trivia) and not guessable from the interface alone.
**Fix**: Add a dismissible "How to Play" card for first-time visitors, or a persistent help icon that opens a short rules summary. Link to `/docs/GAME_RULES.md`. The daily challenge confirmation could reframe as "One shot — make it count" rather than "You only get one attempt."
**Suggested command**: `/impeccable onboard /`

### [P1] Free Play is buried + naming inconsistency
**What**: On mobile, the two daily challenge cards fill the viewport. The "Build Your Own Game" promo card is below the fold and easy to miss. Meanwhile, the nav calls it "Free Play" — the naming split between the homepage CTA and the actual destination page creates confusion.
**Why it matters**: The second core game mode is partially invisible to new players. A returning player who's already done their daily challenge might not scroll far enough to discover it.
**Fix**: Either make the daily cards more compact on mobile (stacked single-column with smaller scores) so the Free Play CTA peeks above the fold, or add a persistent bottom bar / tab nav. Rename consistently — pick "Free Play" or "Build Your Own Game" and use it everywhere.
**Suggested command**: `/impeccable layout /`

### [P2] No mid-game connection/state feedback
**What**: If the network drops during gameplay, there's no visible indicator. The lobby handles the "RESTORING" state with a spinner, but mid-game API failures are silent — the UI just stops responding to throws.
**Why it matters**: Riley (Stress Tester) on a spotty connection loses trust. Casey (Distracted Mobile) switching between apps might return to a silently broken game state.
**Fix**: Add a subtle connection-status indicator. On API failure, show an inline toast or transition to a "Reconnecting…" state rather than silently ignoring input. The disabled state during animation is good; extend that pattern to network errors.
**Suggested command**: `/impeccable harden /`

### [P2] Lobby empty/error states are underdesigned
**What**: The error state ("Couldn't load today's challenges") is a compact inline banner with small text. The empty state ("No challenges available today") is a single muted paragraph. Both are functional but feel like afterthoughts in an otherwise polished surface.
**Why it matters**: The error state blocks the core experience — it should have proportional visual weight. The empty state is a missed opportunity to redirect to Free Play.
**Fix**: Give the error state more visual presence (wider card, icon, clearer retry affordance). The empty state should suggest Free Play as an alternative with a direct link.
**Suggested command**: `/impeccable harden /`

### [P3] Mobile thumb-zone ergonomics
**What**: The "Exit" and theme toggle are in the top corners (hard thumb reach). The entity search input sits mid-screen. Only the "Throw dart" button is in the natural thumb zone.
**Why it matters**: Casey (Distracted Mobile) playing one-handed will struggle. Exit is not a frequent action, but the entity search — the primary input — should be easier to reach.
**Fix**: Consider a bottom-anchored input bar during gameplay (search + staged answer + throw in one row). Move Exit to a swipe gesture or a less prominent position. The theme toggle could live in a settings menu rather than the top bar.
**Suggested command**: `/impeccable adapt /`

## Persona Red Flags

**Jordan (First-Timer)**: Arrives at lobby. Sees giant "501" with no explanation. The "DAILY" kicker and "Today's Challenges" header don't explain the game. Clicks "PLAY NOW" → gets a scary confirmation dialog about one attempt. Has no mental model of darts scoring. Will either play blind and bust repeatedly, or abandon. **No "How to Play" link anywhere on the lobby or game screen.**

**Casey (Distracted Mobile)**: Opens on phone one-handed. Daily cards fill the screen — must scroll to discover Free Play. Exit button in top-left (unreachable). The big 170px score is great for quick glances, but the entity search input requires reaching to mid-screen. **No persistent bottom action bar; primary input is in the thumb-stretch zone.**

**Riley (Stress Tester)**: Refreshes mid-game → sees blank "Restoring game…" spinner with no context about what's being restored. Double-taps "Throw dart" → second tap is silently ignored (disabled state has no "already throwing" feedback). What if the answer API returns a 500? No visible error recovery path in the game UI.

## Minor Observations

- The "BUILD YOUR GAME" CTA pill uses a slightly different layout than the daily challenge "PLAY NOW" pill — one is self-end aligned, the other fills width. Minor inconsistency in button placement.
- The `z-50` on the autocomplete dropdown is the only explicit z-index in the codebase. The DESIGN.md calls for a semantic z-index scale (dropdown 10 → tooltip 60) but the implementation uses an arbitrary value.
- The header "TRIVIA 501" logo on the lobby is a `<Link>` but on the game view it's not present — the game header shows category name + Exit instead. There's no way to navigate back to the lobby without exiting the game.
- The `backdrop:bg-black/60 backdrop:backdrop-blur-sm` on the ConfirmDialog `<dialog>` uses backdrop-filter — technically glassmorphism. But it's a modal backdrop, not a decorative glass card, so it passes the intent check. Still worth noting that the backdrop-blur is the only blur in the system.

## Questions to Consider

- What if the daily challenge cards showed a one-line rules summary instead of just the question text? "Score to zero by naming players. Valid darts scores only."
- What if "Build Your Own Game" were a persistent tab or bottom-nav item rather than a scroll-discoverable card?
- What would a confident, first-time-player-friendly version of the lobby look like? One that assumes zero knowledge but doesn't feel like a tutorial?
