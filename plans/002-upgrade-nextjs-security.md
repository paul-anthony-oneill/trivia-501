# Plan 002: Next.js is on a patched 16.3.x release with no critical/high npm advisories

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 0baf851..HEAD -- frontend-react/package.json frontend-react/package-lock.json frontend-react/src/middleware.ts`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW–MED (framework minor bump; the middleware is the thing most likely to change behaviour)
- **Depends on**: plans/001-run-frontend-tests-in-ci.md (so the upgrade is checked by typecheck + unit tests)
- **Category**: security / migration
- **Planned at**: commit `0baf851`, 2026-09-28

## Why this matters

`npm audit --omit=dev` at 0baf851 reports **1 critical, 3 high, 1 moderate**. The critical
one is in `next` itself: versions up to and including 16.3.2 are affected, and this repo pins
`16.2.6`. The critical advisories include a "Middleware / Proxy bypass in App Router applications
using Turbopack" (GHSA-6gpp-xcg3-4w24) and a Server Actions DoS (GHSA-m99w-x7hq-7vfj). This
app **does** use middleware (`src/middleware.ts`) as the gate on the `/admin` UI. The backend
still enforces `ROLE_ADMIN`, so data isn't exposed, but the UI gate is bypassable. The fix is a
non-major bump: `npm audit` reports the fix as `next@16.3.6`, `isSemVerMajor: false`.

## Current state

- `frontend-react/package.json` pins exact versions:
  ```json
  "next": "16.2.6",
  "react": "19.2.4",
  "react-dom": "19.2.4"
  ```
- `npm audit --omit=dev` findings at 0baf851 (all of these are fixable by the bump + `npm audit fix`):

  | Package | Severity | Fix |
  |---|---|---|
  | `next` (≤16.3.2) | critical | `next@16.3.6` |
  | `postcss` (nested under next) | high | via `next@16.3.6` |
  | `sharp` (under next) | high | via `next@16.3.6` |
  | `nanoid` ≤3.3.17 | high | `npm audit fix` |
  | `baseline-browser-mapping` | moderate | `npm audit fix` |

- Latest 16.x on npm at planning time: `16.3.6`.
- `frontend-react/src/middleware.ts` (the only middleware) redirects non-admin users away from
  `/admin/:path*`, using Supabase (`@/utils/supabase/middleware`). Its matcher is
  `"/admin/:path*"`. In Next 16 the file convention `middleware.ts` is deprecated in
  favour of `proxy.ts`, but it still works. **Don't rename it in this plan.**
- Verification baseline at 0baf851: `npm run typecheck` exits 0; `npm test` → 135 passed;
  `npm run build` passes in CI.

## Commands you will need

| Purpose   | Command (run from `frontend-react/`) | Expected on success |
|-----------|--------------------------------------|---------------------|
| Upgrade   | `npm install next@16.3.6 --save-exact` | exit 0 |
| Audit fix | `npm audit fix`                      | exit 0 |
| Audit     | `npm audit --omit=dev --audit-level=high` | exit 0 |
| Typecheck | `npm run typecheck`                  | exit 0 |
| Tests     | `npm test`                           | 0 failed |
| Build     | `npm run build`                      | exit 0; route table printed |
| Smoke     | `npx playwright test smoke.spec.ts`  | 2 passed (needs `npx playwright install chromium` once) |

## Scope

**In scope**:
- `frontend-react/package.json`
- `frontend-react/package-lock.json`

**Out of scope**:
- Renaming `middleware.ts` to `proxy.ts`. It's a separate deprecation cleanup, and doing it in the same change makes a regression hard to attribute.
- Bumping React, Supabase, Tailwind, Vitest or any other direct dependency beyond what `npm audit fix` changes in the lockfile.
- `npm audit fix --force`. It may jump majors.
- Any source file edits. If the build needs code changes, that's a STOP condition.

## Git workflow

- Branch: `advisor/002-nextjs-security-bump`
- Single commit: `fix(deps): bump next to 16.3.6 for critical advisories`
- Do NOT push unless instructed.

## Steps

### Step 1: Bump next

From `frontend-react/`: `npm install next@16.3.6 --save-exact`

**Verify**: `node -p "require('./package.json').dependencies.next"` → `16.3.6`, and
`node -p "require('next/package.json').version"` → `16.3.6`.

### Step 2: Fix the remaining transitive advisories

`npm audit fix` (NOT `--force`).

**Verify**: `npm audit --omit=dev --audit-level=high` → exit 0 (no high/critical). Moderate/low may remain. Note them in your report.

### Step 3: Typecheck, test, build

`npm run typecheck && npm test && npm run build`

**Verify**: all three exit 0. The build prints a route table that includes `/`, `/daily`,
`/daily/[category]`, `/freeplay`, and `/admin` routes, plus a line mentioning middleware
(or "Proxy") in the output.

### Step 4: Smoke test

`npx playwright install chromium` (once), then `npx playwright test smoke.spec.ts`.

**Verify**: `2 passed`.

### Step 5: Manual middleware check (dev server)

`npm run dev`, then in another shell:
`curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" http://localhost:3000/admin`

**Verify**: prints `307` (or `302`/`308`) and a redirect URL that ends in `/?auth_required=1`.
This confirms the admin gate still runs for an unauthenticated request. Stop the dev server afterwards.

## Test plan

- No new tests. The existing unit tests + smoke spec + the Step 5 curl are the regression gate.

## Done criteria

- [ ] `package.json` `"next": "16.3.6"`
- [ ] `npm audit --omit=dev --audit-level=high` exits 0
- [ ] `npm run typecheck && npm test && npm run build` exits 0
- [ ] Smoke spec: 2 passed
- [ ] `/admin` unauthenticated → redirect to `/?auth_required=1`
- [ ] `git status` shows only `package.json` and `package-lock.json` modified
- [ ] `plans/README.md` status row updated

## STOP conditions

- `npm install next@16.3.6` reports a peer-dependency conflict (e.g. with `@supabase/ssr` or `@vercel/*`). Report the conflict; don't use `--legacy-peer-deps`.
- `npm run build` fails, or emits an error (not just a deprecation warning) about `middleware.ts`.
- `npm audit fix` wants to change a direct dependency's major version.
- Step 5 returns `200` for `/admin` without a session. The middleware gate broke; report immediately.
- A newer 16.x than 16.3.6 exists and `npm audit` recommends it instead. Use the version `npm audit` names, and say so in the report.

## Maintenance notes

- Follow-up (deferred, add to `docs/BACKLOG.md` if not already there): rename `src/middleware.ts` → `src/proxy.ts` and export `proxy` instead of `middleware`, per the Next 16 convention.
- `next` is pinned exactly, so security fixes never arrive on their own. Consider Dependabot/Renovate for `frontend-react/`.
