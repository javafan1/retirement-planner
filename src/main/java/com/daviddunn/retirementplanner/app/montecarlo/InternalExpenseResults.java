package com.daviddunn.retirementplanner.app.montecarlo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Explicitly unsupported internal results. No raw legacy-result accessor, historical codec,
 * PDF adapter, public constructor or UI admission path. Reuses existing reducers without
 * adding a meaning to any persisted model/version. No expense paths are retained.
 */
final class InternalExpenseResults {
    private InternalExpenseResults() { }

    static final class Fixed {
        private final MonteCarloAnalysisResult result;
        Fixed(MonteCarloAnalysisResult result) { this.result = result; }
        List<MonteCarloAnalysisResult.RunOutcome> outcomes() { return result.outcomes(); }
        Map<Integer, Optional<MonteCarloPercentiles>> annual() { return result.annualInvestableAssets(); }
        MonteCarloAnalysisResult.Reference reference() { return result.deterministicReference(); }
        Optional<MonteCarloPercentiles> endingAssets() { return result.endingInvestableAssets(); }
    }

    static final class Longevity {
        private final MonteCarloMortalityAnalysisResult result;
        Longevity(MonteCarloMortalityAnalysisResult result) { this.result = result; }
        List<MonteCarloMortalityAnalysisResult.WorldOutcome> outcomes() { return result.outcomes(); }
        Map<Integer, MonteCarloMortalityAnnualResult> annual() { return result.annualResults(); }
        Optional<MonteCarloPercentiles> endingAssets() { return result.endingInvestableAssets(); }
    }

    static final class Comparison {
        private final MonteCarloStrategyComparisonResult result;
        Comparison(MonteCarloStrategyComparisonResult result) { this.result = result; }
        List<MonteCarloPairedOutcome> outcomes() { return result.outcomes(); }
        MonteCarloStrategyComparisonSummary summary() { return result.summary(); }
    }
}
