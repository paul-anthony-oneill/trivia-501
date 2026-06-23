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
 *   "Goals for Manchester United in the Premier League 2023-24"
 * </pre>
 *
 * <p>Scoped to a single (team, competition, season) triplet.  Produces sharp,
 * harder questions with a small answer pool.
 *
 * <h3>Supported metric keys</h3>
 * goals, appearances, assists, clean_sheets, sub_appearances
 */
@Component
@Slf4j
public class FootballTeamCompetitionSeasonMaterializer extends AbstractQuestionMaterializer {

    public static final String KEY = "football.team_competition_season_metric";

    private static final Map<String, String> METRIC_LABELS = Map.of(
        "goals",           "Goals",
        "appearances",     "Appearances",
        "assists",         "Assists",
        "clean_sheets",    "Clean sheets",
        "sub_appearances", "Substitute appearances"
    );

    private static final List<String> DEFAULT_COMPETITION_TYPES =
        List.of("domestic_league", "domestic_cup", "continental_club");

    private final PlayerSeasonStintRepository stintRepository;
    private final TeamRepository              teamRepository;
    private final SeasonRepository            seasonRepository;

    public FootballTeamCompetitionSeasonMaterializer(
            PlayerSeasonStintRepository stintRepository,
            PlayerRepository            playerRepository,
            TeamRepository              teamRepository,
            CompetitionRepository       competitionRepository,
            SeasonRepository            seasonRepository) {
        super(playerRepository, competitionRepository);
        this.stintRepository  = stintRepository;
        this.teamRepository   = teamRepository;
        this.seasonRepository = seasonRepository;
    }

    @Override
    public String getMaterializerKey() { return KEY; }

    @Override
    protected Map<String, String> getMetricLabels() { return METRIC_LABELS; }

    // ── Enumeration ──────────────────────────────────────────────────────────

    @Override
    public List<Map<String, Object>> enumerateParams(QuestionTemplate template) {
        List<String> competitionTypes = extractCompetitionTypes(template);

        List<Competition> competitions = competitionTypes.stream()
            .flatMap(type -> competitionRepository.findByCompetitionType(type).stream())
            .distinct()
            .collect(Collectors.toList());

        if (competitions.isEmpty()) {
            log.warn("Template {} ({}): no competitions found for types {} — returning empty enumeration.",
                template.getId(), template.getSlug(), competitionTypes);
            return List.of();
        }

        Map<UUID, Season> seasonMap = seasonRepository.findAll().stream()
            .collect(Collectors.toMap(Season::getId, s -> s));

        List<Map<String, Object>> results = new ArrayList<>();

        for (Competition comp : competitions) {
            String compName = comp.getDisplayName() != null ? comp.getDisplayName() : comp.getName();

            List<PlayerSeasonStintRepository.TeamSeasonPair> pairs =
                stintRepository.findDistinctTeamSeasonByCompetition(comp.getId());

            for (PlayerSeasonStintRepository.TeamSeasonPair pair : pairs) {
                Season season = seasonMap.get(pair.getSeasonId());
                if (season == null) {
                    log.warn("Season {} not found in season map — skipping.", pair.getSeasonId());
                    continue;
                }

                Optional<Team> teamOpt = teamRepository.findById(pair.getTeamId());
                if (teamOpt.isEmpty()) {
                    log.warn("Team {} not found — skipping.", pair.getTeamId());
                    continue;
                }

                results.add(Map.of(
                    "team_id",          pair.getTeamId().toString(),
                    "competition_id",   comp.getId().toString(),
                    "season_id",        pair.getSeasonId().toString(),
                    "team_name",        teamOpt.get().getName(),
                    "competition_name", compName,
                    "season_label",     season.getLabel()
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
        UUID seasonId      = ctx.uuidParam("season_id");

        String seasonLabel = ctx.templateParams().getOrDefault("season_label", seasonId.toString()).toString();
        log.debug("Materializing: team={}, comp={}, season={}",
            teamId, competitionId, seasonLabel);
        return stintRepository.aggregateByTeamCompetitionSeason(teamId, competitionId, seasonId);
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Override
    protected Map<String, Object> buildMetadata(
            MaterializationContext ctx, UUID playerId, String metricKey) {
        String seasonLabel = ctx.templateParams().getOrDefault("season_label",
            ctx.param("season_id")).toString();
        return Map.of(
            "player_id",      playerId.toString(),
            "team_id",        ctx.param("team_id"),
            "competition_id", ctx.param("competition_id"),
            "season_id",      ctx.param("season_id"),
            "season_label",   seasonLabel,
            "metric_key",     metricKey
        );
    }

    // ── Season overrides: broader default competition types ──────────────────

    @Override
    protected List<String> getDefaultCompetitionTypes() {
        return DEFAULT_COMPETITION_TYPES;
    }
}
