package com.trivia501.service;

import com.trivia501.dto.admin.AnswerResponse;
import com.trivia501.dto.admin.BulkCreateAnswersRequest;
import com.trivia501.dto.admin.BulkCreateAnswersResponse;
import com.trivia501.dto.admin.CreateAnswerRequest;
import com.trivia501.engine.DartsValidator;
import com.trivia501.model.Answer;
import com.trivia501.model.EntityType;
import com.trivia501.repository.AnswerRepository;
import com.trivia501.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final EntitySearchService entitySearchService;

    @Transactional
    public AnswerResponse createAnswer(UUID questionId, CreateAnswerRequest request) {
        if (!questionRepository.existsById(questionId)) {
            throw new IllegalArgumentException("Question not found with id: " + questionId);
        }

        String answerKey = normalizeAnswerKey(request.getDisplayText());
        if (answerRepository.existsByQuestionIdAndAnswerKey(questionId, answerKey)) {
             throw new IllegalArgumentException("Answer already exists: " + request.getDisplayText());
        }

        Answer answer = new Answer();
        answer.setQuestionId(questionId);
        answer.setDisplayText(request.getDisplayText());
        answer.setAnswerKey(answerKey);
        answer.setScore(request.getScore());
        answer.setMetadata(request.getMetadata());
        answer.setIsValidDarts(isValidDartsScore(request.getScore()));
        answer.setIsBust(isBust(request.getScore()));
        // createdAt set automatically by @CreatedDate / AuditingEntityListener on save

        Answer saved = answerRepository.save(answer);
        // Register in the global entity autocomplete registry (idempotent).
        entitySearchService.upsertEntity(request.getDisplayText(), EntityType.FOOTBALLER, null);
        log.info("Created answer '{}' for question {}", saved.getDisplayText(), questionId);
        return AnswerResponse.from(saved);
    }

    @Transactional
    public BulkCreateAnswersResponse bulkCreateAnswers(UUID questionId, BulkCreateAnswersRequest request) {
        if (!questionRepository.existsById(questionId)) {
            throw new IllegalArgumentException("Question not found with id: " + questionId);
        }

        Set<String> existingKeys = answerRepository.findAnswerKeysByQuestionId(questionId);
        List<Answer> toSave = new ArrayList<>();
        int skipped = 0;

        for (CreateAnswerRequest item : request.getAnswers()) {
            String answerKey = normalizeAnswerKey(item.getDisplayText());
            if (existingKeys.contains(answerKey)) {
                skipped++;
                continue;
            }

            Answer answer = new Answer();
            answer.setQuestionId(questionId);
            answer.setDisplayText(item.getDisplayText());
            answer.setAnswerKey(answerKey);
            answer.setScore(item.getScore());
            answer.setMetadata(item.getMetadata());
            answer.setIsValidDarts(isValidDartsScore(item.getScore()));
            answer.setIsBust(isBust(item.getScore()));
            // createdAt set automatically by @CreatedDate / AuditingEntityListener on save
            toSave.add(answer);
        }

        answerRepository.saveAll(toSave);
        // Register each new name in the global entity autocomplete registry (idempotent).
        toSave.forEach(a -> entitySearchService.upsertEntity(a.getDisplayText(), EntityType.FOOTBALLER, null));

        BulkCreateAnswersResponse response = new BulkCreateAnswersResponse();
        response.setErrors(new ArrayList<>());
        response.setCreated(toSave.size());
        response.setSkipped(skipped);
        return response;
    }

    @Transactional(readOnly = true)
    public List<AnswerResponse> listAnswers(UUID questionId) {
        if (!questionRepository.existsById(questionId)) {
            throw new IllegalArgumentException("Question not found with id: " + questionId);
        }
        return answerRepository.findByQuestionIdOrderByScoreDesc(questionId).stream()
                .map(AnswerResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AnswerResponse updateAnswer(UUID id, CreateAnswerRequest request) {
        Answer answer = answerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Answer not found with id: " + id));

        String newKey = normalizeAnswerKey(request.getDisplayText());
        
        // check duplicate if key changed
        if (!answer.getAnswerKey().equals(newKey)) {
            if (answerRepository.existsByQuestionIdAndAnswerKey(answer.getQuestionId(), newKey)) {
                throw new IllegalArgumentException("Answer already exists: " + request.getDisplayText());
            }
        }

        answer.setDisplayText(request.getDisplayText());
        answer.setAnswerKey(newKey);
        answer.setScore(request.getScore());
        answer.setMetadata(request.getMetadata());
        answer.setIsValidDarts(isValidDartsScore(request.getScore()));
        answer.setIsBust(isBust(request.getScore()));

        Answer saved = answerRepository.save(answer);
        log.info("Updated answer: {}", saved.getId());
        return AnswerResponse.from(saved);
    }

    @Transactional
    public void deleteAnswer(UUID id) {
        if (!answerRepository.existsById(id)) {
            throw new IllegalArgumentException("Answer not found with id: " + id);
        }
        answerRepository.deleteById(id);
        log.info("Deleted answer with id: {}", id);
    }

    @Transactional
    public void deleteAnswers(List<UUID> ids) {
        answerRepository.deleteAllById(ids);
        log.info("Deleted {} answers", ids.size());
    }

    private String normalizeAnswerKey(String displayText) {
        return EntitySearchService.stripAccents(displayText.toLowerCase().trim());
    }

    private boolean isValidDartsScore(int score) {
        return DartsValidator.isValidDartsScore(score);
    }

    private boolean isBust(int score) {
        return !DartsValidator.isValidDartsScore(score);
    }

}
