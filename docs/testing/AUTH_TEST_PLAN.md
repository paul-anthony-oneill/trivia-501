# Auth & Sign-In Test Plan

Last updated: 2026-06-16

## Current Coverage (Baseline)

| Component | Coverage |
|---|---|
| `apiFetch` token injection | 6 tests (bearer injection, header preservation, passthrough) |
| Controller `@WithMockUser` | 4 test classes (admin CRUD, free-play game flow) |
| `OptionalJwtFilter` | **None** |
| `SecurityConfig` URL rules | **None** |
| `JwtConfig` | **None** |
| `RateLimitFilter` | **None** |
| `PlayerProfileService` | **None** |
| `AuthContext` | **None** |
| `LoginButton` | **None** |
| `middleware.ts` | **None** |
| `auth/callback/route.ts` | **None** |

---

## 1. Backend Automated Tests

### 1.1 `OptionalJwtFilterTest` — P0 (3-4h)

The core auth filter has 5 distinct branches. Zero coverage today.

**JWT path — happy:**
- Valid HMAC-SHA256 Supabase JWT sets `JwtAuthenticationToken` in context, sets `X-Auth-Type` = `"jwt"`
- JWT with `app_metadata.role = "admin"` grants `ROLE_ADMIN` + `ROLE_USER`
- JWT without admin claim grants `ROLE_USER` only
- `principal.getName()` returns the JWT `sub` claim (Supabase user UUID)

**JWT path — rejection:**
- Supabase anonymous JWT (`role: "anonymous"`) is rejected, falls back to cookie anon auth (sets `UsernamePasswordAuthenticationToken`)
- Expired JWT with `SUPABASE_JWT_SECRET` configured returns 401 + `{"error":"Invalid or expired token"}`
- Malformed Bearer token returns 401 when secret configured; anonymous fallback when not
- JWT with missing `role` claim: `JwtAuthenticationConverter` produces empty authorities, caught as `AuthenticationException`, returns 401 or falls back

**Existing auth passthrough:**
- Real `Authentication` already in context (not anonymous) → filter skips entirely
- `AnonymousAuthenticationToken` in context → filter replaces it

**Cookie path — creation:**
- No `X-Anonymous-Id` cookie, no JWT → creates new UUID, sets `UsernamePasswordAuthenticationToken`, sets `X-Auth-Type` = `"anonymous"`
- Cookie has `HttpOnly`, `Secure` (on HTTPS), `SameSite=Lax`, `Path=/`, `MaxAge=86400`

**Cookie path — reuse:**
- Existing `X-Anonymous-Id` cookie, no JWT → reuses same UUID, re-issues cookie with sliding 24h expiration

**Cookie rotation:**
- `X-Rotate-Anonymous-Id` = `"true"` on anonymous request → after chain completes, new UUID cookie issued, principal updated in context
- No rotation attribute → same cookie re-issued (sliding expiry only)
- Rotation on JWT-authenticated request → no-op

**Edge cases:**
- HTTP request → `Secure=false` on cookie; HTTPS → `Secure=true`

### 1.2 `SecurityConfigTest` — P0 (2-3h)

URL-level access matrix. Use `@WebMvcTest` slices or programmatic `SecurityFilterChain` assertions.

**permitAll endpoints — spot-check 3 representative paths (all from one `.permitAll()` chain; coverage of one implies coverage of all):**

| Endpoint | Method |
|---|---|
| `/api/categories` | GET |
| `/api/daily-challenge/status` | GET |
| `/actuator/health` | GET |

**Authenticated-only (POST daily-challenge — no auth → 401, with auth → 2xx/4xx):**

| Endpoint | Method |
|---|---|
| `/api/daily-challenge/slug/start` | POST |

**ROLE_USER/ADMIN only (freeplay — anon/JWT user/JWT admin all get 2xx; no-auth gets 401):**

| Endpoint | Method |
|---|---|
| `/api/freeplay/start` | POST |

**ROLE_ADMIN only — test one controller thoroughly, spot-check one other (identical `hasRole('ADMIN')` on all 5):**

| Endpoint | Method | Anon | JWT user | JWT admin |
|---|---|---|---|---|
| `/api/admin/categories` | GET | 401 | 403 | 2xx |
| `/api/admin/questions` | GET | — | — | 2xx |

**Other:**
- Unmapped URL (e.g., `/api/unknown`) → 401 without any auth
- No `JSESSIONID` in responses (stateless session policy)
- `@PreAuthorize("hasRole('ADMIN')")` is active on admin controller classes

### 1.3 `JwtConfigTest` — P1 (1h)

- `SUPABASE_JWT_SECRET` set → `JwtDecoder` bean decodes valid HMAC-SHA256 tokens
- `SUPABASE_JWT_SECRET` not set / empty → no-op decoder throws `JwtException` on any token
- `role: "authenticated"` claim → authority list contains `ROLE_USER`
- `role: "authenticated"` + `app_metadata.role: "admin"` → `ROLE_USER` + `ROLE_ADMIN`
- `role: "something-else"` → no `ROLE_USER` granted (only `"authenticated"` maps)
- Decoder rejects tokens signed with wrong secret → `JwtException`

### 1.4 `RateLimitFilterTest` — P1 (1h)

- Anonymous (`X-Auth-Type` = `"anonymous"`): 11th request in 60s window returns 429 + `{"error":"Too many requests"}`
- JWT (`X-Auth-Type` = `"jwt"`): 101st request in 60s window returns 429
- Different IPs → independent counters
- `/actuator/health` → always passes through (no rate limiting)
- Window expiry → count resets after 60s
- Filter excluded from test profile (`@Profile("!test")`)

### 1.5 `PlayerProfileServiceTest` — P1 (1h)

- `isAuthenticated()` true when `JwtAuthenticationToken` in SecurityContext
- `isAuthenticated()` false when `UsernamePasswordAuthenticationToken` (anonymous)
- `ensureProfile` creates new profile row for first-time JWT user
- `ensureProfile` updates `displayName` / `avatarUrl` / `lastActiveAt` on subsequent visits
- `ensureProfile` extracts `full_name` and `avatar_url` from JWT `user_metadata`
- `ensureProfile` returns `Optional.empty()` for anonymous user (silent no-op)
- `recordGameCompleted` increments `gamesPlayed`, `gamesWon` (when win), `totalScore`, updates `bestScore`
- `recordGameCompleted` returns silently for anonymous user

---

## 2. Frontend Automated Tests

### 2.1 `AuthContext.test.tsx` — P0 (2-3h)

Render `AuthProvider` with mocked Supabase client via `vi.mock("@/utils/supabase/client")`.

**Initial state:**
- `loading = true` during initial mount
- `user = null`, `session = null` when `getSession` returns no session
- `loading = false` after `getSession` resolves
- `backendConfirmed = false`, `profile = null` initially

**Session population:**
- `getSession` returns session → `user` and `session` populated
- `onAuthStateChange` `SIGNED_IN` event → state updates with new user/session
- `onAuthStateChange` `SIGNED_OUT` event → user/session become null, `backendConfirmed` becomes false

**Backend confirmation:**
- Session present + `/api/freeplay/profile` returns 200 → `backendConfirmed = true`, `profile` populated
- Session present + `/api/freeplay/profile` returns 404 → `backendConfirmed = false`, `profile = null`
- Network error on profile fetch → `backendConfirmed` keeps previous value (no flip to false on transient failure)

**Actions:**
- `signInWithGoogle()` calls `supabase.auth.signInWithOAuth` with `provider: "google"` and correct `redirectTo`
- `signOut()` calls `supabase.auth.signOut()`

**Cleanup:**
- Unmount unsubscribes from `onAuthStateChange`

### 2.2 `LoginButton.test.tsx` — P1 (1h)

- **Loading**: Shows disabled "Loading…" button
- **Unauthenticated**: Renders "Sign in with Google" button; clicking calls `signInWithGoogle`
- **Authenticated, backend confirmed**: Shows avatar image + display name (or email fallback) + "Sign out" button; no gold dot
- **Authenticated, backend NOT confirmed**: Gold dot indicator present with correct `title` text; avatar has gold ring
- **Sign out click**: Calls `signOut`
- **No avatar_url in metadata**: Avatar `<img>` not rendered (no broken image)
- **No full_name in metadata**: Falls back to `user.email` for display text

### 2.3 `middleware.test.ts` — P1 (1.5h)

Construct `NextRequest` objects to test the Edge Middleware:

- `/admin` + no user → redirects to `/?auth_required=1`
- `/admin/questions` + no user → redirects to `/?auth_required=1`
- `/admin` + user without `app_metadata.role = "admin"` → redirects to `/`
- `/admin` + user with `app_metadata.role = "admin"` → passes through (no redirect)
- `/daily` → passes through regardless of auth state
- `/` → passes through regardless of auth state
- Static asset paths excluded by matcher → middleware not invoked

### 2.4 `auth/callback/route.test.ts` — P2 (1h)

- Valid `code` param → `exchangeCodeForSession` called, redirects to `next` param value
- Valid `code` param, no `next` param → redirects to `/`
- `exchangeCodeForSession` returns error → redirects to `/?auth_error=1`
- Missing `code` param → redirects to `/?auth_error=1`

### 2.5 `apiFetch.test.ts` additions — P2 (0.5h)

- Multiple concurrent calls → each independently retrieves session (no shared stale state)

---

## 3. Integration Tests

### 3.1 Backend: Full Auth Filter Chain — P2 (2h)

`@SpringBootTest` (not a slice) with real `OptionalJwtFilter` + `SecurityConfig` wired in. Use a test-only JWT secret:

- Anonymous full game: start → submit answers → checkout → verify cookie rotated → share accessible without auth
- JWT full game: same flow with real JWT → verify profile upserted after game
- Identity from `Principal.getName()` — verify controllers never read identity from `@RequestParam`
- Game complete sets `X-Rotate-Anonymous-Id` request attribute to `"true"` on CHECKOUT
- Anonymous hits admin endpoint → 403 (they have ROLE_USER but not ROLE_ADMIN)
- No auth hits admin endpoint → 401

---

## 4. E2E Tests (Playwright) — P3

### 4.1 Anonymous Game Flow (3h)
1. Open app (fresh context, no cookies)
2. Verify daily challenge cards load
3. Click into a challenge → game starts
4. Submit a valid answer → score deducts in UI
5. Checkout → win overlay shown, share button present
6. Reload page → verify game state

### 4.2 Google Sign-In Flow (2h — requires test Google account)
1. Click "Sign in with Google"
2. Complete OAuth (use Playwright auth state for Google test account)
3. Verify avatar + name appear in header, gold dot absent
4. Play a game → verify profile persists
5. Sign out → verify anonymous fallback

### 4.3 Admin Route Protection (1h)
1. Navigate to `/admin` as anonymous → redirected to `/?auth_required=1`
2. Sign in as non-admin user → navigate to `/admin` → redirected to `/`
3. Sign in as admin → `/admin` loads successfully

---

## 5. Manual Test Runbook

Run before each production deploy and after any auth-related change. Only scenarios that can't be automated (real OAuth, infrastructure state, network conditions).

| # | Scenario | Steps | Expected |
|---|---|---|---|
| M1 | Google sign-in happy path | Click sign in, complete OAuth in private window | Avatar + name shown, gold dot absent, game works, profile saved |
| M2 | Google sign-in cancelled | Cancel at Google consent screen | Returned to app, still anonymous, game works |
| M3 | Backend JWT_SECRET missing | Sign in with backend missing the secret | Gold dot visible, games still playable via anon fallback |
| M4 | Backend down | Stop backend, use frontend | Graceful error state, not blank page |
| M5 | Fresh anonymous canary | Open app in private window (no sign in) | Anon cookie set, daily challenge cards load, game plays |

---

## 6. Prioritization Summary

| Priority | Tests | Total Effort |
|---|---|---|
| **P0** | `OptionalJwtFilterTest`, `SecurityConfigTest`, `AuthContext.test.tsx` | 6-9h |
| **P1** | `JwtConfigTest`, `RateLimitFilterTest`, `LoginButton.test.tsx`, `middleware.test.ts`, `PlayerProfileServiceTest` | 5-6h |
| **P2** | `auth/callback/route.test.ts`, backend integration, `apiFetch` additions | 3-4h |
| **P3** | E2E Playwright tests (anon game, Google sign-in, admin routes) | 6h |
| **Manual** | 5-scenario runbook | 15min per run |

**Total automated**: ~20-25h. P0+P1 (~11-15h) covers all critical paths.
