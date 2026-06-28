package com.trivia501.service;

import com.trivia501.model.QuestionTemplate;
import com.trivia501.repository.QuestionRepository;
import com.trivia501.repository.QuestionTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service layer for admin template operations.
 *
 * <p>Extracted from {@code AdminTemplateController} to keep repository access
 * out of the controller layer. The controller retains response assembly
 * (entity → DTO mapping) but no longer imports JPA repositories directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminTemplateService {

    private final QuestionTemplateRepository templateRepository;
    private final QuestionRepository questionRepository;

    public List<QuestionTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    public QuestionTemplate getTemplateById(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
    }

    public long countQuestionsByTemplateIdAndStatus(UUID templateId, String status) {
        return questionRepository.countByTemplateIdAndStatus(templateId, status);
    }
}
