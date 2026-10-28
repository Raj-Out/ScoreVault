package com.scorevault.rubric;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CriterionRequest(
        @NotBlank @Size(max = 150) @Pattern(regexp = "^[^<>]*$", message = "must not contain < or >") String name,
        @Size(max = 500) @Pattern(regexp = "^[^<>]*$", message = "must not contain < or >") String description,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal maxScore,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal weightage) {
}
