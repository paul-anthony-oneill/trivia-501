# Plan 001: Frontend unit tests and typecheck run in CI and in the pre-commit hook

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 0baf851..HEAD -- .github/workflows/frontend-ci.yml .husky frontend-react/.husky frontend-react/package.json`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none (this is the safety net every other frontend plan relies on)
- **Category**: tests / dx
- **Planned at**: commit `0baf851`, 2026-09-28

## Why this matters

The frontend has 135 passing Vitest tests (7 files in `frontend-react/__tests__/`)
and a `typecheck` script, but **nothing ever runs them automatically**. CI only runs
`next build` and a 2-test Playwright smoke spec. The husky pre-commit hooks exist on
disk but were never installed, because `git config core.hooksPath` still points at
`.git/hooks`, which only contains `*.sample` files. That means a regression in the game
loop, share-grid encoding or auth context can merge unnoticed. Once this plan lands,
every PR that touches `frontend-react/**` fails on a broken test or type error.

## Current state

- `.github/workflows/frontend-ci.yml` has two jobs. The `build` job runs only:
  ```yaml
        - run: npm ci
        - run: npm run build
  ```
  The `e2e` job runs `npx playwright test smoke.spec.ts`.
- `frontend-react/package.json` has these scripts (verified):
  ```json
  "test": "vitest run",
  "typecheck": "tsc --noEmit --project tsconfig.typecheck.json",
  "prepare": "husky"
  ```
  `tsconfig.typecheck.json` extends `tsconfig.json` and excludes only `node_modules`
  and `.next`, so it also type-checks `__tests__/` and `e2e/`. `next build` does not.
- There are **two** husky hook dirs:
  - `/.husky/pre-commit` (repo root, mode 755): `cd frontend-react && npx lint-staged && npm run typecheck && npm run test`
  - `/frontend-react/.husky/pre-commit` (mode 644, not executable): `npm test`
- `git config core.hooksPath` returns `.git/hooks`, so neither hook runs.
- Verified locally at 0baf851: `npm run typecheck` exits 0, and `npx vitest run` prints
  `Test Files 7 passed (7)  Tests 135 passed (135)`.
- Commit style (from `git log`): `fix: <summary> (#NN)` or `refactor: ...`, i.e. conventional commits.

## Commands you will need

| Purpose   | Command (run from `frontend-react/`) | Expected on success |
|-----------|--------------------------------------|---------------------|
| Install   | `npm ci`                             | exit 0              |
| Typecheck | `npm run typecheck`                  | exit 0, no output after the header |
| Tests     | `npm test`                           | `Tests  135 passed` (or more) |
| Build     | `npm run build`                      | exit 0              |

## Scope

**In scope** (the only files you should modify):
- `.github/workflows/frontend-ci.yml`
- `frontend-react/package.json` (the `prepare` script only)
- `frontend-react/.husky/pre-commit` (delete)
- `.husky/pre-commit` (edit: drop `npx lint-staged`)

**Out of scope**:
- Adding ESLint. There's no ESLint config in the repo, which is a separate decision.
- Promoting `e2e/full-game.spec.ts` into CI (it needs a live backend).
- Changing any test or source file. If a test fails in CI, that's a STOP condition, not something to fix here.
- `backend-ci.yml`.

## Git workflow

- Branch: `advisor/001-frontend-tests-in-ci`
- One commit per step is fine. Example message: `ci: run vitest and typecheck in frontend CI`
- Do NOT push or open a PR unless the operator told you to.

## Steps

### Step 1: Add typecheck and unit-test steps to the CI build job

In `.github/workflows/frontend-ci.yml`, in the `build` job, insert these two steps
between `- run: npm ci` and `- run: npm run build`:

```yaml
      - run: npm run typecheck
      - run: npm test
```

Keep the indentation identical to the existing `- run:` lines (6 spaces).

**Verify**: `python3 -c "import yaml,sys;d=yaml.safe_load(open('.github/workflows/frontend-ci.yml'));print([s.get('run') for s in d['jobs']['build']['steps']])"`
→ prints a list containing, in order, `'npm ci'`, `'npm run typecheck'`, `'npm test'`, `'npm run build'`.

### Step 2: Confirm the steps pass locally the way CI will run them

From `frontend-react/`: `npm ci && npm run typecheck && npm test`

**Verify**: exit 0, and the Vitest summary shows `Tests  135 passed` (or a higher number with 0 failed).

### Step 3: Make husky install from the repo root and remove the duplicate hook dir

The `.git` directory lives at the repo root, but `npm install` runs `prepare` inside
`frontend-react/`, so husky never installs. Change the `prepare` script in
`frontend-react/package.json` to:

```json
"prepare": "cd .. && husky"
```

With no argument, husky 9 installs `.husky/` from the current directory (the repo root)
and sets `core.hooksPath` to `.husky/_`. Then delete the duplicate
`frontend-react/.husky/` directory: `git rm -r frontend-react/.husky`.

Then edit the root `.husky/pre-commit`. It currently calls `npx lint-staged`, but the repo
has **no lint-staged configuration** (no `lint-staged` key in `package.json`, no
`.lintstagedrc*` file; verified at 0baf851), so lint-staged would exit non-zero and block
every commit. Replace the file's contents with exactly:

```sh
cd frontend-react && npm run typecheck && npm test
```

Leave the `lint-staged` devDependency where it is; removing it is out of scope.

**Verify**:
1. From `frontend-react/`: `npm run prepare` → exit 0.
2. From the repo root: `git config core.hooksPath` → prints `.husky/_`.
3. `ls .husky/_/pre-commit` → the file exists.

### Step 4: Prove the hook fires

From the repo root, make a throwaway change and try to commit it:
```bash
echo "// hook check" >> frontend-react/src/lib/country.ts
git add frontend-react/src/lib/country.ts
git commit -m "tmp: hook check" --dry-run   # dry-run does NOT trigger hooks; skip if unsure
git commit -m "tmp: hook check"
```
The commit must print typecheck and vitest output before it completes.
Then undo it: `git reset --soft HEAD~1 && git restore --staged frontend-react/src/lib/country.ts && git checkout -- frontend-react/src/lib/country.ts`.

If `frontend-react/src/lib/country.ts` doesn't exist, use any `.ts` file under `frontend-react/src/lib/`.

**Verify**: `git status --short frontend-react/src` → empty, and `git log -1 --format=%s` is NOT `tmp: hook check`.

## Test plan

- No new tests. This plan wires up existing ones.
- CI verification: when the branch is pushed (only if the operator asks), the `Frontend CI / build` job shows `npm run typecheck` and `npm test` steps, both green.

## Done criteria

- [ ] `.github/workflows/frontend-ci.yml` `build` job contains `npm run typecheck` and `npm test` before `npm run build`
- [ ] `frontend-react/package.json` `prepare` is `cd .. && husky`
- [ ] `test -d frontend-react/.husky` fails (the directory is gone)
- [ ] `git config core.hooksPath` prints `.husky/_`
- [ ] `npm run typecheck && npm test` in `frontend-react/` exits 0
- [ ] `git status` shows only in-scope files modified
- [ ] `plans/README.md` status row updated

## STOP conditions

- `npm test` or `npm run typecheck` fails at Step 2 on an unmodified checkout. That's a pre-existing breakage; report which test fails.
- `npm run prepare` errors with something like "not a git repository" or "`.git` can't be found".
- The hook in Step 4 doesn't run at all (no typecheck/vitest output). Report `git config core.hooksPath` and `ls -la .husky/_`.

## Maintenance notes

- New contributors' `npm install` now also sets `core.hooksPath`. Anyone who wants to skip hooks uses `git commit --no-verify`.
- If ESLint is added later, add `npm run lint` to the same CI job.
- Vercel runs `npm install` too. `cd .. && husky` is harmless there: husky 9 exits 0 when no `.git` is present. If a Vercel build fails at `prepare`, change it to `cd .. && husky || true`.
