package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonResult;
import java.util.Objects;

/** Frozen result-time metadata, without live plan references. */
public record MonteCarloStrategyComparisonRun(
        MonteCarloStrategyComparisonResult result, MonteCarloMode mode, String details, long elapsedNanos) {
    public MonteCarloStrategyComparisonRun {
        Objects.requireNonNull(result);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(details);
    }
}
