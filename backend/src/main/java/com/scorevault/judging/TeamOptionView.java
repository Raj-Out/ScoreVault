package com.scorevault.judging;

public record TeamOptionView(
        int teamId,
        int eventId,
        String teamName,
        String leadName,
        String leadAffiliation,
        String memberAffiliations
) {}
