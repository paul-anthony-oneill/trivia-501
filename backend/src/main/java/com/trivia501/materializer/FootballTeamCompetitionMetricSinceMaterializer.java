package com.trivia501.materializer;

import com.trivia501.model.*;
import com.trivia501.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Materializes questions of the form:
 * <pre>
 *   "Goals for Manchester United in the Premier League since 2000"
 * </pre>
 *
 * <p>Scoped to a single (team, competition) pair, aggregating across all
 * seasons since the start year.  Produces a moderate answer pool.
 *
 * <h3>Supported metric keys</h3>
 * goals, appearances, assists, clean_sheets, sub_appearances,
 * goals_assists, goals_appearances, assists_appearances, goals_assists_appearances
 */
@Component
@Slf4j
public class FootballTeamCompetitionMetricSinceMaterializer extends AbstractQuestionMaterializer {

    public static final String KEY = "football.team_competition_metric_since";

    private static final Map<String, String> METRIC_LABELS = Map.of(
        "goals",                    "Goals",
        "appearances",              "Appearances",
        "assists",                  "Assists",
        "clean_sheets",             "Clean sheets",
        "sub_appearances",          "Substitute appearances",
        "goals_assists",            "Goals + Assists",
        "goals_appearances",        "Goals + Appearances",
        "assists_appearances",      "Assists + Appearances",
        "goals_assists_appearances","Goals + Assists + Appearances"
    );

    private final PlayerSeasonStintRepository stintRepository;

    public FootballTeamCompetitionMetricSinceMaterializer(
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

    // ── Enumeration ─────────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> enumerateParams(QuestionTemplate template) {
        int startYear = extractStartYear(template);
        List<String> competitionTypes = extractCompetitionTypes(template);

        List<Competition> competitions = competitionTypes.stream()
            .flatMap(type -> competitionRepository.findByCompetitionType(type).stream())
            .distinct()
            .collect(Collectors.toList());

        if (shouldRestrictToTopFlight(template)) {
            competitions = competitions.stream()
                .filter(c -> Short.valueOf((short) 1).equals(c.getTier()))
                .collect(Collectors.toList());
        }

        if (competitions.isEmpty()) {
            log.warn("Template {} ({}): no competitions found for types {} — returning empty enumeration.",
                template.getId(), template.getSlug(), competitionTypes);
            return List.of();
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (Competition comp : competitions) {
            List<UUID> teamIds = stintRepository
                .findDistinctTeamIdsByCompetitionSince(comp.getId(), startYear);

            for (UUID teamId : teamIds) {
                results.add(Map.of(
                    "team_id",          teamId.toString(),
                    "competition_id",   comp.getId().toString(),
                    "start_year",       String.valueOf(startYear),
                    "competition_name", comp.getDisplayName() != null ? comp.getDisplayName() : comp.getName()
                ));
            }
        }

        log.info("Template {} ({}): enumerated {} param sets.",
            template.getId(), template.getSlug(), results.size());
        return results;
    }

    // ── queryRepository hook ──────────────────────────────────────────────────

    @Override
    protected List<PlayerSeasonStintRepository.StintAggregate> queryRepository(
            MaterializationContext ctx) {
        UUID teamId        = ctx.uuidParam("team_id");
        UUID competitionId = ctx.uuidParam("competition_id");
        int  startYear     = ctx.intParam("start_year");

        log.debug("Materializing: team={}, comp={}, since={}",
            teamId, competitionId, startYear);
        return stintRepository.aggregateByTeamCompetitionSince(teamId, competitionId, startYear);
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Override
    protected Map<String, Object> buildMetadata(
            MaterializationContext ctx, UUID playerId, String metricKey) {
        return Map.of(
            "player_id",      playerId.toString(),
            "team_id",        ctx.param("team_id"),
            "competition_id", ctx.param("competition_id"),
            "start_year",     ctx.intParam("start_year"),
            "metric_key",     metricKey
        );
    }
}
