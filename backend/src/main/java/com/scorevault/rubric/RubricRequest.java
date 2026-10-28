package com.scorevault.rubric;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RubricRequest(
        @NotNull Integer categoryId,
        @NotBlank @Size(max = 150) @Pattern(regexp = "^[^<>]*$", message = "must not contain < or >") String name,
        @Size(max = 500) @Pattern(regexp = "^[^<>]*$", message = "must not contain < or >") String description,
        @NotEmpty @Size(max = 20) List<@Valid CriterionRequest> criteria) {
}
