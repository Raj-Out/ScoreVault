package com.scorevault.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(@NotBlank @Size(max = 10000) String credential,
                                 @Size(max = 200) String affiliation) {}
