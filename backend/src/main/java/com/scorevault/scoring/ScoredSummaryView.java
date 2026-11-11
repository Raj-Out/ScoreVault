package com.scorevault.scoring;

import java.math.BigDecimal;

public record ScoredSummaryView(
        int assignmentId,
        int teamId,
        String teamName,
        BigDecimal totalWeightedScore,
        boolean isLocked,
        String status,
        String scoredAt
) {}
