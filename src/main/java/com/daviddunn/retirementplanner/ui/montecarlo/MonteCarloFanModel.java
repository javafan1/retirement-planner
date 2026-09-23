package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalysisResult;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPercentiles;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Immutable chart values. No percentile calculation or financial reconstruction occurs here.
 */
public record MonteCarloFanModel(
        List<Year> years, int requested, ProjectionChartModel context) {
    public MonteCarloFanModel {
        years = List.copyOf(years);
    }

    public record Year(int calendarYear, Optional<MonteCarloPercentiles> percentiles,
                       Optional<BigDecimal> deterministic) {
    }

    public static MonteCarloFanModel from(MonteCarloAnalysisResult result, ProjectionChartModel context) {
        return new MonteCarloFanModel(result.annualInvestableAssets().entrySet().stream()
                .map(entry -> new Year(entry.getKey(), entry.getValue(),
                        Optional.ofNullable(result.deterministicReference().annualInvestableAssets().get(entry.getKey()))))
                .toList(), result.settings().simulationCount(), context);
    }
}
