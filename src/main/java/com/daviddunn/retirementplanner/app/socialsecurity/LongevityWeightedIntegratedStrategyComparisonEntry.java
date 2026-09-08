package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import java.math.BigDecimal;
import java.util.*;

/** Input order is the occurrence identity: 1..N for candidates and 0 for the optional baseline. */
public record LongevityWeightedIntegratedStrategyComparisonEntry(
        int inputOrder,
        SocialSecurityHouseholdClaimingStrategy strategy,
        Optional<LongevityWeightedStrategyAggregate> aggregate,
        Optional<IntegratedSocialSecurityStrategyEvaluationFailure> failure,
        Optional<List<LongevityWeightedIntegratedScenarioOutcome>> scenarioDetails,
        OptionalInt rank,
        Optional<BigDecimal> pvDifferenceFromBaseline,
        Optional<BigDecimal> nominalDifferenceFromBaseline) {
    public LongevityWeightedIntegratedStrategyComparisonEntry {
        Objects.requireNonNull(strategy);
        Objects.requireNonNull(aggregate);
        Objects.requireNonNull(failure);
        Objects.requireNonNull(rank);
        Objects.requireNonNull(pvDifferenceFromBaseline);
        Objects.requireNonNull(nominalDifferenceFromBaseline);
        scenarioDetails = Objects.requireNonNull(scenarioDetails).map(List::copyOf);
        if (aggregate.isPresent() == failure.isPresent()) {
            throw new IllegalArgumentException("An entry must contain either a complete aggregate or a failure.");
        }
        if (failure.isPresent() && (scenarioDetails.isPresent() || rank.isPresent()
                || pvDifferenceFromBaseline.isPresent() || nominalDifferenceFromBaseline.isPresent())) {
            throw new IllegalArgumentException("A failed strategy cannot have completed financial results.");
        }
    }

    public boolean successful() { return aggregate.isPresent(); }
}
