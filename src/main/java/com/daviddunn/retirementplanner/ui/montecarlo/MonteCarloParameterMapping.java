package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.analysis.parameters.AnalysisParameterConfiguration;
import com.daviddunn.retirementplanner.app.montecarlo.*;

/** Small immutable effective-request capture; never reads controls or financial results. */
final class MonteCarloParameterMapping {
    private MonteCarloParameterMapping() { }
    static AnalysisParameterConfiguration.MonteCarlo capture(MonteCarloSettings settings,
            MonteCarloMortalityRequest mortality, Integer baselineAge) {
        var inflation = settings.inflation().map(value -> new AnalysisParameterConfiguration.Inflation("STOCHASTIC",
                value.expectedInflationRate(), value.inflationVolatility(), value.minimumInflationRate()))
                .orElseGet(() -> new AnalysisParameterConfiguration.Inflation("DETERMINISTIC", null, null, null));
        return new AnalysisParameterConfiguration.MonteCarlo(settings.simulationCount(), settings.seed(), settings.expectedReturn(),
                settings.returnVolatility(), inflation, mortality == null ? null : mortality.primary().mortalityAdjustment().factor(),
                mortality != null && mortality.hasSpouse() ? mortality.longevityAssumptions().spouseAdjustment().factor() : null,
                mortality == null ? null : mortality.survivorClaimingAge().orElse(null), baselineAge);
    }
    static AnalysisParameterConfiguration.MonteCarlo capture(MonteCarloStrategyComparisonRunService.Prepared prepared) {
        var request = prepared.request();
        var mortality = request.assumptions() instanceof MonteCarloStrategyComparisonRequest.Longevity longevity ? longevity.mortality() : null;
        return capture(request.assumptions().settings(), mortality, mortality != null && mortality.hasSpouse()
                ? request.strategyB().survivorClaimingAge() : null);
    }
}
