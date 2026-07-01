package com.trivia501.controller;

import tools.jackson.databind.ObjectMapper;
import com.trivia501.BaseTest;
import com.trivia501.dto.StartFreePlayRequest;
import com.trivia501.dto.SubmitAnswerRequest;
import com.trivia501.model.Answer;
import com.trivia501.model.Category;
import com.trivia501.model.Question;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.repository.CategoryRepository;
import com.trivia501.repository.GameMoveRepository;
import com.trivia501.repository.GameRepository;
import com.trivia501.repository.MatchRepository;
import com.trivia501.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack HTTP integration tests for the Free Play game flow.
 * Exercises: HTTP → Controller → Service → Repository → H2
 * Fuzzy matching scenarios (requiring PostgreSQL similarity()) are covered
 * separately in FuzzyMatchingContainerTest.
 */
@DisplayName("Free Play Integration Tests")
@WithMockUser(username = "00000000-0000-0000-0000-000000000001", roles = {"USER", "ADMIN"})
class FreePlayIntegrationTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private AnswerRepository answerRepository;
    @Autowired private GameMoveRepository gameMoveRepository;
    @Autowired private GameRepository gameRepository;
    @Autowired private MatchRepository matchRepository;

    private UUID playerId;
    private Answer knownAnswer;

    @BeforeEach
    void setUp() {
        gameMoveRepository.deleteAll();
        gameRepository.deleteAll();
        matchRepository.deleteAll();
        answerRepository.deleteAll();
        questionRepository.deleteAll();
        categoryRepository.deleteAll();

        playerId = UUID.randomUUID();

        Category category = categoryRepository.save(Category.builder()
            .name("Football")
            .slug("football")
            .build());

        // difficulty defaults to 2 via @Builder.Default; createMatch also defaults to 2
        Question question = questionRepository.save(Question.builder()
            .categoryId(category.getId())
            .questionText("Appearances for Test FC in Test League 2024/25")
            .metricKey("appearances")
            .config(Map.of())
            .status(Question.STATUS_ACTIVE)
            .build());

        // Seed 12 answers — above the DEFAULT_MIN_ANSWERS (10) threshold
        // Scores 10-21 are all valid darts scores and well clear of bust territory
        knownAnswer = null;
        for (int i = 0; i < 12; i++) {
            Answer a = answerRepository.save(Answer.builder()
                .questionId(question.getId())
                .displayText("Player " + i)
                .answerKey("player " + i)
                .score(10 + i)
                .isValidDarts(true)
                .isBust(false)
                .build());
            if (i == 0) knownAnswer = a;
        }

        // Update denormalized count so findRandomActiveQuestion passes the min-answers gate
        question.setTotalValidCount(12);
        questionRepository.save(question);
    }

    @Test
    @DisplayName("POST /api/freeplay/start returns 200 with starting score 501")
    void startGame_returnsGameStateWithStartingScore() throws Exception {
        mockMvc.perform(post("/api/freeplay/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(startRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gameId").isNotEmpty())
            .andExpect(jsonPath("$.currentScore").value(501))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.questionText").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/freeplay/start returns 400 for unknown category slug")
    void startGame_unknownCategory_returns400() throws Exception {
        StartFreePlayRequest req = StartFreePlayRequest.builder()
            .categorySlug("nonexistent-sport")
            .build();

        mockMvc.perform(post("/api/freeplay/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Submitting a valid answer deducts the answer's score")
    void submitValidAnswer_deductsScore() throws Exception {
        UUID gameId = startGame();

        mockMvc.perform(post("/api/freeplay/games/{id}/submit", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody(knownAnswer.getDisplayText())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("VALID"))
            .andExpect(jsonPath("$.scoreBefore").value(501))
            .andExpect(jsonPath("$.scoreAfter").value(501 - knownAnswer.getScore()))
            .andExpect(jsonPath("$.matchedAnswer").value(knownAnswer.getDisplayText()))
            .andExpect(jsonPath("$.isWin").value(false));
    }

    @Test
    @DisplayName("Submitting an already-used answer returns INVALID with score unchanged")
    void submitAlreadyUsedAnswer_returnsInvalid() throws Exception {
        UUID gameId = startGame();
        String body = submitBody(knownAnswer.getDisplayText());

        // First submission is valid
        mockMvc.perform(post("/api/freeplay/games/{id}/submit", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(jsonPath("$.result").value("VALID"));

        int scoreAfterFirst = 501 - knownAnswer.getScore();

        // Second submission of the same answer is invalid — answer is in usedAnswerIds
        mockMvc.perform(post("/api/freeplay/games/{id}/submit", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("INVALID"))
            .andExpect(jsonPath("$.scoreBefore").value(scoreAfterFirst))
            .andExpect(jsonPath("$.scoreAfter").value(scoreAfterFirst));
    }

    @Test
    @DisplayName("Submitting a blank answer returns 400 (bean validation)")
    void submitEmptyAnswer_returnsInvalid() throws Exception {
        UUID gameId = startGame();

        mockMvc.perform(post("/api/freeplay/games/{id}/submit", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody("   ")))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/freeplay/games/{id} returns current game state")
    void getGameState_returnsCurrentState() throws Exception {
        UUID gameId = startGame();

        mockMvc.perform(get("/api/freeplay/games/{id}", gameId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gameId").value(gameId.toString()))
            .andExpect(jsonPath("$.currentScore").value(501))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("GET /api/freeplay/games/{id} returns 404 for unknown game ID")
    void getGameState_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/freeplay/games/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Score is cumulative across multiple valid answers")
    void multipleValidAnswers_scoreDeductsCumulatively() throws Exception {
        UUID gameId = startGame();

        // Submit Player 0 (score 10) → 501 - 10 = 491
        mockMvc.perform(post("/api/freeplay/games/{id}/submit", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody("Player 0")))
            .andExpect(jsonPath("$.scoreAfter").value(491));

        // Submit Player 1 (score 11) → 491 - 11 = 480
        mockMvc.perform(post("/api/freeplay/games/{id}/submit", gameId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitBody("Player 1")))
            .andExpect(jsonPath("$.result").value("VALID"))
            .andExpect(jsonPath("$.scoreBefore").value(491))
            .andExpect(jsonPath("$.scoreAfter").value(480));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private StartFreePlayRequest startRequest() {
        return StartFreePlayRequest.builder()
            .categorySlug("football")
            .build();
    }

    private UUID startGame() throws Exception {
        String body = mockMvc.perform(post("/api/freeplay/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(startRequest())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(body).get("gameId").asText());
    }

    private String submitBody(String answer) throws Exception {
        return objectMapper.writeValueAsString(
            SubmitAnswerRequest.builder().answer(answer).build()
        );
    }
}
