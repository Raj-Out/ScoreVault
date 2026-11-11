package com.scorevault.judging;

public record JudgeAssignmentView(
        int assignmentId,
        int roundId,
        String roundName,
        String eventTitle,
        int judgeUserId,
        String judgeName,
        String judgeAffiliation,
        int teamId,
        String teamName,
        String status,
        String assignedAt
) {}
