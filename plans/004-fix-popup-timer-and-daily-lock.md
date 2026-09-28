# Plan 004: The INVALID popup auto-dismisses, and dailies started from /daily get locked

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 0baf851..HEAD -- frontend-react/src/components/game/AnimatedScorePopup.tsx frontend-react/src/app/daily frontend-react/src/hooks/useGamePersistence.ts frontend-react/__tests__`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/001-run-frontend-tests-in-ci.md (recommended, not required)
- **Category**: bug
- **Planned at**: commit `0baf851`, 2026-09-28

## Why this matters

Two player-facing bugs in the core game flow, fixed together because both are small and both live in the frontend game shell.

**Bug A: the INVALID popup never auto-dismisses.** `GamePage` (`src/app/page.tsx`) calls
`useCountdown()` (the "next daily in HH:MM:SS" timer), which sets state **every second**.
So the page re-renders every second, and `useGameLoop` returns a brand-new
`handlePopupComplete` function each render, passed to `<AnimatedScorePopup onComplete=…>`.
The popup's dismiss effect lists `onComplete` in its dependency array, so every second the
effect cleans up (clearing the timeout) and restarts it. The INVALID timeout is 1500ms, which
is longer than the 1s tick, so it never fires. The player has to tap to dismiss, and input stays
disabled until they do (`isAnimating = popup !== null`). The 500ms "flashing"/"showing" timers
also get pushed back at random.

**Bug B: dailies started from `/daily` or `/daily/[category]` never get locked on the device.**
Those two pages write `activeGameState` to sessionStorage by hand and leave out
`categorySlug`. After redirecting to `/`, `useGameLoop` restores the game but only sets
`currentCategorySlug` when `saved.categorySlug` exists, and the localStorage daily lock
(`in_progress`/`completed`) is only written when it's set. `/daily/[category]` is the URL
the share text links to. The result: the lobby keeps offering "Play now" for a daily the
player already used, they hit the "one attempt" confirm dialog and then a 409 toast, and
"View result" never appears.

## Current state

- `frontend-react/src/components/game/AnimatedScorePopup.tsx`: props
  `{ scoreValue, result, reason?, onComplete: () => void }`. It already uses refs to guard
  double-fire (lines ~35-38):
  ```tsx
  const completedRef = useRef(false);
  const phaseRef = useRef(phase);
  phaseRef.current = phase;
  ```
  It calls `onComplete()` in a skip handler (~line 56) and in the dismiss effect (lines 109-132):
  ```tsx
  useEffect(() => {
    if (phase === "flashing") { const t = setTimeout(() => setPhase("showing"), 500); return () => clearTimeout(t); }
    if (phase === "showing") {
      const t = setTimeout(() => { if (!completedRef.current) { completedRef.current = true; onComplete(); } }, 500);
      return () => clearTimeout(t);
    }
    if (phase === "invalid") {
      const t = setTimeout(() => { if (!completedRef.current) { completedRef.current = true; onComplete(); } }, 1500);
      return () => clearTimeout(t);
    }
  }, [phase, onComplete]);
  ```
- `frontend-react/src/app/page.tsx:82`: `const timeUntilReset = useCountdown();`, and
  `:266-270` renders `<AnimatedScorePopup … onComplete={onPopupComplete} />`.
- `frontend-react/src/hooks/useGamePersistence.ts`: the canonical helper, already exported:
  ```ts
  function saveGameState(gameId: string, label: string, gameType: GameType, categorySlug?: string)
  ```
  The Free Play pages already use it (`src/app/freeplay/page.tsx:28`), and so does
  `useGameLoop.ts:195` (`saveGameState(game.gameId, label, "daily-challenge", categorySlug)`).
- `frontend-react/src/app/daily/page.tsx:43-50` (inside `handlePlay(slug, label)`):
  ```tsx
      sessionStorage.setItem(
        "activeGameState",
        JSON.stringify({
          gameId: game.gameId,
          label: label,
          gameType: "daily-challenge",
        }),
      );
  ```
- `frontend-react/src/app/daily/[category]/page.tsx:69-73` (inside `handlePlay()`; the slug variable is `categorySlug`, from line 24):
  ```tsx
      sessionStorage.setItem("activeGameState", JSON.stringify({
        gameId: game.gameId,
        label: status.categoryName,
        gameType: "daily-challenge",
      }));
  ```
- `frontend-react/src/hooks/useGameLoop.ts:122-130` (restore). This is the code that needs `categorySlug`:
  ```ts
  if (savedGameType === "daily-challenge" && saved.categorySlug) {
    setCurrentCategorySlug(saved.categorySlug);
    if (game.status === "COMPLETED") setDailyLockCompleted(saved.categorySlug, game.gameId);
    else if ((game.turnCount ?? 0) > 0) setDailyLockInProgress(saved.categorySlug, game.gameId);
  }
  ```
- Tests: Vitest + jsdom + Testing Library, files in `frontend-react/__tests__/*.test.{ts,tsx}`
  (see `vitest.config.ts`). The mocking pattern (`vi.hoisted` + `vi.mock`) is in `__tests__/useGameLoop.test.ts:1-30`.

## Commands you will need

| Purpose | Command (from `frontend-react/`) | Expected |
|---|---|---|
| Typecheck | `npm run typecheck` | exit 0 |
| All tests | `npm test` | 0 failed |
| One file | `npx vitest run __tests__/AnimatedScorePopup.test.tsx` | all pass |

## Suggested executor toolkit

- If available, use the `react-dev-guidelines` skill for the project's React conventions.

## Scope

**In scope**:
- `frontend-react/src/components/game/AnimatedScorePopup.tsx`
- `frontend-react/src/app/daily/page.tsx`
- `frontend-react/src/app/daily/[category]/page.tsx`
- `frontend-react/__tests__/AnimatedScorePopup.test.tsx` (create)
- `frontend-react/__tests__/useGamePersistence.test.ts` (create)

**Out of scope** (known issues that belong in separate changes):
- Moving `useCountdown` out of `GamePage`. The ref fix makes the popup immune to parent re-renders, which is the root cause.
- Refactoring the `/daily` pages to route through `useGameLoop.startDailyChallenge`. Nice-to-have dedup, but bigger.
- "Play again" sending the label as the slug (`page.tsx:88-90`), stale lobby lock state after exit, the entity-cache poisoning in `entityCache.ts`, and the hardcoded `"freeplay"` in `useGameLoop.ts:153,180`. All real, all separate.
- `useGameLoop.ts`: no change needed.

## Git workflow

- Branch: `advisor/004-popup-timer-daily-lock`
- Two commits: `fix: keep score popup dismiss timer stable across parent re-renders` and `fix: save categorySlug when starting a daily from /daily pages`
- Do NOT push unless instructed.

## Steps

### Step 1: Write a failing test for Bug A

Create `frontend-react/__tests__/AnimatedScorePopup.test.tsx`:

```tsx
import { describe, it, expect, vi, afterEach } from "vitest";
import { render, act } from "@testing-library/react";
import AnimatedScorePopup from "@/components/game/AnimatedScorePopup";

afterEach(() => vi.useRealTimers());

describe("AnimatedScorePopup — dismiss timer", () => {
  it("auto-dismisses INVALID after 1500ms even when the parent re-renders every second with a new onComplete", () => {
    vi.useFakeTimers();
    const calls: number[] = [];
    // A new function identity each render, like useGameLoop's handlePopupComplete
    const make = (n: number) => () => calls.push(n);

    const { rerender } = render(<AnimatedScorePopup scoreValue={0} result="INVALID" onComplete={make(0)} />);
    act(() => { vi.advanceTimersByTime(1000); });
    rerender(<AnimatedScorePopup scoreValue={0} result="INVALID" onComplete={make(1)} />);
    act(() => { vi.advanceTimersByTime(600); });

    expect(calls).toHaveLength(1);          // fired once, at ~1500ms from mount
    expect(calls[0]).toBe(1);               // and called the LATEST callback
  });
});
```

If `window.matchMedia` is undefined in jsdom and the component throws, check
`vitest.setup.ts`. If it doesn't stub `matchMedia`, add at the top of this test file:
`window.matchMedia = window.matchMedia || ((() => ({ matches: false, addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {} })) as any);`

**Verify**: `npx vitest run __tests__/AnimatedScorePopup.test.tsx` → **FAILS** (`calls` has length 0). If it passes before the fix, STOP (see STOP conditions).

### Step 2: Hold `onComplete` in a ref

In `AnimatedScorePopup.tsx`, next to the existing refs, add:

```tsx
// Parent re-renders (e.g. the 1s countdown on GamePage) pass a new onComplete
// each time; reading it through a ref keeps the dismiss timers from restarting.
const onCompleteRef = useRef(onComplete);
onCompleteRef.current = onComplete;
```

Replace **every** `onComplete()` call inside the component (the skip handler and the two in
the dismiss effect) with `onCompleteRef.current()`. Change the dismiss effect's dependency
array from `[phase, onComplete]` to `[phase]`. If the linter comment style requires it, add
`// eslint-disable-next-line react-hooks/exhaustive-deps` above the array, matching the existing one at ~line 105.

**Verify**: `grep -n "onComplete()" src/components/game/AnimatedScorePopup.tsx` → no matches;
`npx vitest run __tests__/AnimatedScorePopup.test.tsx` → passes; `npm run typecheck` → exit 0.

### Step 3: Save `categorySlug` from both /daily pages

In `src/app/daily/page.tsx`, add `import { saveGameState } from "@/hooks/useGamePersistence";`
and replace the whole `sessionStorage.setItem("activeGameState", …)` block with:

```tsx
      saveGameState(game.gameId, label, "daily-challenge", slug);
```

In `src/app/daily/[category]/page.tsx`, add the same import and replace its block with:

```tsx
      saveGameState(game.gameId, status.categoryName, "daily-challenge", categorySlug);
```

**Verify**: `grep -rn '"activeGameState"' src/app` → no matches;
`grep -rn "saveGameState(" src/app/daily` → 2 matches, both ending with a slug argument; `npm run typecheck` → exit 0.

### Step 4: Lock in the persistence contract with a test

Create `frontend-react/__tests__/useGamePersistence.test.ts`:
- `saveGameState("g1", "Geography", "daily-challenge", "geography")` followed by `loadSavedGameState()` returns `{ gameId: "g1", label: "Geography", gameType: "daily-challenge", categorySlug: "geography" }`.
- The same round-trip without a slug returns `categorySlug: undefined`.
- `clearSavedGameState()` then `loadSavedGameState()` returns `null`.

Use jsdom's real `sessionStorage` (call `sessionStorage.clear()` in `beforeEach`). No mocks needed.

**Verify**: `npx vitest run __tests__/useGamePersistence.test.ts` → 3 passed.

### Step 5: Full suite

`npm run typecheck && npm test`

**Verify**: exit 0; total tests = baseline (135) + 4 new, 0 failed.

## Test plan

- `AnimatedScorePopup.test.tsx`: reproduces Bug A with fake timers and a re-rendering parent (Step 1).
- `useGamePersistence.test.ts`: slug round-trip (Step 4).
- Manual (optional, needs backend running): open `/daily`, start a challenge, make one answer, go back to `/`. The hero card shows "In progress"/"Resume", not "Play now". Submit an obviously wrong name: the INVALID popup closes by itself after ~1.5s.

## Done criteria

- [ ] `grep -n "onComplete()" frontend-react/src/components/game/AnimatedScorePopup.tsx` → no matches
- [ ] `grep -rn '"activeGameState"' frontend-react/src/app` → no matches
- [ ] `npm run typecheck && npm test` → exit 0, ≥139 tests passed
- [ ] `git status` shows only in-scope files
- [ ] `plans/README.md` status row updated

## STOP conditions

- The Step 1 test **passes before** the Step 2 fix. The diagnosis is wrong (e.g. the component is memoized upstream). Report instead of changing code.
- `AnimatedScorePopup` has gained other props or callbacks used inside effects that would need the same treatment. List them and ask.
- `saveGameState`'s signature differs from `(gameId, label, gameType, categorySlug?)`.
- Fixing the test environment would need changes to `vitest.setup.ts` beyond the one-line `matchMedia` stub described in Step 1.

## Maintenance notes

- Any new callback prop used inside a timer effect in this component should also go through a ref, or parent re-renders will bring this bug back.
- The two `/daily` pages still duplicate the start/error-parsing logic that lives in `GameApiClient`. That duplication is how Bug B happened. Consolidating them into `useGameLoop.startDailyChallenge` is a good follow-up. Log it in `docs/BACKLOG.md`.
