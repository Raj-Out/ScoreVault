package com.scorevault.rubric;

import java.math.BigDecimal;
import java.util.List;

public record RubricView(
        int id,
        int categoryId,
        String categoryName,
        String name,
        String description,
        String createdBy,
        String createdAt,
        BigDecimal totalWeight,
        List<CriterionView> criteria) {

    public record CriterionView(int id, String name, String description, BigDecimal maxScore, BigDecimal weightage) {
    }
}
