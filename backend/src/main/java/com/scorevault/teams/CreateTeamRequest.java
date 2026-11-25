package com.scorevault.teams;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(
        @NotNull Integer eventId,
        @NotBlank @Size(max = 150) @Pattern(regexp = "^[^<>]*$", message = "must not contain < or >") String teamName,
        @Size(max = 10) List<@NotBlank @Email String> memberEmails) {}
