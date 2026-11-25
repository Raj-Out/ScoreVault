package com.scorevault.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoundRequest(
        @NotNull Integer eventId,
        @NotNull @Min(1) Integer roundNumber,
        @NotBlank @Size(max = 100) @Pattern(regexp = "^[^<>]*$", message = "must not contain < or >") String roundName,
        boolean isFinal) {}
