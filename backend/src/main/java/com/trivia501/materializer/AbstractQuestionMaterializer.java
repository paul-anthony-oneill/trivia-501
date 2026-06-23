package com.trivia501.materializer;

import com.trivia501.model.*;
import com.trivia501.repository.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Template-method base for {@link QuestionMaterializer} implementations.
 *
 * <p>Owns the materialization loop once: resolve metric key, validate against
 * the subclass's {@code METRIC_LABELS}, query the repository via the abstract
 * {@link #queryRepository} hook, iterate aggregates, resolve the metric score,
 * filter zeros, look up the player, and build {@link MaterializedAnswer} rows.
 *
 * <p>Subclasses provide:
 * <ul>
 *   <li>{@link #getMetricLabels()} — supported metric keys</li>
 *   <li>{@link #queryRepository(MaterializationContext)} — param extraction + repository call</li>
 *   <li>{@link #buildMetadata(MaterializationContext, UUID, String)} — per-answer metadata</li>
 *   <li>{@link #getMaterializerKey()} and {@link #enumerateParams(QuestionTemplate)}</li>
 * </ul>
 *
 * <p>The Career materializer overrides {@link #extractCompetitionTypes} and
 * {@link #shouldRestrictToTopFlight} to read from {@code params.*} instead of
 * the default {@code params.competition_id.*}.
 */
@Slf4j
public abstract class AbstractQuestionMaterializer implements QuestionMaterializer {

    protected static final int DEFAULT_START_YEAR = 2000;

    protected final PlayerRepository playerRepository;
    protected final CompetitionRepository competitionRepository;

    protected AbstractQuestionMaterializer(
            PlayerRepository playerRepository,
            CompetitionRepository competitionRepository) {
        this.playerRepository = playerRepository;
        this.competitionRepository = competitionRepository;
    }

    // ── Template method ───────────────────────────────────────────────────────

    @Override
    public final List<MaterializedAnswer> materialize(MaterializationContext ctx) {
        String metricKey = ctx.template() != null
            ? ctx.template().getMetricKey()
            : ctx.question().getMetricKey();

        Map<String, String> labels = getMetricLabels();
        if (!labels.containsKey(metricKey)) {
            throw new IllegalArgumentException(
                "Unknown metric_key: '" + metricKey + "'. Valid: " + labels.keySet());
        }

        List<PlayerSeasonStintRepository.StintAggregate> aggregates = queryRepository(ctx);

        if (aggregates.isEmpty()) {
            log.warn("No stint data found for question {}", ctx.question().getId());
            return List.of();
        }

        List<MaterializedAnswer> answers = new ArrayList<>();

        for (PlayerSeasonStintRepository.StintAggregate agg : aggregates) {
            int score = resolveMetric(agg, metricKey);
            if (score <= 0) {
                continue;
            }

            Optional<Player> playerOpt = playerRepository.findById(agg.getPlayerId());
            if (playerOpt.isEmpty()) {
                log.warn("Player not found: {}", agg.getPlayerId());
                continue;
            }
            Player player = playerOpt.get();

            answers.add(new MaterializedAnswer(
                player.getNormalizedName(),
                player.getName(),
                score,
                buildMetadata(ctx, player.getId(), metricKey)
            ));
        }

        log.info("Materialised {} answers for question {} (metric={})",
            answers.size(), ctx.question().getId(), metricKey);
        return answers;
    }

    // ── Subclass contract ─────────────────────────────────────────────────────

    /** The supported metric keys and their human-readable labels. */
    protected abstract Map<String, String> getMetricLabels();

    /**
     * Extract params from the context, call the appropriate repository method,
     * and return the aggregate rows.
     */
    protected abstract List<PlayerSeasonStintRepository.StintAggregate> queryRepository(
        MaterializationContext ctx);

    /** Build the metadata map for one answer row. */
    protected abstract Map<String, Object> buildMetadata(
        MaterializationContext ctx, UUID playerId, String metricKey);

    // ── Shared metric resolver (9-case switch) ────────────────────────────────

    protected int resolveMetric(PlayerSeasonStintRepository.StintAggregate agg, String metricKey) {
        return switch (metricKey) {
            case "goals"                    -> (int) agg.getTotalGoals();
            case "appearances"              -> (int) agg.getTotalAppearances();
            case "assists"                  -> (int) agg.getTotalAssists();
            case "clean_sheets"             -> (int) agg.getTotalCleanSheets();
            case "sub_appearances"          -> (int) agg.getTotalSubAppearances();
            case "goals_assists"            -> (int) (agg.getTotalGoals() + agg.getTotalAssists());
            case "goals_appearances"        -> (int) (agg.getTotalGoals() + agg.getTotalAppearances());
            case "assists_appearances"      -> (int) (agg.getTotalAssists() + agg.getTotalAppearances());
            case "goals_assists_appearances"-> (int) (agg.getTotalGoals() + agg.getTotalAssists()
                                                    + agg.getTotalAppearances());
            default -> throw new IllegalArgumentException("Unknown metric_key: " + metricKey);
        };
    }

    // ── Path-reader helpers (one home for @SuppressWarnings) ─────────────────

    @SuppressWarnings("unchecked")
    protected int extractStartYear(QuestionTemplate template) {
        try {
            Map<String, Object> schema = template.getParamSchema();
            Map<String, Object> params = (Map<String, Object>) schema.get("params");
            if (params != null) {
                Map<String, Object> def = (Map<String, Object>) params.get("start_year");
                if (def != null) {
                    List<Object> values = (List<Object>) def.get("values");
                    if (values != null && !values.isEmpty()) {
                        return Integer.parseInt(values.get(0).toString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract start_year from param_schema, using default {}: {}",
                DEFAULT_START_YEAR, e.getMessage());
        }
        return DEFAULT_START_YEAR;
    }

    /** Override to change the fallback when the template schema has no value. */
    protected List<String> getDefaultCompetitionTypes() {
        return List.of("domestic_league");
    }

    /**
     * Default: reads {@code competition_types} from
     * {@code params.competition_id.competition_types}.
     * Career materializer overrides to read from {@code params.competition_types}.
     */
    @SuppressWarnings("unchecked")
    protected List<String> extractCompetitionTypes(QuestionTemplate template) {
        try {
            Map<String, Object> schema = template.getParamSchema();
            Map<String, Object> params = (Map<String, Object>) schema.get("params");
            if (params != null) {
                Map<String, Object> compDef = (Map<String, Object>) params.get("competition_id");
                if (compDef != null) {
                    List<Object> types = (List<Object>) compDef.get("competition_types");
                    if (types != null && !types.isEmpty()) {
                        return types.stream().map(Object::toString).collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract competition_types from param_schema: {}", e.getMessage());
        }
        return getDefaultCompetitionTypes();
    }

    /**
     * Default: reads {@code top_flight_only} from
     * {@code params.competition_id.top_flight_only}.
     * Career materializer overrides to read from {@code params.top_flight_only}.
     */
    @SuppressWarnings("unchecked")
    protected boolean shouldRestrictToTopFlight(QuestionTemplate template) {
        try {
            Map<String, Object> schema = template.getParamSchema();
            Map<String, Object> params = (Map<String, Object>) schema.get("params");
            if (params != null) {
                Map<String, Object> compDef = (Map<String, Object>) params.get("competition_id");
                if (compDef != null) {
                    Object topFlightOnly = compDef.get("top_flight_only");
                    if (topFlightOnly != null) {
                        return Boolean.parseBoolean(topFlightOnly.toString());
                    }
                }
            }
        } catch (Exception ignored) { }
        return true;
    }
}
