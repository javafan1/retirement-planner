package com.daviddunn.retirementplanner.domain.breakeven;

import java.util.Map;

public record BreakEvenAnalysisResult(
        Integer baselineStartYear, Integer baselineEndYear,
        Integer currentStartYear, Integer currentEndYear,
        Integer comparisonStartYear, Integer comparisonEndYear,
        int comparableYearCount, boolean planningHorizonsDiffer,
        BreakEvenPlanSummary baselineAssumptions, BreakEvenPlanSummary currentAssumptions,
        Map<BreakEvenMetric, BreakEvenMetricResult> metrics) {
    public BreakEvenAnalysisResult { metrics = Map.copyOf(metrics); }
}
