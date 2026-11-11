package com.scorevault.judging;

public record JudgeUserView(
        int userId,
        String fullName,
        String email,
        String affiliation
) {}
