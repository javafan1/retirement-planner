package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloAnalysisResult;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary;

/**
 * Frozen result-time metadata. No references to the mutable active plan.
 */
public record MonteCarloRun(
        MonteCarloAnalysisResult result,
        MonteCarloFanModel fan,
        BreakEvenPlanSummary people,
        int firstYear,
        int lastYear,
        boolean referenceIncomplete,
        long elapsedNanos) {
}
