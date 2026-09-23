package com.daviddunn.retirementplanner.domain.breakeven;

import java.math.BigDecimal;

public record BreakEvenSurvivalPoint(int year, Integer primaryAge, Integer spouseAge,
        BigDecimal primarySurvivalProbability, BigDecimal spouseSurvivalProbability,
        BigDecimal householdAtLeastOneAliveProbability) { }
