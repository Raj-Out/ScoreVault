package com.scorevault.scoring;

import java.math.BigDecimal;

public record JudgeTaskCriterionView(
        int criterionId,
        String name,
        String description,
        BigDecimal maxScore,
        BigDecimal weightagePercent,
        BigDecimal existingRawScore,
        BigDecimal existingWeightedScore
) {}
