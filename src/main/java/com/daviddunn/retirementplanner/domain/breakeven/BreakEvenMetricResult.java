package com.daviddunn.retirementplanner.domain.breakeven;

import java.math.BigDecimal;
import java.util.List;

public record BreakEvenMetricResult(BreakEvenMetric metric, BreakEvenStatus status,
        List<BreakEvenYearResult> years, Integer firstCrossoverYear,
        Integer sustainedBreakEvenYear, List<Crossing> crossings) {
    public record Crossing(int year, boolean currentCatchesUp) { }
    public BreakEvenMetricResult {
        years = List.copyOf(years);
        crossings = List.copyOf(crossings);
    }
    public BigDecimal finalDifference() {
        return years.isEmpty() ? null : years.getLast().difference();
    }
}
