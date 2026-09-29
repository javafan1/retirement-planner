package com.daviddunn.retirementplanner.app.montecarlo;

import java.util.List;
import java.util.Objects;

/** Complete pairs only; cancellation publishes no partial result. Statistics are reduced once and cached. */
public final class MonteCarloStrategyComparisonResult {
    private final MonteCarloStrategyComparisonRequest request;
    private final WorldSource worldSource;
    private final List<MonteCarloPairedOutcome> outcomes;
    private final MonteCarloStrategyComparisonSummary summary;

    public enum WorldSource { GENERATED, CALLER_SUPPLIED }

    public MonteCarloStrategyComparisonResult(MonteCarloStrategyComparisonRequest request,
            WorldSource worldSource, List<MonteCarloPairedOutcome> outcomes) {
        this.request = Objects.requireNonNull(request);
        this.worldSource = Objects.requireNonNull(worldSource);
        this.outcomes = List.copyOf(outcomes);
        outcomes = this.outcomes;
        if (outcomes.size() != request.assumptions().settings().simulationCount()) {
            throw new IllegalArgumentException("Every requested world needs a complete pair.");
        }
        for (int i = 0; i < outcomes.size(); i++) {
            if (outcomes.get(i).scenarioIndex() != i) {
                throw new IllegalArgumentException("Pairs must retain scenario order.");
            }
        }
        summary = MonteCarloStrategyComparisonAccumulator.reduce(request.assumptions(), outcomes);
    }

    public MonteCarloStrategyComparisonRequest request() { return request; }
    public WorldSource worldSource() { return worldSource; }
    public List<MonteCarloPairedOutcome> outcomes() { return outcomes; }
    public MonteCarloStrategyComparisonSummary summary() { return summary; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MonteCarloStrategyComparisonResult that
                && request.equals(that.request) && worldSource == that.worldSource
                && outcomes.equals(that.outcomes) && summary.equals(that.summary);
    }

    @Override
    public int hashCode() { return Objects.hash(request, worldSource, outcomes, summary); }

    @Override
    public String toString() {
        return "MonteCarloStrategyComparisonResult[request=" + request + ", worldSource=" + worldSource
                + ", outcomes=" + outcomes + ", summary=" + summary + "]";
    }

    public int requestedPairedCount() {
        return request.assumptions().settings().simulationCount();
    }
}
