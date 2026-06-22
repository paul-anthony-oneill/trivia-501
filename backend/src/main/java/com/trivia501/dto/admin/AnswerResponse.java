package com.trivia501.dto.admin;

import com.trivia501.model.Answer;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class AnswerResponse {
    private UUID id;
    private UUID questionId;
    private String answerKey;
    private String displayText;
    private Integer score;
    private Boolean isValidDarts;
    private Boolean isBust;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;

    /** MapStruct-free 1:1 mapping — ponytail: this exists, add MapStruct when mapping diverges from 1:1. */
    public static AnswerResponse from(Answer answer) {
        AnswerResponse r = new AnswerResponse();
        r.setId(answer.getId());
        r.setQuestionId(answer.getQuestionId());
        r.setAnswerKey(answer.getAnswerKey());
        r.setDisplayText(answer.getDisplayText());
        r.setScore(answer.getScore());
        r.setIsValidDarts(answer.getIsValidDarts());
        r.setIsBust(answer.getIsBust());
        r.setMetadata(answer.getMetadata());
        r.setCreatedAt(answer.getCreatedAt());
        return r;
    }
}
