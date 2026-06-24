---
name: react-dev-guidelines
description: Frontend development guidelines for Next.js 16 / React 19 / TypeScript / Tailwind CSS 4. Covers component patterns, data fetching, file organization, React Context, hooks, App Router routing, loading states, and TypeScript best practices for the Football-501 React PWA.
---

# React Development Guidelines

## Purpose

Establish consistent patterns for the Football-501 React frontend (Next.js 16 App Router + React 19 + Tailwind 4) — component structure, data fetching, React Context, custom hooks, routing, and TypeScript best practices.

## When to Use This Skill

- Creating new React components or pages
- Fetching data from the Spring Boot API
- Setting up Next.js App Router routes
- Managing shared state with React Context
- Styling with Tailwind CSS
- Writing custom hooks
- TypeScript best practices

---

## Quick Start

### New Component Checklist

- [ ] `"use client"` directive at the top (all pages are client-rendered — no SSR needed)
- [ ] Keep component under 300 lines — extract sub-components if larger
- [ ] Define props as a typed interface
- [ ] Tailwind for all styling — use project design tokens (`text-ink`, `bg-surface`, `border-line`, etc.), not raw Tailwind colors
- [ ] Handle loading, empty, and error states explicitly
- [ ] Use `@/` path alias for imports (never relative paths beyond one level)

### New Feature Checklist

- [ ] Hooks in `src/hooks/use{Feature}.ts`
- [ ] Components in `src/components/{domain}/`
- [ ] API calls through `src/lib/api/client.ts` (`apiFetch`)
- [ ] Shared types in `src/lib/types/` (or co-locate interfaces in the hook file if they're hook-specific)
- [ ] App Router page: `src/app/{feature}/page.tsx`

---

## File Organization

```
frontend-react/src/
  app/                       # Next.js App Router pages
    layout.tsx               # Root layout (providers, fonts, metadata)
    page.tsx                 # Home / lobby
    daily/
      page.tsx               # Daily challenge overview
      [category]/
        page.tsx             # Daily challenge detail / share target
    admin/
      layout.tsx             # Admin layout (sidebar)
      page.tsx               # Admin dashboard
      questions/
        page.tsx             # Question list
        create/page.tsx      # Create question
        [id]/page.tsx        # Edit question
      ...
  components/
    game/                    # Game-specific components
      lobby/LobbyView.tsx    # Lobby with drill-down nav
      match/MatchView.tsx    # Active game view
      EntitySearch.tsx       # Autocomplete input
      ...
    ui/                      # Reusable UI primitives
      ConfirmDialog.tsx
      Select.tsx
      ...
    auth/                    # Auth components
    admin/                   # Admin CRUD components
  hooks/                     # Custom hooks (one per file)
    useGameLoop.ts           # Core game state + API calls
    useDailyChallenge.ts     # Daily challenge status
    useAnimatedScore.ts      # Score transition animation
    ...
  context/                   # React Context providers
    AuthContext.tsx           # Supabase auth state
    ToastContext.tsx          # Toast notifications
  lib/
    api/
      client.ts              # apiFetch — wraps fetch with Bearer auth injection
      footballApi.ts         # Football-specific API helpers
      entityCache.ts         # Client-side entity cache for autocomplete
    types/
      admin.ts               # Admin-specific types
    dailyLock.ts             # localStorage lock helpers for daily challenges
  utils/
    share.ts                 # Share URL generation
    country.ts               # Flag emoji + nationality formatting
    supabase/                # Supabase client helpers (client.ts, server.ts, middleware.ts)
  middleware.ts              # Next.js middleware
```

---

## Component Patterns

### Basic Component Structure

```tsx
"use client";

import React from "react";

interface PlayerCardProps {
  player: Player;
  onSelect: (player: Player) => void;
}

export default function PlayerCard({ player, onSelect }: PlayerCardProps) {
  return (
    <div className="flex items-center gap-2 p-3 rounded-md bg-surface border border-line">
      <span className="font-semibold text-ink">{player.name}</span>
      <button
        className="ml-auto px-3 py-1 bg-accent text-bg rounded hover:opacity-90 transition-opacity"
        onClick={() => onSelect(player)}
      >
        Select
      </button>
    </div>
  );
}
```

### Loading States

Use early-return or ternary patterns — React has no `{#await}` equivalent:

```tsx
"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api/client";

export default function QuestionCard({ questionId }: { questionId: string }) {
  const [question, setQuestion] = useState<Question | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    apiFetch(`/api/questions/${questionId}`)
      .then(async (res) => {
        if (!res.ok) throw new Error(`Failed: ${res.status}`);
        return res.json();
      })
      .then((data) => { if (!cancelled) setQuestion(data); })
      .catch((err) => { if (!cancelled) setError(err.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [questionId]);

  if (loading) return <Skeleton />;
  if (error) return <ErrorMessage message={error} />;
  if (!question) return null;

  return <div>{question.text}</div>;
}
```

**Key pattern**: `cancelled` flag in `useEffect` cleanup to avoid state updates on unmounted components.

---

## Data Fetching

Always use `apiFetch` from `@/lib/api/client` — it auto-injects the Supabase Bearer token for authenticated requests:

```typescript
// src/lib/api/client.ts — already exists, use it everywhere
import { apiFetch } from "@/lib/api/client";

// GET
const res = await apiFetch("/api/freeplay/status");
if (!res.ok) throw new Error(`Failed: ${res.status}`);
const data = await res.json();

// POST
const res = await apiFetch("/api/freeplay/start", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ categorySlug, startingScore }),
});
```

**Rules:**
- Always use `apiFetch`, never raw `fetch` for `/api/*` calls
- Throw on non-OK responses (the caller handles error display)
- Parse error body for the server's `error` or `message` field
- API routes: `/api/freeplay/*`, `/api/daily-challenge/*`, `/api/entities/*`, `/api/categories/*`, `/api/admin/*`

---

## React Context (Shared State)

Use Context for app-wide state that many components need. This project uses two:

```tsx
// src/context/AuthContext.tsx — pattern

"use client";

import { createContext, useContext, useState, useEffect, useCallback, useMemo } from "react";

interface AuthState {
  user: User | null;
  loading: boolean;
  signOut: () => Promise<void>;
  // ...
}

const AuthContext = createContext<AuthState>({ /* defaults */ });

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  // ... state logic ...

  const value = useMemo(() => ({ user, loading, signOut }), [user, loading, signOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
```

**Rules:**
- Wrap providers in `src/app/layout.tsx` (already done — `AuthProvider`, `ToastProvider`)
- Export a `useX()` hook alongside the provider — never call `useContext(XContext)` directly
- `useMemo` the context value to avoid unnecessary re-renders
- Context is for global-ish state; for component-local state, just use `useState`

---

## Custom Hooks

Hooks own state + API calls. Components are mostly presentation:

```typescript
// src/hooks/useGameLoop.ts — the main game hook (already exists)

"use client";

import { useState, useEffect, useRef } from "react";
import { apiFetch } from "@/lib/api/client";

export interface GameLoopState {
  score: number;
  question: string;
  gameStatus: GameStatus;
  moves: Move[];
  // ...
}

export interface GameLoopActions {
  startNewGame: (categorySlug: string, label: string) => Promise<void>;
  submitAnswer: (answer: string) => Promise<void>;
  exitGame: () => void;
}

export function useGameLoop(): GameLoopState & GameLoopActions {
  // state + effects + actions
  return { score, question, gameStatus, moves, startNewGame, submitAnswer, exitGame };
}
```

**Pattern**: hooks return a flat object combining state + actions (not `[state, actions]` tuples). Components destructure what they need.

---

## Routing (Next.js App Router)

File-based routing with `page.tsx` files:

```
src/app/
  layout.tsx              # Root layout (providers, fonts, metadata)
  page.tsx                # Home / lobby
  daily/
    page.tsx              # /daily
    [category]/
      page.tsx            # /daily/football, /daily/film, etc.
  admin/
    layout.tsx            # Admin layout (sidebar)
    page.tsx              # /admin
    questions/
      page.tsx            # /admin/questions
      create/
        page.tsx          # /admin/questions/create
      [id]/
        page.tsx          # /admin/questions/123
```

**Key points:**
- All pages are `"use client"` — data comes from the Spring Boot API at runtime, no SSR
- Next.js `rewrites()` in `next.config.ts` proxies `/api/*` → Spring Boot in dev
- Dynamic segments: `[category]`, `[id]` — accessed via `useParams()` on the client, or `params` prop

---

## Tailwind CSS 4 Patterns

This project uses design tokens defined via `@theme inline` in `globals.css`. Prefer these over raw Tailwind colors:

| Token | Usage |
|---|---|
| `text-ink` | Primary text color |
| `text-muted` | Secondary / hint text |
| `bg-bg` | Page background |
| `bg-surface` / `bg-surface-2` | Card / elevated surfaces |
| `border-line` / `border-line-strong` | Borders |
| `text-accent` / `bg-accent` | Brand accent color |
| `text-ok` / `bg-ok-soft` | Success / valid |
| `text-danger` / `bg-danger-soft` | Error / bust |
| `text-gold` | Checkout / daily challenge gold |

```tsx
// Good — uses project tokens
<div className="bg-surface border border-line rounded-md p-5">
  <span className="kicker">Points remaining</span>
  <div className="display-num">{score}</div>
</div>

// Avoid — raw Tailwind colors
<div className="bg-white border border-gray-200 rounded-lg p-5">
```

**Utility classes** defined in the project:
- `kicker` — small uppercase label
- `display-num` — giant score number
- `hint` — muted instructional text
- `btn-primary`, `btn-ghost` — button variants
- `scrollbar-thin` — custom scrollbar
- `animate-score-pop`, `animate-shake`, `animate-rise`, `animate-fade-in`, `animate-nav-push`, `animate-nav-pop`

**Rules:**
- Tailwind classes only — no inline styles, no CSS-in-JS
- Use project tokens, not raw colors
- Responsive prefixes: `sm:`, `md:`, `lg:`
- Dark mode is handled by `data-theme` attribute on `<html>` — use CSS variables, not `dark:` prefix

---

## TypeScript Standards

```typescript
// Props: always an explicit interface
interface MatchViewProps {
  score: number;
  question: string;
  moves: Move[];
  onExit: () => void;
  onSubmitAnswer: (answer: string, entityId?: string) => void;
  entityType?: string;   // optional with default in destructuring
}

// Use @/ imports (never relative paths beyond one level)
import { apiFetch } from "@/lib/api/client";
import { useAuth } from "@/context/AuthContext";
import type { Move } from "@/hooks/useGameLoop";

// Explicit return types on exported functions
export function calculateBust(score: number): boolean {
  return score > 180 || score < -10;
}

// Discriminated unions for status types
type GameStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "RESTORING";

// No `any` — use `unknown` if truly untyped, or define the shape
```

---

## Core Principles

1. **`apiFetch` for all API calls** — auto-injects Bearer token, no raw `fetch` for `/api/*`
2. **React Context for shared state** — not prop-drilling; but don't over-use it
3. **Custom hooks own the logic** — components are mostly presentation
4. **`"use client"` on every page** — no SSR, data comes from Spring Boot at runtime
5. **`@/` imports** — use the path alias, not relative paths beyond one level
6. **Project design tokens** — `text-ink`, `bg-surface`, `border-line`, not raw Tailwind colors
7. **`cancelled` flag in effects** — avoid state updates on unmounted components
8. **Co-locate types with their hook** — if a type is only used by one hook, define it there
9. **No new dependencies without discussion** — the stack is deliberately minimal (Next.js + React + Tailwind + Supabase)
