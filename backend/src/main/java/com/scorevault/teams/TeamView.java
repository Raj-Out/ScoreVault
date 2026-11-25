package com.scorevault.teams;

import java.util.List;

public record TeamView(int teamId, int eventId, String eventTitle, String teamName,
        String leadName, List<TeamMemberView> members) {}
