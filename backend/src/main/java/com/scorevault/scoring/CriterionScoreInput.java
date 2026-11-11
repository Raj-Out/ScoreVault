package com.scorevault.scoring;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record CriterionScoreInput(
        @NotNull(message = "criterionId is required") Integer criterionId,
        @NotNull(message = "rawScore is required") @DecimalMin(value = "0.0", message = "rawScore must be non-negative") BigDecimal rawScore
) {}
