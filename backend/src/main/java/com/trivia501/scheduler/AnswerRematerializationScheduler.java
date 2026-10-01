package com.trivia501.scheduler;

import com.trivia501.model.Question;
import com.trivia501.repository.QuestionRepository;
import com.trivia501.service.QuestionMaterializerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Re-materializes answers for every active question once a week, after the
 * Python scraper's Monday 03:17 UTC run has refreshed player_season_stints.
 *
 * <p>Each question is materialized in its own transaction (materialize() is
 * @Transactional on a separate bean). A failure on one question — including
 * hand-curated questions with no registered materializer — is logged and
 * skipped, so it can't abort the batch.
 */
@Component
@Slf4j
public class AnswerRematerializationScheduler {

    private final QuestionRepository questionRepository;
    private final QuestionMaterializerService materializerService;

    public AnswerRematerializationScheduler(
            QuestionRepository questionRepository,
            QuestionMaterializerService materializerService
    ) {
        this.questionRepository = questionRepository;
        this.materializerService = materializerService;
    }

    @Scheduled(cron = "0 17 5 * * MON")
    public void rematerializeActiveQuestions() {
        List<Question> active = questionRepository.findByStatus(Question.STATUS_ACTIVE);
        int ok = 0, skipped = 0, upserted = 0;
        for (Question q : active) {
            try {
                upserted += materializerService.materialize(q);
                ok++;
            } catch (Exception e) {
                skipped++;
                log.debug("Skipped rematerializing question {}: {}", q.getId(), e.getMessage());
            }
        }
        log.info("Weekly rematerialization: {} questions refreshed, {} skipped, {} answer rows upserted",
                ok, skipped, upserted);
    }
}
