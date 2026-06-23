package com.trivia501.materializer;

import com.trivia501.model.*;
import com.trivia501.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Materializes league-wide player questions of the form:
 * <pre>
 *   "Goals in the Premier League since 2000"
 *   "Goals + Assists in La Liga since 2000"
 * </pre>
 *
 * <p>Covers <em>every player</em> who appeared in the given competition since
 * the start year — a much larger answer pool than the team-scoped materializers.
 *
 * <h3>Supported metric keys</h3>
 * goals, appearances, assists, goals_assists, clean_sheets, sub_appearances
 */
@Component
@Slf4j
public class FootballPlayerCompetitionMetricSinceMaterializer extends AbstractQuestionMaterializer {

    public static final String KEY = "football.player_competition_metric_since";

    private static final Map<String, String> METRIC_LABELS = Map.of(
        "goals",           "Goals",
        "appearances",     "Appearances",
        "assists",         "Assists",
        "goals_assists",   "Goals + Assists",
        "clean_sheets",    "Clean sheets",
        "sub_appearances", "Substitute appearances"
    );

    private final PlayerSeasonStintRepository stintRepository;

    public FootballPlayerCompetitionMetricSinceMaterializer(
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
        int startYear              = extractStartYear(template);
        List<String> compTypes     = extractCompetitionTypes(template);
        boolean topFlightOnly      = shouldRestrictToTopFlight(template);

        List<Competition> competitions = compTypes.stream()
            .flatMap(type -> competitionRepository.findByCompetitionType(type).stream())
            .distinct()
            .collect(Collectors.toList());

        if (topFlightOnly) {
            competitions = competitions.stream()
                .filter(c -> Short.valueOf((short) 1).equals(c.getTier()))
                .collect(Collectors.toList());
        }

        if (competitions.isEmpty()) {
            log.warn("Template {} ({}): no competitions found for types {} — empty enumeration.",
                template.getId(), template.getSlug(), compTypes);
            return List.of();
        }

        Set<UUID> competitionsWithData = new HashSet<>(
            stintRepository.findDistinctCompetitionIdsSince(startYear));

        List<Map<String, Object>> results = new ArrayList<>();

        for (Competition comp : competitions) {
            if (!competitionsWithData.contains(comp.getId())) {
                log.debug("Skipping competition {} — no stint data since {}.", comp.getName(), startYear);
                continue;
            }

            String compName = comp.getDisplayName() != null ? comp.getDisplayName() : comp.getName();

            results.add(Map.of(
                "competition_id",   comp.getId().toString(),
                "competition_name", compName,
                "start_year",       String.valueOf(startYear)
            ));
        }

        log.info("Template {} ({}): enumerated {} param sets.",
            template.getId(), template.getSlug(), results.size());
        return results;
    }

    // ── queryRepository hook ──────────────────────────────────────────────────

    @Override
    protected List<PlayerSeasonStintRepository.StintAggregate> queryRepository(
            MaterializationContext ctx) {
        UUID competitionId = ctx.uuidParam("competition_id");
        int  startYear     = ctx.intParam("start_year");

        log.debug("Materializing: comp={}, since={}", competitionId, startYear);
        return stintRepository.aggregateByCompetitionSince(competitionId, startYear);
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Override
    protected Map<String, Object> buildMetadata(
            MaterializationContext ctx, UUID playerId, String metricKey) {
        return Map.of(
            "player_id",      playerId.toString(),
            "competition_id", ctx.param("competition_id"),
            "start_year",     ctx.intParam("start_year"),
            "metric_key",     metricKey
        );
    }
}
