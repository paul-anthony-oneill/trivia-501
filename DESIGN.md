---
name: Trivia 501
description: Daily football trivia with darts 501 scoring — dark native, bullseye-precise, game-state color.
colors:
  obsidian-bg: "#0d0f13"
  slate-surface: "#14171d"
  slate-elevated: "#1b1f27"
  warm-ink: "#f1efe8"
  ash-muted: "#8f939e"
  hairline: "rgba(241,239,232,0.09)"
  line-strong: "rgba(241,239,232,0.24)"
  bullseye-red: "#ff5c4d"
  red-on-dark: "#0d0f13"
  valid-green: "#35d77e"
  checkout-gold: "#f2b63c"
  bust-red: "#ff5757"
  valid-green-soft: "rgba(53,215,126,0.12)"
  checkout-gold-soft: "rgba(242,182,60,0.13)"
  bust-red-soft: "rgba(255,87,87,0.12)"
  paper-bg: "#f5f2ea"
  paper-surface: "#fdfcf7"
  paper-elevated: "#ebe7db"
  near-black-ink: "#17191e"
  stone-muted: "#6d7077"
  light-hairline: "rgba(23,25,30,0.1)"
  light-line-strong: "rgba(23,25,30,0.28)"
  deep-red: "#df3a2c"
  red-on-light: "#fdfcf7"
  forest-green: "#128a4c"
  amber-gold: "#9c6d12"
  crimson-red: "#cd2f2f"
typography:
  display:
    fontFamily: "Bricolage Grotesque, sans-serif"
    fontSize: "clamp(2.5rem, 7vw, 5.5rem)"
    fontWeight: 800
    lineHeight: 1
    letterSpacing: "-0.04em"
    fontVariation: "'wdth' 80"
  headline:
    fontFamily: "Bricolage Grotesque, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  body:
    fontFamily: "Hanken Grotesk, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.6
  label:
    fontFamily: "IBM Plex Mono, monospace"
    fontSize: "0.625rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0.22em"
    textTransform: "uppercase"
  hint:
    fontFamily: "IBM Plex Mono, monospace"
    fontSize: "0.6875rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "0.06em"
rounded:
  xs: "2px"
  sm: "4px"
  md: "8px"
  full: "9999px"
spacing:
  tight: "6px"
  snug: "8px"
  base: "12px"
  roomy: "16px"
  generous: "24px"
  airy: "32px"
  breath: "40px"
components:
  button-primary:
    backgroundColor: "{colors.warm-ink}"
    textColor: "{colors.obsidian-bg}"
    rounded: "{rounded.sm}"
    padding: "12px 20px"
  button-primary-hover:
    backgroundColor: "{colors.bullseye-red}"
    textColor: "{colors.obsidian-bg}"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.ash-muted}"
    rounded: "{rounded.sm}"
    padding: "8px 16px"
  chip-cta:
    backgroundColor: "{colors.bullseye-red}"
    textColor: "{colors.obsidian-bg}"
    rounded: "{rounded.full}"
    padding: "10px 20px"
  card-surface:
    backgroundColor: "{colors.slate-surface}"
    rounded: "{rounded.md}"
    padding: "24px 32px"
---

# Design System: Trivia 501

## 1. Overview

**Creative North Star: "The Dartboard"**

Trivia 501's visual system is built on concentric precision — like a dartboard under pub lights. The chrome stays quiet (near-black backgrounds, hairline borders, monospace whispers) so the game-state rings read instantly: green for valid, gold for checkout, red for bust. The bullseye-red accent is a single point of color — the brand mark, the primary CTA, the focus ring. Everything else recedes.

The system is dark-native. Football happens at night, in pubs, on couches with the lights low. The dark theme is the primary mode; the light theme is a deliberate daytime alternative, not an afterthought. Both themes share the same structural vocabulary: sharp corners (2–4px), confident hover lifts, and a three-typeface hierarchy that moves from display (Bricolage Grotesque — big scores, headings) through body (Hanken Grotesk — answers, labels) to mono (IBM Plex Mono — kickers, hints, timestamps).

The aesthetic is FotMob-meets-Chess.com: modern sports data, dark, stats-forward, clean competitive. It explicitly rejects cartoonish casual-game visuals, flashy betting-site energy, gamified SaaS dashboards, and over-branded sports media. The warm-neutral cream body background — the saturated AI default of 2026 — is banned. The light theme uses a true off-white with no warm-tint reflex.

**Key Characteristics:**
- Dark-first with a deliberate light alternative
- Monochrome chrome; color belongs to game state only
- Three-typeface system: display (scores), sans (body), mono (metadata)
- Sharp radii (2–8px) — no soft, friendly corners
- Tactile interactions: hover lifts, focus rings, scale-on-press
- Bullseye brand mark: a 10px red circle with a concentric ring, used once per surface

## 2. Colors

The palette splits into three layers: the **chrome** (obsidian-to-warm-ink neutrals that carry 95% of the interface), the **accent** (a single bullseye red used on ≤5% of any screen), and the **game-state colors** (green/gold/red applied only as information, never as decoration).

### Primary (Accent)
- **Bullseye Red** (#ff5c4d dark / #df3a2c light): The dartboard's center ring. Used exclusively for the brand mark (`.bullseye`), primary CTA hover states, focus rings, and the header logo's "501" numeral. Never used as a background larger than a pill button, never used for decoration.

### Neutral (Chrome — Dark Theme)
- **Obsidian** (#0d0f13): Page background. The deepest layer — the wall behind the dartboard.
- **Slate Surface** (#14171d): Card, input, and dropdown backgrounds. One step above obsidian.
- **Slate Elevated** (#1b1f27): Hover states, active list items, the second surface layer.
- **Warm Ink** (#f1efe8): Primary text. Slightly warm off-white — not clinical pure white. Used for body text, headings, and button-ghost hover states.
- **Ash Muted** (#8f939e): Secondary text, placeholders, disabled states, icon default color.
- **Hairline** (rgba(241,239,232,0.09)): Subtle dividers, card borders at rest.
- **Line Strong** (rgba(241,239,232,0.24)): Focus rings, active borders, button-ghost hover borders.

### Neutral (Chrome — Light Theme)
- **Paper** (#f5f2ea): Page background. True off-white — no warm-tint reflex. Chroma is deliberately near-zero.
- **Paper Surface** (#fdfcf7): Card and input backgrounds.
- **Paper Elevated** (#ebe7db): Hover states, elevated surfaces.
- **Near-Black Ink** (#17191e): Primary text.
- **Stone Muted** (#6d7077): Secondary text.
- Light hairline and line-strong follow the same opacity logic against near-black.

### Game-State Colors (both themes)
- **Valid Green** (#35d77e dark / #128a4c light): Correct answer, score reduced. Appears as score-pop flash, valid move indicator, and share-grid 🟩.
- **Checkout Gold** (#f2b63c dark / #9c6d12 light): In checkout range (−10 to 0). Appears as the score ring-burst animation, checkout indicator, and share-grid 🎯. Also used for the daily challenge section header dot.
- **Bust Red** (#ff5757 dark / #cd2f2f light): Invalid answer, bust, error. Appears as the shake animation flash, bust indicator, error text, and share-grid 🟥.

Each game-state color has a soft variant (`*-soft`) at ~12% opacity — used for subtle background tints on result toasts, score popup backdrops, and inline feedback areas.

### Named Rules

**The Bullseye Rule.** The red accent is used on ≤5% of any given screen. If you see red, it's either the brand mark or the single most important action on the page. Its rarity is the point.

**The Ring Rule.** Green, gold, and red communicate game state in concentric priority — green (valid) is safe, gold (checkout) is urgent, red (bust) is failure. Never use these colors for anything other than game-state information. A green button that doesn't mean "correct" is a lie.

**The Chrome Stays Quiet Rule.** The neutral palette (obsidian/slate/ink/muted/line) carries 95% of the interface. If the chrome is drawing attention, the game-state colors can't do their job.

## 3. Typography

**Display Font:** Bricolage Grotesque (variable, with `wdth` axis)
**Body Font:** Hanken Grotesk
**Label/Mono Font:** IBM Plex Mono (weights 400, 500)

**Character:** Bricolage brings the personality — wide, confident, slightly condensed for big numerals. Hanken is the workhorse sans, clean and unobtrusive. Plex Mono handles the metadata layer — kickers, timestamps, hints, button-ghost labels. The pairing is geometric-display + humanist-sans + technical-mono: three distinct voices, never colliding.

### Hierarchy
- **Display** (Bricolage Grotesque, 800, clamp(2.5rem, 7vw, 5.5rem), line-height 1, tracking −0.04em, `wdth` 80): Game scores, target numbers. The `.display-num` utility class. Used only where a number IS the content — daily challenge cards, score displays, checkout moments.
- **Headline** (Bricolage Grotesque, 700, 1.25–1.5rem, line-height 1.2, tracking −0.02em): Section headings, card titles, category names. Never smaller than 1.25rem; never larger than 2rem.
- **Body** (Hanken Grotesk, 400, 0.875rem, line-height 1.6): Question text, answer suggestions, descriptions. Capped at 65–75ch for prose; denser for lists. Game UI body runs at 15px (0.9375rem) for answer entries.
- **Label** (IBM Plex Mono, 400, 0.625rem, line-height 1.5, tracking 0.22em, uppercase): Kickers, section eyebrows, button-ghost text. The `.kicker` utility class. Used sparingly — one per section maximum. Not an every-section reflex.
- **Hint** (IBM Plex Mono, 400, 0.6875rem, line-height 1.5, tracking 0.06em): Helper text, input hints, "Keep typing…" messages, countdown timers. The `.hint` utility class. Sentence case, quieter than kickers.

### Named Rules

**The Three Voices Rule.** Bricolage, Hanken, and Plex Mono each own a distinct layer. Never use Bricolage for body text. Never use Plex Mono for headings. Never use Hanken for scores. The separation IS the hierarchy.

**The One Kicker Rule.** A `.kicker` appears at most once per section. If you're putting an eyebrow above every heading, you're scaffolding, not designing.

## 4. Elevation

The system uses shadows sparingly and only in response to interaction. At rest, surfaces are flat — distinguished by background color contrast (obsidian → slate → slate-elevated), not by shadow. Depth comes from tonal layering and hover lift, not from ambient shadow casting.

### Shadow Vocabulary
- **Card Lift** (`box-shadow: 0 2px 12px rgba(0,0,0,0.25)` dark / `0 2px 12px rgba(23,25,30,0.06)` light): Applied on card hover alongside a −2px translateY. The card "lifts off the surface." Never present at rest.
- **Pop** (`box-shadow: 0 24px 64px rgba(0,0,0,0.55)` dark / `0 24px 64px rgba(23,25,30,0.2)` light): Dropdowns, modals, autocomplete menus. Deep, diffuse — the element has broken free of the surface.
- **Bullseye Ring** (`box-shadow: 0 0 0 3px var(--bg), 0 0 0 4.5px var(--accent)`): The brand mark's concentric halo. Not a shadow — a deliberate ring that echoes the dartboard.

### Named Rules

**The Flat-At-Rest Rule.** Surfaces are flat at rest. Shadows appear only as a response to interaction (hover, focus, dropdown open). A card with a permanent shadow is a card trying too hard.

**The Pop Distance Rule.** The shadow-blur ratio signals distance from surface: 12px blur = still attached (card hover); 64px blur = floating free (dropdown, modal). Never use a mid-range blur (24–40px) for UI elements — it reads as "uncertain."

## 5. Components

### Buttons

**Character:** Tactile and confident. Buttons have weight — they lift on hover, scale on press, and respond instantly.

- **Shape:** Sharp rectangles with 4px radius (`rounded-sm`). Never fully rounded (pill) except for the CTA chip variant.
- **Primary (`btn-primary`):** Warm ink background (#f1efe8), obsidian text. On hover: background flips to bullseye red, text stays obsidian. Transition: 150ms. Disabled: 30% opacity, cursor not-allowed, hover color suppressed. Used for: game-start CTAs, submit-answer, modal confirms.
- **Ghost (`btn-ghost`):** Transparent background, ash muted text, hairline border. Uppercase, mono font, 11px, tracking 0.18em. On hover: text → warm ink, border → line-strong. Used for: secondary actions, filter toggles, "View Result" links.
- **CTA Pill:** Bullseye red background, obsidian text. Fully rounded (9999px). Display font, bold, 14px. On hover: shadow-pop + scale(1.05). Used for: "PLAY NOW," "BUILD YOUR GAME," "RESUME." The most visually committed button — one per card maximum.

### Cards

**Character:** Quiet containers. The content does the work; the card is just a frame.

- **Corner Style:** 8px radius (`rounded-md`).
- **Background:** Slate Surface (#14171d) in dark, Paper Surface (#fdfcf7) in light.
- **Border:** Hairline at rest. On hover: border shifts to line-strong.
- **Hover Behavior:** −2px translateY + card-lift shadow. Transition: 200ms.
- **Internal Padding:** 24px (p-6) default, 32px (p-8) on larger breakpoints.

Cards are used for daily challenge entries and the "Build Your Own Game" promo. They are never nested. A card inside a card is always wrong.

### Inputs & Autocomplete

**Character:** Invisible until needed. The input is a text line with a prompt; the dropdown is a surface breaking free.

- **Text Input:** No visible border (borderless or hairline-bottom). Placeholder in ash muted. Focus: no ring on the input itself; the parent container or form handles focus indication. Font: Hanken Grotesk, 15px.
- **Autocomplete Dropdown:** Positioned above the input (`.bottom-full`), Surface background, line-strong border, 8px radius, pop shadow. Max-height 300px with thin custom scrollbar.
- **Dropdown Items:** 14px top/bottom padding, 3.5px left/right. Hanken Grotesk 15px for names, Plex Mono 11px for nationality badges. Hover/active: Slate Elevated background. No border, no side-stripe — tonal background shift only.
- **States:** Loading → "Loading…" in mono hint style. No match → "No match — try a different spelling" in bust red. 1–3 characters → "Keep typing for suggestions…" in hint style.

### Chips & Pills

- **Score Display:** `.display-num` — Bricolage 800, clamp sizing, tabular-nums, negative tracking. The visual center of any card it appears in. Used for: target scores (daily challenge cards), current score (MatchView), checkout flash.
- **Kicker Label:** Plex Mono, 10–11px, uppercase, 0.15–0.22em tracking. Ash muted. Appears above card content or section headers. Always paired with a visual anchor (bullseye dot, gold dot, or icon).
- **Status Badge:** Inline pill with border. Mono font, uppercase. Used for: "PLAYED," "DAILY," "IN PROGRESS" labels on daily challenge cards.

### Navigation

- **Header:** Obsidian background, hairline bottom border. Left: bullseye mark + "TRIVIA 501" logo (Bricolage extrabold, "501" in bullseye red). Right: ThemeToggle + LoginButton.
- **Theme Toggle:** 36×36px circle, hairline border, muted icon color. Hover: border → line-strong, icon → warm ink. Renders both sun and moon SVGs; CSS shows only the relevant one based on `[data-theme]`.
- **Drill-Down:** Category → League → Club navigation uses slide-in-right (220ms) and slide-in-left (220ms) animations. Forward navigation pushes right; back navigation pops left.

### Signature: The Bullseye Mark

A 10px diameter circle in bullseye red, with a concentric ring: `box-shadow: 0 0 0 3px var(--bg), 0 0 0 4.5px var(--accent)`. The outer ring is the page background color, creating a gap; the inner ring is the accent. Used once — in the header, next to the logo. It's the system's signature. Do not scatter bullseyes across the page; one is identity, two is decoration.

### Signature: The Score Ring Burst

On checkout (score hits −10 to 0), two concentric rings expand outward from the score display: an inner ring in checkout gold, an outer ring (delayed 220ms) in bullseye red. 1.2s animation, cubic-bezier(0.2, 0.7, 0.3, 1). The rings scale from 0.35 → 1.7 and fade from 0.9 → 0 opacity. Reduced motion: rings are hidden entirely.

## 6. Do's and Don'ts

### Do:
- **Do** use the dark theme as the default. Light mode is a deliberate alternative, not a fallback.
- **Do** reserve bullseye red (#ff5c4d) for the brand mark, primary CTA hover, and focus rings. If you see red elsewhere, delete it.
- **Do** let game-state colors (green/gold/red) carry meaning. A green element means "correct." A red element means "bust" or "error." Never repurpose them.
- **Do** use Bricolage Grotesque only for scores and headings. Hanken Grotesk for body. IBM Plex Mono for metadata. The three voices never cross.
- **Do** apply shadows only on interaction (hover, focus, dropdown). Flat at rest.
- **Do** use tonal background shifts (obsidian → slate → slate-elevated) for depth, not shadows.
- **Do** keep hover transitions at 150–250ms. The user is in flow; don't make them wait for choreography.
- **Do** test every animation in `prefers-reduced-motion: reduce`. The system already handles this — don't add new animations without the media query.
- **Do** use 8px radius for cards, 4px for buttons, 2px for focus rings. The scale is deliberate; don't invent new radii.
- **Do** provide skeleton loading states for card grids. The daily challenge section shows the pattern.

### Don't:
- **Don't** use cartoonish casual-game visuals — no bubbly illustrations, no mascots, no primary-colour palettes.
- **Don't** use flashy betting-site energy — no neon, no aggressive gradients, no urgency-manipulation patterns.
- **Don't** use gamified SaaS dashboard patterns — no leaderboard-heavy layouts, no progress bars everywhere, no XP/streak-fire gamification crutches.
- **Don't** use over-branded sports media language — no giant league logos, no jersey-texture backgrounds, no "SPORTS!" shouting.
- **Don't** use the warm-neutral cream/sand/beige body background. The dark theme is obsidian (#0d0f13); the light theme is paper (#f5f2ea), a true off-white with near-zero chroma.
- **Don't** use gradient text (`background-clip: text`). Solid colors only. Emphasis comes from weight, size, or the accent.
- **Don't** use side-stripe borders (`border-left` > 1px as a colored accent on cards). Use tonal background shifts or full borders.
- **Don't** nest cards inside cards. One surface level per container.
- **Don't** add a second bullseye mark to any surface. One is identity; two is decoration.
- **Don't** use glassmorphism or backdrop-filter blur. The system is opaque and confident.
- **Don't** put a `.kicker` above every section heading. One per section maximum; often zero is better.
- **Don't** use `z-index` values like 999 or 9999. Build a semantic scale: dropdown (10) → sticky (20) → modal-backdrop (30) → modal (40) → toast (50) → tooltip (60).
