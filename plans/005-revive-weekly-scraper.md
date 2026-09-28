# Plan 005: The weekly data refresh runs green and refreshes question answers

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 0baf851..HEAD -- .github/workflows/scraper-scheduled.yml trivia-501-scraper/config.py backend/src/main/java/com/trivia501/scheduler backend/src/main/java/com/trivia501/service/QuestionMaterializerService.java`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED. The scraper writes straight to the production Supabase database.
- **Depends on**: none
- **Category**: bug / dx
- **Planned at**: commit `0baf851`, 2026-09-28

## Why this matters

`.github/workflows/scraper-scheduled.yml` is supposed to refresh current-season football stats
every Monday. It has **failed on every run** (the last 5 scheduled runs, 2026-08-10 to 2026-09-07,
all failed in ~55s with `sqlalchemy.exc.ArgumentError: Could not parse SQLAlchemy URL`), and GitHub
has now **disabled it for inactivity** (`gh workflow list --all` → `disabled_inactivity`). Even with the
URL fixed, steps 2 and 3 run dead scripts, the season defaults to last year, and nothing re-materializes
questions afterwards. So scraped stats would never reach the `answers` table players are scored against.
A daily trivia game with stale answers loses players' trust fast.

## Current state

- `.github/workflows/scraper-scheduled.yml` (full file, 40 lines): cron `"17 3 * * 1"` +
  `workflow_dispatch`; one job with `actions/checkout@v4`, `actions/setup-python@v5` (3.12),
  `pip install -r requirements.txt`, then three steps, each with
  `env: DATABASE_URL: ${{ secrets.SUPABASE_DB_URL }}`:
  1. `python scrape_current_season.py`
  2. `python init_questions_v2.py`
  3. `python populate_answers_v2.py`
- Repo secrets (`gh secret list`): only `FLY_API_TOKEN`. **`SUPABASE_DB_URL` doesn't exist.**
  The job has no `environment:` key, so environment-scoped secrets don't apply either.
- `trivia-501-scraper/populate_answers_v2.py` is deprecated and exits via `raise SystemExit(...)`.
- `trivia-501-scraper/init_questions_v2.py` uses `database/models_v4.py`, which references
  `questions.is_active`. That column was dropped by backend migration `V7__question_lifecycle_and_materialization.sql`.
  Questions are now created and materialized by the Java backend, not by Python.
- `trivia-501-scraper/config.py:24-26`: `database_url` reads env `DATABASE_URL`. **It has a
  hardcoded local-dev default URL with a password. Do not copy that value anywhere.**
  `config.py:67`: `current_season: str = Field(default="2025-2026", env="CURRENT_SEASON")`.
- `trivia-501-scraper/scrape_current_season.py` supports `--season YYYY-YYYY`, `--leagues`, and
  `--dry-run`, and writes to `player_season_stints`. Its docstring says to trigger
  `POST /api/admin/questions/rematerialize-stale` afterwards, **but that endpoint does not exist**.
  The only admin endpoint is per-question `POST /api/admin/questions/{id}/rematerialize`
  (`AdminQuestionController.java:101`).
- `backend/.../service/QuestionMaterializerService.java`:
  - `public int materialize(Question question)` (line 98, `@Transactional`) recomputes and
    upserts answers for one question. It throws `IllegalStateException` if no materializer is
    registered (e.g. hand-curated Geography/Film questions).
  - `public int rematerializeAll(List<Question> questions)` (line 127) has **no callers**. It
    wraps the whole loop in one `@Transactional`. Don't use it: one SQL failure would abort the Postgres transaction for every question after it.
- `QuestionRepository.findByStatus(String status)` exists; the active status is `Question.STATUS_ACTIVE` (`"active"`).
- Scheduler pattern to copy: `backend/.../scheduler/DailyChallengeScheduler.java` (a `@Component`,
  `@Slf4j`, constructor injection, `@Scheduled(cron = "0 5 0 * * *")`). Scheduling is enabled
  (`@EnableScheduling` on `Trivia501Application`). Spring cron is **6 fields** (seconds first) and uses the JVM zone, which is UTC on Fly.
- SQLAlchemy needs a `postgresql://user:pass@host:port/db` URL, **not** the JDBC
  `jdbc:postgresql://…` form stored in the Fly `DB_URL` secret.

## Commands you will need

| Purpose | Command | Expected |
|---|---|---|
| Workflow YAML check | `python3 -c "import yaml;yaml.safe_load(open('.github/workflows/scraper-scheduled.yml'))"` | exit 0 |
| Backend compile | `cd backend && mvn -B -q compile` | exit 0 |
| Backend unit test | `cd backend && mvn -B test -Dtest=AnswerRematerializationSchedulerTest` | BUILD SUCCESS |
| Trigger workflow | `gh workflow run scraper-scheduled.yml -f dry_run=true` | run queued |
| Watch run | `gh run list --workflow=scraper-scheduled.yml -L 1` | `completed success` |

## Scope

**In scope**:
- `.github/workflows/scraper-scheduled.yml`
- `backend/src/main/java/com/trivia501/scheduler/AnswerRematerializationScheduler.java` (create)
- `backend/src/test/java/com/trivia501/scheduler/AnswerRematerializationSchedulerTest.java` (create)
- `trivia-501-scraper/scrape_current_season.py` (docstring only: fix the non-existent endpoint reference)
- `docs/BACKLOG.md` (add one entry, Step 6)

**Out of scope**:
- Deleting `init_questions_v2.py`, `populate_answers_v2.py`, `models_v4.py`, the broken `Dockerfile`, or unused requirements. It's worth doing, but as a separate cleanup.
- `config.py`'s hardcoded default URL (separate hardening; changing it can break local dev).
- `rematerializeAll`: leave it; don't call it.
- Changing FBref scraping logic.

## Git workflow

- Branch: `advisor/005-revive-weekly-scraper`
- Commits: `ci(scraper): fix weekly refresh workflow`, then `feat: weekly answer re-materialization job`
- Do NOT push unless instructed. **Steps 1 and 5 need the human operator** (secrets and prod runs).

## Steps

### Step 1 (OPERATOR, not the executor): create the secret

The repo owner must create a repo secret named `SUPABASE_DB_URL` holding the Supabase
**direct** connection (port 5432, not the 6543 pgBouncer pooler) in SQLAlchemy form:
`postgresql://<user>:<password>@<host>:5432/postgres?sslmode=require`.
Command: `gh secret set SUPABASE_DB_URL` (it prompts for the value, so the value never appears in shell history).

Note: the project memory says the DB password was exposed earlier and needs rotating. If it
hasn't been rotated yet, rotate it first and update the Fly `DB_PASSWORD` secret at the same time.

**Verify**: `gh secret list` → includes `SUPABASE_DB_URL`.
**Executor**: if this secret is missing, finish Steps 2-4 and 6, then report that Step 5 is blocked on the operator.

### Step 2: Rewrite the workflow

Replace `.github/workflows/scraper-scheduled.yml` with:

```yaml
name: Scraper — Weekly Data Refresh

on:
  schedule:
    # Every Monday at 03:17 UTC (off-peak, avoids :00/:30 stampede)
    - cron: "17 3 * * 1"
  workflow_dispatch:
    inputs:
      dry_run:
        description: "Parse only, don't write to the database"
        type: boolean
        default: true

jobs:
  scrape:
    runs-on: ubuntu-latest
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v5

      - uses: actions/setup-python@v6
        with:
          python-version: "3.12"
          cache: pip
          cache-dependency-path: trivia-501-scraper/requirements.txt

      - name: Install dependencies
        run: pip install -r requirements.txt
        working-directory: trivia-501-scraper

      # Season label rolls over on 1 July: Jul 2026 → "2026-2027".
      - name: Compute current season
        id: season
        run: |
          y=$(date -u +%Y); m=$(date -u +%-m)
          if [ "$m" -ge 7 ]; then echo "season=$y-$((y+1))" >> "$GITHUB_OUTPUT"
          else echo "season=$((y-1))-$y" >> "$GITHUB_OUTPUT"; fi

      # Answers are re-materialized by the backend's AnswerRematerializationScheduler
      # (Mondays 05:17 UTC), so this workflow only has to write player_season_stints.
      - name: Scrape current season data
        run: python scrape_current_season.py --season "${{ steps.season.outputs.season }}" ${{ inputs.dry_run && '--dry-run' || '' }}
        working-directory: trivia-501-scraper
        env:
          DATABASE_URL: ${{ secrets.SUPABASE_DB_URL }}
```

Notes: scheduled runs have no `inputs`, so `inputs.dry_run` is empty/false and scheduled runs
write for real. Manual runs default to dry-run. `checkout@v5` matches the other workflows; `setup-python@v6` moves off the deprecated Node 20 runtime.

**Verify**: the YAML check command → exit 0; `grep -c "init_questions_v2\|populate_answers_v2" .github/workflows/scraper-scheduled.yml` → `0`.

### Step 3: Add the backend re-materialization job

Create `backend/src/main/java/com/trivia501/scheduler/AnswerRematerializationScheduler.java`,
following `DailyChallengeScheduler`'s style (constructor injection, `@Slf4j`, Javadoc):

```java
/**
 * Re-materializes answers for every active question once a week, after the
 * Python scraper's Monday 03:17 UTC run has refreshed player_season_stints.
 *
 * <p>Each question is materialized in its own transaction (materialize() is
 * @Transactional on a separate bean). A failure on one question — including
 * hand-curated questions with no registered materializer — is logged and
 * skipped, so it can't abort the batch.
 */
@Component
@Slf4j
public class AnswerRematerializationScheduler {
    private final QuestionRepository questionRepository;
    private final QuestionMaterializerService materializerService;
    // constructor …

    @Scheduled(cron = "0 17 5 * * MON")
    public void rematerializeActiveQuestions() {
        List<Question> active = questionRepository.findByStatus(Question.STATUS_ACTIVE);
        int ok = 0, skipped = 0, upserted = 0;
        for (Question q : active) {
            try {
                upserted += materializerService.materialize(q);
                ok++;
            } catch (Exception e) {
                skipped++;
                log.debug("Skipped rematerializing question {}: {}", q.getId(), e.getMessage());
            }
        }
        log.info("Weekly rematerialization: {} questions refreshed, {} skipped, {} answer rows upserted",
                ok, skipped, upserted);
    }
}
```

Do **not** annotate the scheduler method with `@Transactional`.

**Verify**: `cd backend && mvn -B -q compile` → exit 0.

### Step 4: Unit-test the job

Create `backend/src/test/java/com/trivia501/scheduler/AnswerRematerializationSchedulerTest.java`,
using Mockito the same way `GameCleanupSchedulerTest.java` in the same folder does (open it and copy its setup style):
- Three active questions; `materialize` returns 10 for q1, throws `IllegalStateException` for q2, and returns 5 for q3. Verify `materialize` was called 3 times (the failure didn't stop the loop).
- An empty active list means `materialize` is never called.

**Verify**: `mvn -B test -Dtest=AnswerRematerializationSchedulerTest` → `Tests run: 2, Failures: 0`.

### Step 5 (OPERATOR-assisted): re-enable and dry-run

Only once Step 1 is done and the branch is merged to `master` (workflow_dispatch runs the default branch's workflow file):
```bash
gh workflow enable scraper-scheduled.yml
gh workflow run scraper-scheduled.yml -f dry_run=true
gh run watch $(gh run list --workflow=scraper-scheduled.yml -L 1 --json databaseId -q '.[0].databaseId')
```

**Verify**: the run finishes `success`, and its log shows the computed season (`2026-2027`) plus per-league row counts greater than 0. Then run once for real (`-f dry_run=false`) and check it succeeds.

### Step 6: Fix the docstring and log the deferred cleanup

- In `trivia-501-scraper/scrape_current_season.py`'s module docstring, replace the
  `POST /api/admin/questions/rematerialize-stale` line with: `Answers are re-materialized automatically by the backend's AnswerRematerializationScheduler (Mondays 05:17 UTC).`
- Add a `docs/BACKLOG.md` entry under Architecture & Code Quality: "Scraper cleanup: delete dead `init_questions_v2.py`, `populate_answers_v2.py`, `database/models_v4.py`; fix or delete `Dockerfile` (CMD points at non-existent `api.main`); prune unused deps (`fastapi`, `uvicorn`, `python-jose`, `passlib`, `apscheduler`, `prometheus-client`) and pin versions."

**Verify**: `grep -n "rematerialize-stale" trivia-501-scraper/scrape_current_season.py` → no matches.

## Test plan

- New `AnswerRematerializationSchedulerTest` (2 tests).
- Workflow correctness is only verified by a real run (Step 5). There's no local way to run GitHub cron.

## Done criteria

- [ ] Workflow has one scrape step with `--season`, and no references to the dead scripts
- [ ] `AnswerRematerializationScheduler` exists; `mvn -B test` → BUILD SUCCESS, including 2 new tests
- [ ] `grep -n "rematerialize-stale" trivia-501-scraper/scrape_current_season.py` → no matches
- [ ] BACKLOG entry added
- [ ] (Operator) `gh workflow list --all | grep Scraper` → `active`; latest run `success`
- [ ] `plans/README.md` status row updated

## STOP conditions

- The dry-run (Step 5) logs 0 rows for every league, or FBref returns HTTP 403/429 or pages without stats tables. FBref's data availability may have changed, which is a product decision, not a code fix. Report the log excerpt.
- `scrape_current_season.py --help` doesn't list `--season` and `--dry-run`.
- `materialize()` turns out to delete answers when a materializer returns fewer rows (check `upsertAnswers`). If re-materializing can *remove* answers that are live in today's daily, STOP and report before scheduling it.
- Any step would require printing, committing or echoing a database URL or password.

## Maintenance notes

- Re-materializing on Monday morning can change valid answers for a daily challenge already in progress (at 05:17 UTC, most players haven't started yet). If that becomes a complaint, skip questions used by today's `daily_challenges`.
- The weekly run silently no-ops if the secret is removed. Consider a failure notification (GitHub emails the workflow author by default).
- Scheduled workflows get auto-disabled after 60 days without repo activity. If the project goes quiet again, the refresh stops. Check `gh workflow list --all` when you come back.
