package com.trivia501.controller;

import com.trivia501.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public metadata endpoints for the Football category.
 *
 * <p>All endpoints are {@code permitAll} — they expose only structural metadata
 * (which leagues/clubs have questions) needed to build the lobby navigation.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/football/clubs?league={slug} — distinct clubs with active questions in a league</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/football")
@Slf4j
public class FootballController {

    private final QuestionService questionService;

    public FootballController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /**
     * Returns clubs with active questions for the given league.
     *
     * @param league the league slug, e.g. "premier-league"
     * @return list of {@code {id, name}} objects — id is the slug, name is the display name from teams table
     */
    @GetMapping("/clubs")
    public ResponseEntity<List<Map<String, String>>> getClubs(@RequestParam String league) {
        log.debug("Fetching clubs for league: {}", league);
        return ResponseEntity.ok(questionService.getClubsForLeague(league));
    }
}
