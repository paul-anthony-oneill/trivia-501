package com.trivia501.controller;

import com.trivia501.dto.GameHints;
import com.trivia501.dto.GameStateResponse;
import com.trivia501.dto.StartFreePlayRequest;
import com.trivia501.dto.SubmitAnswerRequest;
import com.trivia501.model.*;
import com.trivia501.security.DevModeAuthFilter;
import com.trivia501.service.GameService;
import com.trivia501.service.MatchService;
import com.trivia501.service.PlayerProfileService;
import com.trivia501.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FreePlayController.class)
@Import(JacksonAutoConfiguration.class)
@ActiveProfiles("test")
@WithMockUser(username = DevModeAuthFilter.DEV_PLAYER_ID, roles = {"USER", "ADMIN"})
@DisplayName("FreePlayController Tests")
class FreePlayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MatchService matchService;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private QuestionService questionService;

    @MockitoBean
    private PlayerProfileService playerProfileService;

    @MockitoBean
    private GameResponseAssembler assembler;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final GameHints STUB_HINTS = GameHints.builder()
        .maxScoresLeft(3)
        .checkoutsLeft(0)
        .build();

    private static final Principal PRINCIPAL = () -> DevModeAuthFilter.DEV_PLAYER_ID;

    private UUID playerId;
    private UUID matchId;
    private UUID gameId;
    private UUID questionId;
    private UUID categoryId;
    private Match match;
    private Game game;
    private Question question;
    private Category category;

    @BeforeEach
    void setUp() {
        playerId  = UUID.fromString(DevModeAuthFilter.DEV_PLAYER_ID);
        matchId   = UUID.randomUUID();
        gameId    = UUID.randomUUID();
        questionId  = UUID.randomUUID();
        categoryId  = UUID.randomUUID();

        category = Category.builder()
            .id(categoryId)
            .name("Football")
            .slug("football")
            .build();

        match = Match.builder()
            .id(matchId)
            .player1Id(playerId)
            .type(Match.MatchType.CASUAL)
            .format(Match.MatchFormat.BEST_OF_1)
            .status(Match.MatchStatus.IN_PROGRESS)
            .categoryId(categoryId)
            .build();

        question = Question.builder()
            .id(questionId)
            .categoryId(categoryId)
            .questionText("Appearances for Manchester City in Premier League 2023/24")
            .metricKey("appearances")
            .status(Question.STATUS_ACTIVE)
            .build();

        game = Game.builder()
            .id(gameId)
            .matchId(matchId)
            .gameNumber(1)
            .questionId(questionId)
            .status(Game.GameStatus.IN_PROGRESS)
            .currentTurnPlayerId(playerId)
            .player1Score(501)
            .turnCount(0)
            .turnTimerSeconds(45)
            .build();
    }

    /** Build a standard GameStateResponse for stubbing the assembler. */
    private GameStateResponse stubGameState(Game g, Question q, Match m, int score, String status, boolean isWin) {
        return GameStateResponse.builder()
            .gameId(g.getId()).matchId(m.getId()).questionId(q.getId())
            .questionText(q.getQuestionText()).currentScore(score)
            .turnCount(g.getTurnCount()).status(status).isWin(isWin)
            .turnTimerSeconds(g.getTurnTimerSeconds()).entityType(EntityType.FOOTBALLER)
            .hints(STUB_HINTS).build();
    }

    @Test
    @DisplayName("Should start Free Play game and return game state with hints")
    void shouldStartFreePlay() throws Exception {
        StartFreePlayRequest request = StartFreePlayRequest.builder()
            .categorySlug("football")
            .build();

        when(questionService.getCategoryBySlug("football")).thenReturn(Optional.of(category));
        when(matchService.createMatch(eq(playerId), eq(categoryId),
            eq(Match.MatchType.CASUAL), eq(Match.MatchFormat.BEST_OF_1), isNull()))
            .thenReturn(match);
        when(matchService.startNextGame(match, 501))
            .thenReturn(new MatchService.GameStartRecord(game, question));
        when(assembler.playerIdFrom(any())).thenReturn(playerId);
        when(assembler.buildGameStateResponse(eq(game), eq(question), eq(match), eq(List.of()), eq(List.of())))
            .thenReturn(stubGameState(game, question, match, 501, "IN_PROGRESS", false));

        mockMvc.perform(post("/api/freeplay/start")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gameId").value(gameId.toString()))
            .andExpect(jsonPath("$.matchId").value(matchId.toString()))
            .andExpect(jsonPath("$.questionText").value("Appearances for Manchester City in Premier League 2023/24"))
            .andExpect(jsonPath("$.currentScore").value(501))
            .andExpect(jsonPath("$.turnCount").value(0))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.isWin").value(false))
            .andExpect(jsonPath("$.hints.maxScoresLeft").value(3));
    }

    @Test
    @DisplayName("Should submit valid answer and return result with updated game state")
    void shouldSubmitValidAnswer() throws Exception {
        SubmitAnswerRequest request = SubmitAnswerRequest.builder()
            .answer("Erling Haaland")
            .build();

        UUID answerId = UUID.randomUUID();

        GameMove move = GameMove.builder()
            .id(UUID.randomUUID()).gameId(gameId).playerId(playerId)
            .moveNumber(1).submittedAnswer("Erling Haaland")
            .matchedAnswerId(answerId).matchedDisplayText("Erling Haaland")
            .result(GameMove.MoveResult.VALID).scoreValue(36).scoreBefore(501).scoreAfter(465)
            .build();

        Game updatedGame = Game.builder()
            .id(gameId).matchId(matchId).gameNumber(1).questionId(questionId)
            .status(Game.GameStatus.IN_PROGRESS).currentTurnPlayerId(playerId)
            .player1Score(465).turnCount(1).turnTimerSeconds(45)
            .build();

        List<UUID> usedAnswerIds = List.of();
        when(gameService.processPlayerMove(eq(gameId), eq(playerId), eq("Erling Haaland"), isNull()))
            .thenReturn(new GameService.MoveRecord(move, updatedGame, match, usedAnswerIds, null));
        when(questionService.getQuestionById(questionId)).thenReturn(Optional.of(question));
        when(assembler.playerIdFrom(any())).thenReturn(playerId);
        when(assembler.buildGameStateResponse(eq(updatedGame), eq(question), eq(match), eq(usedAnswerIds), eq(List.of())))
            .thenReturn(stubGameState(updatedGame, question, match, 465, "IN_PROGRESS", false));

        mockMvc.perform(post("/api/freeplay/games/{gameId}/submit", gameId)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("VALID"))
            .andExpect(jsonPath("$.matchedAnswer").value("Erling Haaland"))
            .andExpect(jsonPath("$.scoreValue").value(36))
            .andExpect(jsonPath("$.scoreBefore").value(501))
            .andExpect(jsonPath("$.scoreAfter").value(465))
            .andExpect(jsonPath("$.isWin").value(false))
            .andExpect(jsonPath("$.gameState.currentScore").value(465))
            .andExpect(jsonPath("$.gameState.turnCount").value(1));
    }

    @Test
    @DisplayName("Should submit invalid answer and return invalid result")
    void shouldSubmitInvalidAnswer() throws Exception {
        SubmitAnswerRequest request = SubmitAnswerRequest.builder()
            .answer("Unknown Player")
            .build();

        GameMove move = GameMove.builder()
            .id(UUID.randomUUID()).gameId(gameId).playerId(playerId)
            .moveNumber(1).submittedAnswer("Unknown Player")
            .result(GameMove.MoveResult.INVALID).scoreBefore(501).scoreAfter(501)
            .build();

        List<UUID> usedAnswerIds = List.of();
        when(gameService.processPlayerMove(eq(gameId), eq(playerId), eq("Unknown Player"), isNull()))
            .thenReturn(new GameService.MoveRecord(move, game, match, usedAnswerIds, null));
        when(questionService.getQuestionById(questionId)).thenReturn(Optional.of(question));
        when(assembler.playerIdFrom(any())).thenReturn(playerId);
        when(assembler.buildGameStateResponse(eq(game), eq(question), eq(match), eq(usedAnswerIds), eq(List.of())))
            .thenReturn(stubGameState(game, question, match, 501, "IN_PROGRESS", false));

        mockMvc.perform(post("/api/freeplay/games/{gameId}/submit", gameId)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("INVALID"))
            .andExpect(jsonPath("$.scoreBefore").value(501))
            .andExpect(jsonPath("$.scoreAfter").value(501))
            .andExpect(jsonPath("$.isWin").value(false));
    }

    @Test
    @DisplayName("Should submit checkout answer and return win result")
    void shouldSubmitCheckoutAnswer() throws Exception {
        SubmitAnswerRequest request = SubmitAnswerRequest.builder()
            .answer("Player with 35")
            .build();

        GameMove move = GameMove.builder()
            .id(UUID.randomUUID()).gameId(gameId).playerId(playerId)
            .moveNumber(11).submittedAnswer("Player with 35")
            .matchedAnswerId(UUID.randomUUID()).matchedDisplayText("Player Name")
            .result(GameMove.MoveResult.CHECKOUT).scoreValue(35).scoreBefore(35).scoreAfter(0)
            .build();

        Game completedGame = Game.builder()
            .id(gameId).matchId(matchId).gameNumber(1).questionId(questionId)
            .status(Game.GameStatus.COMPLETED).currentTurnPlayerId(playerId)
            .player1Score(0).winnerId(playerId).turnCount(11).turnTimerSeconds(45)
            .build();

        List<UUID> usedAnswerIds = List.of();
        when(gameService.processPlayerMove(eq(gameId), eq(playerId), eq("Player with 35"), isNull()))
            .thenReturn(new GameService.MoveRecord(move, completedGame, match, usedAnswerIds, null));
        when(questionService.getQuestionById(questionId)).thenReturn(Optional.of(question));
        when(assembler.playerIdFrom(any())).thenReturn(playerId);
        when(assembler.buildGameStateResponse(eq(completedGame), eq(question), eq(match), eq(usedAnswerIds), eq(List.of())))
            .thenReturn(stubGameState(completedGame, question, match, 0, "COMPLETED", true));

        mockMvc.perform(post("/api/freeplay/games/{gameId}/submit", gameId)
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result").value("CHECKOUT"))
            .andExpect(jsonPath("$.scoreAfter").value(0))
            .andExpect(jsonPath("$.isWin").value(true))
            .andExpect(jsonPath("$.gameState.status").value("COMPLETED"))
            .andExpect(jsonPath("$.gameState.isWin").value(true));
    }

    @Test
    @DisplayName("Should get current game state")
    void shouldGetGameState() throws Exception {
        when(gameService.getGameById(gameId)).thenReturn(Optional.of(game));
        when(questionService.getQuestionById(questionId)).thenReturn(Optional.of(question));
        when(matchService.getMatchById(matchId)).thenReturn(Optional.of(match));
        when(assembler.playerIdFrom(any())).thenReturn(playerId);
        when(assembler.buildGameStateResponse(eq(game), eq(question), eq(match), eq(List.of())))
            .thenReturn(stubGameState(game, question, match, 501, "IN_PROGRESS", false));

        mockMvc.perform(get("/api/freeplay/games/{gameId}", gameId)
                .principal(PRINCIPAL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gameId").value(gameId.toString()))
            .andExpect(jsonPath("$.questionText").value("Appearances for Manchester City in Premier League 2023/24"))
            .andExpect(jsonPath("$.currentScore").value(501))
            .andExpect(jsonPath("$.turnCount").value(0))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.hints.maxScoresLeft").value(3));
    }

    @Test
    @DisplayName("Should return 404 when game not found")
    void shouldReturn404WhenGameNotFound() throws Exception {
        UUID nonExistentGameId = UUID.randomUUID();
        when(gameService.getGameById(nonExistentGameId)).thenReturn(Optional.empty());
        when(assembler.playerIdFrom(any())).thenReturn(playerId);

        mockMvc.perform(get("/api/freeplay/games/{gameId}", nonExistentGameId)
                .principal(PRINCIPAL))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 400 when category not found")
    void shouldReturn400WhenCategoryNotFound() throws Exception {
        StartFreePlayRequest request = StartFreePlayRequest.builder()
            .categorySlug("invalid-category")
            .build();

        when(questionService.getCategoryBySlug("invalid-category")).thenReturn(Optional.empty());
        when(assembler.playerIdFrom(any())).thenReturn(playerId);

        mockMvc.perform(post("/api/freeplay/start")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should abandon game and return 204 No Content")
    void shouldAbandonGame() throws Exception {
        when(assembler.playerIdFrom(any())).thenReturn(playerId);

        mockMvc.perform(post("/api/freeplay/games/{gameId}/abandon", gameId)
                .principal(PRINCIPAL))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Should return 204 when abandoning twice (idempotent)")
    void shouldAbandonGameTwice() throws Exception {
        when(assembler.playerIdFrom(any())).thenReturn(playerId);

        mockMvc.perform(post("/api/freeplay/games/{gameId}/abandon", gameId)
                .principal(PRINCIPAL))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/freeplay/games/{gameId}/abandon", gameId)
                .principal(PRINCIPAL))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Should use default category when not specified")
    void shouldUseDefaultCategory() throws Exception {
        StartFreePlayRequest request = StartFreePlayRequest.builder().build();

        when(questionService.getCategoryBySlug("football")).thenReturn(Optional.of(category));
        when(matchService.createMatch(eq(playerId), eq(categoryId),
            eq(Match.MatchType.CASUAL), eq(Match.MatchFormat.BEST_OF_1), isNull()))
            .thenReturn(match);
        when(matchService.startNextGame(match, 501))
            .thenReturn(new MatchService.GameStartRecord(game, question));
        when(assembler.playerIdFrom(any())).thenReturn(playerId);
        when(assembler.buildGameStateResponse(eq(game), eq(question), eq(match), eq(List.of()), eq(List.of())))
            .thenReturn(stubGameState(game, question, match, 501, "IN_PROGRESS", false));

        mockMvc.perform(post("/api/freeplay/start")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gameId").exists());
    }
}
