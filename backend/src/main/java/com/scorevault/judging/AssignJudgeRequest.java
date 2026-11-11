package com.scorevault.judging;

import jakarta.validation.constraints.NotNull;

public record AssignJudgeRequest(
        @NotNull(message = "roundId is required") Integer roundId,
        @NotNull(message = "judgeUserId is required") Integer judgeUserId,
        @NotNull(message = "teamId is required") Integer teamId
) {}
