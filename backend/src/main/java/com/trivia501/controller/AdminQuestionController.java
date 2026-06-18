package com.trivia501.controller;

import com.trivia501.dto.admin.CreateQuestionRequest;
import com.trivia501.dto.admin.DifficultyLockRequest;
import com.trivia501.dto.admin.QuestionListResponse;
import com.trivia501.dto.admin.QuestionResponse;
import com.trivia501.dto.admin.SuitableForDailyRequest;
import com.trivia501.dto.admin.UpdateQuestionRequest;
import com.trivia501.dto.admin.UpdateStatusRequest;
import com.trivia501.service.DifficultyRecalibrationService;
import com.trivia501.service.AdminQuestionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/questions")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminQuestionController {

    private final AdminQuestionService adminQuestionService;

    public AdminQuestionController(AdminQuestionService adminQuestionService) {
        this.adminQuestionService = adminQuestionService;
    }

    @PostMapping
    public ResponseEntity<QuestionResponse> createQuestion(
            @Valid @RequestBody CreateQuestionRequest request) {
        log.info("Create question for category: {}", request.getCategoryId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminQuestionService.createQuestion(request));
    }

    /**
     * List questions with optional filters.
     *
     * @param categoryId filter by category UUID
     * @param status     filter by lifecycle status: {@code draft}, {@code active}, {@code retired}
     */
    @GetMapping
    public ResponseEntity<QuestionListResponse> listQuestions(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminQuestionService.listQuestions(categoryId, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuestionResponse> getQuestion(@PathVariable UUID id) {
        return ResponseEntity.ok(adminQuestionService.getQuestion(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionResponse> updateQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuestionRequest request) {
        log.info("Update question: {}", id);
        return ResponseEntity.ok(adminQuestionService.updateQuestion(id, request));
    }

    /**
     * Transition a question's lifecycle status.
     *
     * <p>Body: {@code {"status": "active"}} — valid values: {@code draft}, {@code active},
     * {@code retired}.
     *
     * <p>Promoting to {@code active} is the point at which the question enters the
     * game rotation (future: triggers materialisation).
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<QuestionResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        log.info("Update question {} status → {}", id, request.getStatus());
        return ResponseEntity.ok(adminQuestionService.updateStatus(id, request.getStatus()));
    }

    /**
     * Re-materializes the answer set for an active question.
     *
     * <p>Use this after the scraper has refreshed {@code player_season_stints} data
     * and you want to update cached answers without cycling the question status.
     * Only works on questions with {@code status = "active"}.
     *
     * <p>Returns:
     * <pre>
     * { "questionId": "...", "answersUpserted": 47 }
     * </pre>
     */
    @PostMapping("/{id}/rematerialize")
    public ResponseEntity<Map<String, Object>> rematerialize(@PathVariable UUID id) {
        log.info("Admin triggered re-materialize for question: {}", id);
        int count = adminQuestionService.rematerialize(id);
        return ResponseEntity.ok(Map.of(
            "questionId",      id.toString(),
            "answersUpserted", count
        ));
    }

    /**
     * Activates up to {@code limit} draft questions in one call.
     *
     * <p>Each promoted question is auto-materialised (answers computed from
     * {@code player_season_stints}).  Failures on individual questions are
     * caught and counted rather than aborting the batch.
     *
     * <p>Call repeatedly until {@code remainingDraft} reaches 0 to fully
     * populate the game question pool.
     *
     * <p>Returns:
     * <pre>
     * {
     *   "activated":       150,
     *   "answersUpserted": 6823,
     *   "errors":          2,
     *   "remainingDraft":  10856
     * }
     * </pre>
     *
     * @param limit max questions to activate per call (default 100, max 500)
     */
    @PostMapping("/bulk-activate")
    public ResponseEntity<Map<String, Object>> bulkActivate(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) UUID templateId) {
        int clampedLimit = Math.min(limit, 500);
        log.info("Admin triggered bulk-activate (limit={}, templateId={})", clampedLimit, templateId);
        Map<String, Object> result = adminQuestionService.bulkActivateDraft(clampedLimit, templateId);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(@PathVariable UUID id) {
        log.info("Delete question: {}", id);
        adminQuestionService.deleteQuestion(id);
    }

    /**
     * Bulk-recalculates difficulty scores and viability for all unlocked questions
     * using their stored zone counts. Does not touch the answers table.
     *
     * <p>Use this after adjusting any constant in {@code DifficultyConstants.java}.
     * For zone-boundary changes (e.g. checkout range 1–19 → 1–25) re-materialisation
     * is required first — this endpoint only re-derives scores from stored counts.
     *
     * <p>Returns:
     * <pre>
     * { "total": 142, "updated": 38, "reExcluded": 12 }
     * </pre>
     */
    @PostMapping("/recalculate-difficulty")
    public ResponseEntity<Map<String, Object>> recalculateDifficulty() {
        log.info("Admin triggered bulk difficulty recalibration");
        DifficultyRecalibrationService.RecalibrationResult result =
            adminQuestionService.recalculateDifficulty();
        return ResponseEntity.ok(Map.of(
            "total",                   result.total(),
            "updated",                 result.updated(),
            "reExcluded",              result.reExcluded(),
            "dailyEligibilityChanged", result.dailyEligibilityChanged()
        ));
    }

    /**
     * Locks or unlocks a question's difficulty score so the recalibration job
     * skips it.
     *
     * <p>Body: {@code { "locked": true, "difficultyScore": 6.5 }}.
     * {@code difficultyScore} is optional — if omitted the stored score is unchanged.
     * {@code difficultyScore} is only applied when {@code locked = true}.
     *
     * <p>Useful when the formula computes an incorrect score for a specific question
     * (e.g. an unusual stat distribution) and you want to pin a manual value.
     */
    @PatchMapping("/{id}/suitable-for-daily")
    public ResponseEntity<QuestionResponse> updateSuitableForDaily(
            @PathVariable UUID id,
            @Valid @RequestBody SuitableForDailyRequest request) {
        log.info("Admin set suitableForDaily={} for question {}", request.getSuitable(), id);
        return ResponseEntity.ok(adminQuestionService.updateSuitableForDaily(id, request.getSuitable()));
    }

    @PatchMapping("/{id}/difficulty-lock")
    public ResponseEntity<QuestionResponse> lockDifficulty(
            @PathVariable UUID id,
            @Valid @RequestBody DifficultyLockRequest request) {
        log.info("Admin {} difficulty lock for question {} (score override: {})",
            Boolean.TRUE.equals(request.getLocked()) ? "enabling" : "disabling",
            id, request.getDifficultyScore());
        return ResponseEntity.ok(adminQuestionService.lockDifficulty(id, request));
    }
}
