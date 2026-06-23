package com.trivia501.materializer;

import com.trivia501.model.*;
import com.trivia501.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Materializes career-total questions of the form:
 * <pre>
 *   "Career goals in top-flight football since 2000"
 *   "Career appearances in top-flight football since 2000"
 * </pre>
 *
 * <p>Aggregates across <em>all</em> qualifying competitions for every player's
 * entire career since a start year.  Produces the largest possible answer pool.
 *
 * <h3>Supported metric keys</h3>
 * goals, appearances, assists, goals_assists, sub_appearances
 */
@Component
@Slf4j
public class FootballPlayerCareerMetricMaterializer extends AbstractQuestionMaterializer {

    public static final String KEY = "football.player_career_metric";

    private static final Map<String, String> METRIC_LABELS = Map.of(
        "goals",           "Goals",
        "appearances",     "Appearances",
        "assists",         "Assists",
        "goals_assists",   "Goals + Assists",
        "sub_appearances", "Substitute appearances"
    );

    private final PlayerSeasonStintRepository stintRepository;

    public FootballPlayerCareerMetricMaterializer(
            PlayerSeasonStintRepository stintRepository,
            PlayerRepository            playerRepository,
            CompetitionRepository       competitionRepository) {
        super(playerRepository, competitionRepository);
        this.stintRepository = stintRepository;
    }

    @Override
    public String getMaterializerKey() { return KEY; }

    @Override
    protected Map<String, String> getMetricLabels() { return METRIC_LABELS; }

    // ── Enumeration ──────────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> enumerateParams(QuestionTemplate template) {
        int startYear = extractStartYear(template);
        return List.of(Map.of("start_year", String.valueOf(startYear)));
    }

    // ── queryRepository hook ──────────────────────────────────────────────────

    @Override
    protected List<PlayerSeasonStintRepository.StintAggregate> queryRepository(
            MaterializationContext ctx) {
        int startYear = ctx.intParam("start_year");
        List<UUID> competitionIds = resolveCompetitionIds(ctx.template());
        if (competitionIds.isEmpty()) {
            log.warn("No matching competitions found for question {} — returning empty.",
                ctx.question().getId());
            return List.of();
        }
        log.debug("Materializing career: since={}, across {} competitions",
            startYear, competitionIds.size());
        return stintRepository.aggregateCareerTotalsSince(startYear, competitionIds);
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Override
    protected Map<String, Object> buildMetadata(
            MaterializationContext ctx, UUID playerId, String metricKey) {
        return Map.of(
            "player_id",  playerId.toString(),
            "start_year", ctx.intParam("start_year"),
            "metric_key", metricKey
        );
    }

    // ── Career overrides: read from params.* not params.competition_id.* ──────

    @Override
    @SuppressWarnings("unchecked")
    protected List<String> extractCompetitionTypes(QuestionTemplate template) {
        try {
            Map<String, Object> schema = template.getParamSchema();
            Map<String, Object> params = (Map<String, Object>) schema.get("params");
            if (params != null) {
                List<Object> types = (List<Object>) params.get("competition_types");
                if (types != null && !types.isEmpty()) {
                    return types.stream().map(Object::toString).collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract competition_types from param_schema: {}", e.getMessage());
        }
        return getDefaultCompetitionTypes();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected boolean shouldRestrictToTopFlight(QuestionTemplate template) {
        try {
            Map<String, Object> schema = template.getParamSchema();
            Map<String, Object> params = (Map<String, Object>) schema.get("params");
            if (params != null) {
                Object topFlightOnly = params.get("top_flight_only");
                if (topFlightOnly != null) {
                    return Boolean.parseBoolean(topFlightOnly.toString());
                }
            }
        } catch (Exception ignored) { }
        return true;
    }

    // ── Competition resolution ───────────────────────────────────────────────

    private List<UUID> resolveCompetitionIds(QuestionTemplate template) {
        List<String> compTypes = extractCompetitionTypes(template);

        List<Competition> competitions = compTypes.stream()
            .flatMap(type -> competitionRepository.findByCompetitionType(type).stream())
            .distinct()
            .collect(Collectors.toList());

        if (shouldRestrictToTopFlight(template)) {
            competitions = competitions.stream()
                .filter(c -> Short.valueOf((short) 1).equals(c.getTier()))
                .collect(Collectors.toList());
        }

        return competitions.stream().map(Competition::getId).collect(Collectors.toList());
    }
}
