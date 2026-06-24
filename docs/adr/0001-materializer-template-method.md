# ADR-0001: Template Method for Question Materializers

**Status**: Accepted  
**Date**: 2026-06-23

## Context

The `QuestionMaterializer` interface has four implementations, one per query shape:

- `FootballPlayerCareerMetricMaterializer`
- `FootballPlayerCompetitionMetricSinceMaterializer`
- `FootballTeamCompetitionMetricSinceMaterializer`
- `FootballTeamCompetitionSeasonMaterializer`

Each implementation carries its own copy of the materialization loop — validate metric key, query repository, iterate aggregates, resolve metric, filter zero scores, lookup player, build answer, upsert entity, log summary. Helper methods (`extractStartYear`, `extractCompetitionTypes`, `shouldRestrictToTopFlight`, `resolveMetric`) are copy-pasted with minor differences in JSON path navigation and case sets. Estimated ~200 lines of duplication across the four files.

## Decision

Extract `AbstractQuestionMaterializer implements QuestionMaterializer`. It owns the materialization loop once. Subclasses provide:

- `queryRepository(MaterializationContext ctx)` → `List<StintAggregate>` — bundles param extraction and the repository call
- `METRIC_LABELS` static map — declares which metric keys this materializer supports
- `buildMetadata(params, playerId)` → `Map<String, Object>` — per-subclass metadata keys

The template owns:

- Metric key validation (against `METRIC_LABELS`)
- The aggregate loop (resolve metric, filter ≤0, lookup player, build answer)
- Entity upsert
- Logging

`enumerateParams()` remains abstract — the four implementations share no skeleton.

`resolveMetric()` moves to the template as a single 9-case switch. Subclasses declare support via `METRIC_LABELS`; unknown keys are caught by validation before `resolveMetric` is called.

Protected path-reader helpers (`readStringList`, `readBoolean`, `extractStartYear`) eliminate duplicated `@SuppressWarnings("unchecked")` blocks. The Career materializer overrides `extractCompetitionTypes` and `shouldRestrictToTopFlight` to read from `params.*` instead of the default `params.competition_id.*`.

## Consequences

- ~200 duplicate lines deleted across four files
- New materializer = subclass with three methods, not 250-line copy-paste
- The materialization loop has one test surface (the template) plus N query-strategy tests (the subclasses)
- `QuestionMaterializerService` is unchanged — it injects `List<QuestionMaterializer>` as before
- `enumerateParams()` remains a per-subclass concern with no forced common structure
