package com.trivia501.controller;

import com.trivia501.model.Category;
import com.trivia501.model.Question;
import com.trivia501.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for retrieving category information.
 *
 * Endpoints:
 * - GET /api/categories - Get all available categories
 * - GET /api/categories/{slug}/questions - Get active questions for a category
 */
@RestController
@RequestMapping("/api/categories")
@Slf4j
public class CategoryController {

    private final QuestionService questionService;

    public CategoryController(QuestionService questionService) {
        this.questionService = questionService;
    }

    /**
     * Get all categories.
     *
     * @return list of categories
     */
    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        log.debug("Getting all categories");
        List<Category> categories = questionService.getAllCategories();
        return ResponseEntity.ok(categories);
    }

    /**
     * Get active questions for a category (for E2E test discovery).
     *
     * @param slug the category slug
     * @return list of active questions
     */
    @GetMapping("/{slug}/questions")
    public ResponseEntity<List<Question>> getQuestions(@PathVariable String slug) {
        log.debug("Getting active questions for category: {}", slug);
        List<Question> questions = questionService.getActiveQuestionsBySlug(slug);
        return ResponseEntity.ok(questions);
    }
}
