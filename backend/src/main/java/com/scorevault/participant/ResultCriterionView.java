package com.scorevault.participant;

import java.math.BigDecimal;

public record ResultCriterionView(String criterion, BigDecimal maxScore, BigDecimal weightPercent,
                                  BigDecimal avgRawScore, BigDecimal avgWeightedScore) {}