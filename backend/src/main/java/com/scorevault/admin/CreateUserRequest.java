package com.scorevault.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 150) @Pattern(regexp = "^[^<>]*$", message = "must not contain < or >") String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 200) String affiliation,
        @NotBlank @Pattern(regexp = "^(ADMIN|JUDGE|PARTICIPANT)$", message = "must be ADMIN, JUDGE, or PARTICIPANT") String role,
        @NotBlank @Size(min = 12, max = 100, message = "must be between 12 and 100 characters") String initialPassword) {}
