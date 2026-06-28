package com.trivia501.controller;

import com.trivia501.dto.admin.TemplateResponse;
import com.trivia501.model.Question;
import com.trivia501.model.QuestionTemplate;
import com.trivia501.service.AdminTemplateService;
import com.trivia501.service.QuestionGeneratorService;
import com.trivia501.service.QuestionGeneratorService.GeneratorResult;
import com.trivia501.service.QuestionMaterializerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin endpoints for the question template + generator pipeline.
 *
 * <h3>Endpoints</h3>
 * <pre>
 *   GET  /api/admin/templates              — list all templates with draft/active counts
 *   GET  /api/admin/templates/{id}         — get a single template
 *   POST /api/admin/templates/generate     — run generator for ALL active templates
 *   POST /api/admin/templates/{id}/generate — run generator for ONE template
 * </pre>
 *
 * <p>The generate endpoints are idempotent: existing draft/active questions are not
 * modified; only missing param-set combinations are created.
 */
@RestController
@RequestMapping("/api/admin/templates")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminTemplateController {

    private final AdminTemplateService        templateService;
    private final QuestionGeneratorService    generatorService;
    private final QuestionMaterializerService materializerService;

    /**
     * {@code @Lazy} on the materializer service is defensive: it prevents a
     * circular-dependency error if any materializer ever depends on a service
     * that transitively depends on this controller.
     */
    public AdminTemplateController(
            AdminTemplateService         templateService,
            QuestionGeneratorService     generatorService,
            @Lazy QuestionMaterializerService materializerService
    ) {
        this.templateService    = templateService;
        this.generatorService   = generatorService;
        this.materializerService = materializerService;
    }

    // ── GET /api/admin/templates ─────────────────────────────────────────────

    /**
     * Lists all templates, ordered by {@code created_at} ascending.
     *
     * <p>Each entry includes live draft/active question counts and a
     * {@code hasMaterializer} flag so the UI can flag mis-configured templates.
     */
    @GetMapping
    public ResponseEntity<List<TemplateResponse>> listTemplates() {
        List<TemplateResponse> body = templateService.getAllTemplates().stream()
            .sorted((a, b) -> {
                if (a.getCreatedAt() == null) return 1;
                if (b.getCreatedAt() == null) return -1;
                return a.getCreatedAt().compareTo(b.getCreatedAt());
            })
            .map(this::toResponse)
            .collect(Collectors.toList());

        return ResponseEntity.ok(body);
    }

    // ── GET /api/admin/templates/{id} ────────────────────────────────────────

    /**
     * Returns a single template by UUID.
     *
     * @param id the template UUID
     */
    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getTemplate(@PathVariable UUID id) {
        QuestionTemplate template = templateService.getTemplateById(id);
        return ResponseEntity.ok(toResponse(template));
    }

    // ── POST /api/admin/templates/generate ───────────────────────────────────

    /**
     * Runs the template generator for all active templates.
     *
     * <p>Returns a JSON summary:
     * <pre>
     * { "created": 42, "skipped": 8, "total": 50, "message": "Generator complete." }
     * </pre>
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateAll() {
        log.info("Admin triggered: generate all templates");
        GeneratorResult result = generatorService.generateAll();
        return ResponseEntity.ok(Map.of(
            "created", result.created(),
            "skipped", result.skipped(),
            "total",   result.total(),
            "message", "Generator complete."
        ));
    }

    // ── POST /api/admin/templates/{id}/generate ───────────────────────────────

    /**
     * Runs the template generator for a single template.
     *
     * @param id the template UUID
     */
    @PostMapping("/{id}/generate")
    public ResponseEntity<Map<String, Object>> generateForTemplate(@PathVariable UUID id) {
        log.info("Admin triggered: generate template {}", id);
        GeneratorResult result = generatorService.generateForTemplate(id);
        return ResponseEntity.ok(Map.of(
            "created",     result.created(),
            "skipped",     result.skipped(),
            "total",       result.total(),
            "template_id", id.toString(),
            "message",     "Generator complete for template " + id + "."
        ));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private TemplateResponse toResponse(QuestionTemplate t) {
        TemplateResponse r = new TemplateResponse();
        r.setId(t.getId());
        r.setCategoryId(t.getCategoryId());
        r.setSlug(t.getSlug());
        r.setDisplayName(t.getDisplayName());
        r.setTextTemplate(t.getTextTemplate());
        r.setMaterializerKey(t.getMaterializerKey());
        r.setMetricKey(t.getMetricKey());
        r.setDefaultMinScore(t.getDefaultMinScore());
        r.setActive(Boolean.TRUE.equals(t.getIsActive()));
        r.setHasMaterializer(materializerService.hasMaterializer(t.getMaterializerKey()));
        r.setDraftCount(templateService.countQuestionsByTemplateIdAndStatus(t.getId(), Question.STATUS_DRAFT));
        r.setActiveCount(templateService.countQuestionsByTemplateIdAndStatus(t.getId(), Question.STATUS_ACTIVE));
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());
        return r;
    }
}
