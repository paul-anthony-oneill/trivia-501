# Design Sync Notes — Trivia 501 (frontend-react)

## Repo shape
- Next.js app, not a standalone DS package — **no `dist/`** → synth-entry mode (converter synthesises bundle from `src/components/` via `componentSrcMap`)
- `@/` path alias → `src/` (tsconfig.json `paths`)

## CSS — Tailwind v4
`globals.css` starts with `@import "tailwindcss"` (PostCSS build-time directive, not browser CSS).
Before each sync, compile it first:
```bash
cd frontend-react
npx tailwindcss --input src/app/globals.css --output ds-compiled.css
```
Then set `cfg.cssEntry` to `"ds-compiled.css"`. Without this step the Tailwind utilities (`bg-bg`, `text-ink`, etc.) won't resolve in preview cards.

## Fonts
Hanken Grotesk, IBM Plex Mono, Bricolage Grotesque — loaded via `next/font/google` at runtime.
No woff2 in repo. Suppressed via `runtimeFontPrefixes` — expected and correct.

## AuthContext
Components calling `useAuth()` get the default context value (`user: null`, `loading: true`) without
a provider — the default is set in `createContext({...})`. No crash, just unauthenticated state.
No `cfg.provider` needed.

## EntitySearch
Calls `loadEntityCache()` on mount → fetches from backend. No backend in preview.
The input shell renders fine; the suggestions dropdown stays empty.
Preview shows: empty input, placeholder, no suggestions — an accurate static representation.

## DebugPanel
Starts with `open = false` → no API call on mount. Preview shows the toggle/closed state. Fine.

## LobbyView + MatchView
Floor cards by design — full-page app views with `next/dynamic`, `useAuth`, API calls.
Worth authoring if the UI overhaul ever needs screen-level comps in Claude Design.

## Known render warns
*(populated during first sync verify loop)*

## Re-sync risks
- `ds-compiled.css` must be regenerated whenever `globals.css` or component class usage changes
- `EntitySearch` previews are static (empty suggestions) — valid representation, not a bug
- `ThemeToggle` preview reflects `data-theme` not set (defaults to "light" fallback)
- `LobbyView`/`MatchView` floor cards — won't auto-update when their source changes
