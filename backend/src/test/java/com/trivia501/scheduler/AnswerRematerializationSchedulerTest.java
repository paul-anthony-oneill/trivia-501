package com.trivia501.scheduler;

import com.trivia501.model.Question;
import com.trivia501.repository.QuestionRepository;
import com.trivia501.service.QuestionMaterializerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerRematerializationScheduler Tests")
class AnswerRematerializationSchedulerTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionMaterializerService materializerService;

    private AnswerRematerializationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AnswerRematerializationScheduler(questionRepository, materializerService);
    }

    @Test
    @DisplayName("A failing question is skipped and the rest are still materialized")
    void shouldContinuePastFailingQuestion() {
        Question q1 = Question.builder().id(UUID.randomUUID()).build();
        Question q2 = Question.builder().id(UUID.randomUUID()).build();
        Question q3 = Question.builder().id(UUID.randomUUID()).build();
        when(questionRepository.findByStatus(Question.STATUS_ACTIVE)).thenReturn(List.of(q1, q2, q3));
        when(materializerService.materialize(q1)).thenReturn(10);
        when(materializerService.materialize(q2)).thenThrow(new IllegalStateException("no materializer"));
        when(materializerService.materialize(q3)).thenReturn(5);

        scheduler.rematerializeActiveQuestions();

        verify(materializerService).materialize(q1);
        verify(materializerService).materialize(q2);
        verify(materializerService).materialize(q3);
    }

    @Test
    @DisplayName("No active questions means nothing is materialized")
    void shouldDoNothingWhenNoActiveQuestions() {
        when(questionRepository.findByStatus(Question.STATUS_ACTIVE)).thenReturn(List.of());

        scheduler.rematerializeActiveQuestions();

        verify(materializerService, never()).materialize(any());
    }
}
