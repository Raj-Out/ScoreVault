package com.scorevault.participant;

import java.math.BigDecimal;
import java.util.List;

public record TeamResultView(int teamId, String teamName, int eventId, String eventTitle,
                             int roundId, int roundNumber, String roundName,
                             BigDecimal finalScore, Long rank, int judgesEvaluated,
                             List<ResultCriterionView> criteria) {}