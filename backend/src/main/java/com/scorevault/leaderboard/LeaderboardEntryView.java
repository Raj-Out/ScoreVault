package com.scorevault.leaderboard;

import java.math.BigDecimal;

public record LeaderboardEntryView(
        int roundId,
        int eventId,
        String eventTitle,
        int roundNumber,
        String roundName,
        int teamId,
        String teamName,
        String teamLead,
        String institution,
        int judgesEvaluatedCount,
        BigDecimal finalWeightedAverageScore,
        long leaderboardRank,
        BigDecimal priorityCriterionScore,
        String firstSubmittedAt
) {}
