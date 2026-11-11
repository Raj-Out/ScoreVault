package com.scorevault.leaderboard;

public record RoundOptionView(
        int roundId,
        int eventId,
        String eventTitle,
        int roundNumber,
        String roundName,
        boolean isFinal,
        String status
) {}
