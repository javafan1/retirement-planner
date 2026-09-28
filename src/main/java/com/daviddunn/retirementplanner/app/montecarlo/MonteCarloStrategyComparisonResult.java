package com.daviddunn.retirementplanner.app.montecarlo;

import java.util.List;
import java.util.Objects;

/** Complete pairs only; cancellation throws and publishes no partial result. No Phase 4B reducers. */
public record MonteCarloStrategyComparisonResult(
        MonteCarloStrategyComparisonRequest request,
        WorldSource worldSource,
        List<MonteCarloPairedOutcome> outcomes) {

    public enum WorldSource { GENERATED, CALLER_SUPPLIED }

    public MonteCarloStrategyComparisonResult {
        Objects.requireNonNull(request);
        Objects.requireNonNull(worldSource);
        outcomes = List.copyOf(outcomes);
        if (outcomes.size() != request.assumptions().settings().simulationCount()) {
            throw new IllegalArgumentException("Every requested world needs a complete pair.");
        }
        for (int i = 0; i < outcomes.size(); i++) {
            if (outcomes.get(i).scenarioIndex() != i) {
                throw new IllegalArgumentException("Pairs must retain scenario order.");
            }
        }
    }

    public int requestedPairedCount() {
        return request.assumptions().settings().simulationCount();
    }
}
