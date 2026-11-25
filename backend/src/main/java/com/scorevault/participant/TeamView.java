package com.scorevault.participant;

import java.util.List;

public record TeamView(int teamId, int eventId, String eventTitle, String teamName,
                       boolean lead, List<String> members) {}