package com.scorevault.scoring;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitScoreRequest(
        @NotNull(message = "assignmentId is required") Integer assignmentId,
        @NotEmpty(message = "At least one criterion score is required") List<@Valid CriterionScoreInput> criteriaScores,
        @Size(max = 1000, message = "Feedback must not exceed 1000 characters") String feedback
) {}
