package com.scorevault.scoring;

import java.util.List;

public record JudgeTaskView(
        int assignmentId,
        int roundId,
        String roundName,
        int eventId,
        String eventTitle,
        int teamId,
        String teamName,
        String teamLead,
        String submissionBlobUrl,
        String submissionFileName,
        String submissionNotes,
        String status,
        boolean isLocked,
        List<JudgeTaskCriterionView> criteria
) {}
