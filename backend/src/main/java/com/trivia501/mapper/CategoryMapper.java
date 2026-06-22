package com.trivia501.mapper;

import com.trivia501.dto.admin.CategoryResponse;
import com.trivia501.model.Category;
import com.trivia501.repository.QuestionRepository;
import org.springframework.stereotype.Component;

/**
 * Manual mapper for {@link Category} → {@link CategoryResponse}.
 * ponytail: MapStruct removed — single mapping with one DB lookup doesn't justify an annotation processor.
 */
@Component
public class CategoryMapper {

    private final QuestionRepository questionRepository;

    public CategoryMapper(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    public CategoryResponse toResponse(Category category) {
        CategoryResponse r = new CategoryResponse();
        r.setId(category.getId());
        r.setName(category.getName());
        r.setSlug(category.getSlug());
        r.setDescription(category.getDescription());
        r.setQuestionCount(questionRepository.countByCategoryId(category.getId()));
        r.setCreatedAt(category.getCreatedAt());
        r.setUpdatedAt(category.getUpdatedAt());
        return r;
    }
}
