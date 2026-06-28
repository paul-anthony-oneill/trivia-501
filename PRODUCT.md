# Product

## Register

product

## Users

Football fans who enjoy daily puzzles and trivia. They play on desktop or mobile (PWA), usually in quick sessions — a few minutes to tackle the daily challenge, compare with friends via share links, or mess around in Free Play. No account needed to play; sign-in is optional and only for admin features.

Their context: could be commuting, on a lunch break, or winding down in the evening. The game is a daily ritual, not a marathon session.

## Product Purpose

Trivia 501 combines football knowledge with darts 501 scoring into a daily social puzzle. The goal is to reach exactly zero by naming players whose stats match the question. One challenge per category per day, same for everyone globally. Share your emoji-grid result and compare with friends — Wordle-style.

Free Play mode lets players pick any category and question to play on their own terms, no daily lock.

No leaderboards. No multiplayer. No monetization at launch. The product is a tight, well-crafted single-player daily trivia experience.

## Brand Personality

**Sharp, playful, focused.** Three words:

- **Sharp** — precise like a dart throw. The UI is clean, decisive, editorial. No fluff, no decoration-for-decoration's-sake.
- **Playful** — it's a game, not a spreadsheet. The bullseye motif, emoji-grid sharing, score animations, and occasional cheeky copy keep it fun.
- **Focused** — one question, one attempt, one score to zero. The interface gets out of the way so the player can think. No notification spam, no upsells, no cruft.

References: FotMob / modern football data apps — dark native, stats-forward, live-score energy. Clean competitive game UIs like Chess.com (polished, serious but accessible).

## Anti-references

- **Cartoonish casual-game visuals** — no bubbly illustrations, no mascots, no primary-colour palettes
- **Flashy betting-site energy** — no neon, no aggressive gradients, no urgency-manipulation patterns
- **Gamified SaaS dashboards** — no leaderboard-heavy layouts, no progress bars everywhere, no XP/streak-fire gamification crutches
- **Over-branded sports media** — no giant league logos, no jersey-texture backgrounds, no "SPORTS!" shouting
- **The cream/sand/beige body bg** — the warm-neutral AI default. The dark theme is the native mode; the light theme uses true off-white with no warm tint default

## Design Principles

1. **Color carries meaning** — the monochrome chrome stays quiet; green, red, and gold are reserved for game state (valid, bust, checkout). The bullseye-red accent is the brand mark and primary CTA color only.

2. **Precision over decoration** — every element earns its place. Sharp radii, tight spacing, monospace hints. If it doesn't help the player think or play, cut it.

3. **Daily ritual, not a treadmill** — one attempt per category per day. No streaks to maintain, no FOMO mechanics. The game respects the player's time and attention.

4. **Dark native, light considered** — the dark theme is the primary mode (football is often watched at night, in pubs, on couches). The light theme is a deliberate alternative, not an afterthought.

5. **Accessible by default** — WCAG 2.1 AA. Color is never the only signal. Reduced motion is respected. The game works on keyboard, screen reader, and any viewport.

## Accessibility & Inclusion

- Target: WCAG 2.1 AA
- Reduced motion: all animations have `prefers-reduced-motion: reduce` alternatives (already implemented)
- Color is reinforced by text labels, icons, and position — never the sole information channel
- Focus indicators visible and consistent (accent outline)
- Touch targets ≥ 44px for mobile play
- Light and dark themes both tested for contrast compliance
