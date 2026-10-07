package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalysisResult;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloPercentiles;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityAnalysisResult;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityAnnualResult;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;
import com.daviddunn.retirementplanner.ui.charts.ProjectionChartModel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Immutable chart values. No percentile calculation or financial reconstruction occurs here.
 */
public record MonteCarloFanModel(
        List<Year> years, int requested, ProjectionChartModel context, MonteCarloMode mode,
        String primaryName, String spouseName) {
    public MonteCarloFanModel {
        years = List.copyOf(years);
    }

    public MonteCarloFanModel(List<Year> years, int requested, ProjectionChartModel context) {
        this(years, requested, context, MonteCarloMode.FIXED_LIFESPAN, "Primary", "Spouse");
    }

    public record Year(int calendarYear, Optional<MonteCarloPercentiles> percentiles,
                       Optional<BigDecimal> deterministic, Optional<MonteCarloMortalityAnnualResult> population) {
        public Year(int calendarYear, Optional<MonteCarloPercentiles> percentiles,
                    Optional<BigDecimal> deterministic) {
            this(calendarYear, percentiles, deterministic, Optional.empty());
        }
    }

    public static MonteCarloFanModel from(MonteCarloMortalityAnalysisResult result, BreakEvenPlanSummary people) {
        return new MonteCarloFanModel(result.annualResults().values().stream()
                .map(annual -> new Year(annual.year(), annual.investableAssets(), Optional.empty(), Optional.of(annual)))
                .toList(), result.requestedSimulationCount(), ProjectionChartModel.empty(), MonteCarloMode.LONGEVITY_ADJUSTED,
                people.primary().name(), people.hasSpouse() ? people.spouse().name() : "");
    }

    public static MonteCarloFanModel from(MonteCarloAnalysisResult result, ProjectionChartModel context) {
        return new MonteCarloFanModel(result.annualInvestableAssets().entrySet().stream()
                .map(entry -> new Year(entry.getKey(), entry.getValue(),
                        Optional.ofNullable(result.deterministicReference().annualInvestableAssets().get(entry.getKey()))))
                .toList(), result.settings().simulationCount(), context);
    }
}
