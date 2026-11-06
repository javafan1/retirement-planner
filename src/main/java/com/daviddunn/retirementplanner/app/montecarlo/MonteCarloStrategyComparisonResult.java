package com.daviddunn.retirementplanner.app.montecarlo;

import java.util.List;
import java.util.Objects;

/** Complete pairs only; cancellation publishes no partial result. Statistics are reduced once and cached. */
public final class MonteCarloStrategyComparisonResult {
    private final MonteCarloStrategyComparisonRequest request;
    private final WorldSource worldSource;
    private final List<MonteCarloPairedOutcome> outcomes;
    private final MonteCarloStrategyComparisonSummary summary;
    private final PairedExpenseExecution expenseExecution;
    private final com.daviddunn.retirementplanner.app.socialsecurity.results.ConditionalBenefitAnalysisExecution benefitExecution;

    public enum WorldSource { GENERATED, CALLER_SUPPLIED }

    public MonteCarloStrategyComparisonResult(MonteCarloStrategyComparisonRequest request,
            WorldSource worldSource, List<MonteCarloPairedOutcome> outcomes) {
        this(request, worldSource, outcomes, null);
    }

    /** Codec-only pure reconstruction; never reduces historical observations again. */
    MonteCarloStrategyComparisonResult(MonteCarloStrategyComparisonRequest request,
            WorldSource worldSource, List<MonteCarloPairedOutcome> outcomes, MonteCarloStrategyComparisonSummary frozen) {
        this(request, worldSource, outcomes, frozen, null);
    }

    MonteCarloStrategyComparisonResult(MonteCarloStrategyComparisonRequest request,
            WorldSource worldSource, List<MonteCarloPairedOutcome> outcomes, MonteCarloStrategyComparisonSummary frozen,
            PairedExpenseExecution expenseExecution) {
        this(request, worldSource, outcomes, frozen, expenseExecution, null);
    }

    MonteCarloStrategyComparisonResult(MonteCarloStrategyComparisonRequest request,
            WorldSource worldSource, List<MonteCarloPairedOutcome> outcomes, MonteCarloStrategyComparisonSummary frozen,
            PairedExpenseExecution expenseExecution,
            com.daviddunn.retirementplanner.app.socialsecurity.results.ConditionalBenefitAnalysisExecution benefitExecution) {
        this.request = Objects.requireNonNull(request);
        this.worldSource = Objects.requireNonNull(worldSource);
        this.expenseExecution = expenseExecution;
        this.benefitExecution = benefitExecution;
        if ((expenseExecution != null || benefitExecution != null) && worldSource != WorldSource.GENERATED)
            throw new IllegalArgumentException("Enabled paired expenses require generated worlds.");
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
        summary = frozen == null ? MonteCarloStrategyComparisonAccumulator.reduce(request.assumptions(), outcomes) : frozen;
    }

    public MonteCarloStrategyComparisonRequest request() { return request; }
    public WorldSource worldSource() { return worldSource; }
    public List<MonteCarloPairedOutcome> outcomes() { return outcomes; }
    public MonteCarloStrategyComparisonSummary summary() { return summary; }
    public PairedExpenseExecution expenseExecution() { return expenseExecution; }
    public com.daviddunn.retirementplanner.app.socialsecurity.results.ConditionalBenefitAnalysisExecution benefitExecution() { return benefitExecution; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MonteCarloStrategyComparisonResult that
                && request.equals(that.request) && worldSource == that.worldSource
                && Objects.equals(expenseExecution, that.expenseExecution)
                && Objects.equals(benefitExecution, that.benefitExecution)
                && outcomes.equals(that.outcomes) && summary.equals(that.summary);
    }

    @Override
    public int hashCode() {
        int legacy = Objects.hash(request, worldSource, outcomes, summary);
        int expense = expenseExecution == null ? legacy : Objects.hash(legacy, expenseExecution);
        return benefitExecution == null ? expense : Objects.hash(expense, benefitExecution);
    }

    @Override
    public String toString() {
        return "MonteCarloStrategyComparisonResult[request=" + request + ", worldSource=" + worldSource
                + ", outcomes=" + outcomes + ", summary=" + summary
                + (expenseExecution == null ? "" : ", expenseExecution=" + expenseExecution)
                + (benefitExecution == null ? "" : ", benefitExecution=" + benefitExecution) + "]";
    }

    public int requestedPairedCount() {
        return request.assumptions().settings().simulationCount();
    }
}
