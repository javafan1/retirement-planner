package com.daviddunn.retirementplanner.app.montecarlo;

import java.math.BigDecimal;

/** All probabilities have ALL requested paired worlds as denominator. */
public record MonteCarloPairedStateSummary(
        int requestedCount,
        int bothCompleted,
        int aCompletedBFailed,
        int aFailedBCompleted,
        int bothFailed) {

    public MonteCarloPairedStateSummary {
        if (requestedCount <= 0 || bothCompleted < 0 || aCompletedBFailed < 0
                || aFailedBCompleted < 0 || bothFailed < 0
                || requestedCount != (long) bothCompleted + aCompletedBFailed + aFailedBCompleted + bothFailed) {
            throw new IllegalArgumentException("Paired funding populations must reconcile.");
        }
    }

    private BigDecimal probability(int index) {
        return MonteCarloPairedProbabilities.of(bothCompleted, aCompletedBFailed, aFailedBCompleted, bothFailed).get(index);
    }

    public BigDecimal bothCompletedProbability() { return probability(0); }
    public BigDecimal aCompletedBFailedProbability() { return probability(1); }
    public BigDecimal aFailedBCompletedProbability() { return probability(2); }
    public BigDecimal bothFailedProbability() { return probability(3); }
    public int aCompletedCount() { return bothCompleted + aCompletedBFailed; }
    public int bCompletedCount() { return bothCompleted + aFailedBCompleted; }
    public int aFailedCount() { return aFailedBCompleted + bothFailed; }
    public int bFailedCount() { return aCompletedBFailed + bothFailed; }
    public BigDecimal aFundingProbability() { return bothCompletedProbability().add(aCompletedBFailedProbability()); }
    public BigDecimal bFundingProbability() { return bothCompletedProbability().add(aFailedBCompletedProbability()); }

    /** Strategy A minus Strategy B, with no preference encoded. */
    public BigDecimal fundingProbabilityDifference() { return aFundingProbability().subtract(bFundingProbability()); }
    public BigDecimal netAsymmetricFundingAdvantage() {
        return aCompletedBFailedProbability().subtract(aFailedBCompletedProbability());
    }
}
