package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.util.List;
import java.util.Objects;

/** Nominal cumulative crossover classification and all detected events. */
public record SocialSecurityBreakEvenResult(
        SocialSecurityBreakEvenStatus status,
        StrategyComparisonWinner initiallyAhead,
        StrategyComparisonWinner aheadAtEnd,
        List<SocialSecurityBreakEvenEvent> crossoverEvents) {

    public SocialSecurityBreakEvenResult {
        Objects.requireNonNull(status, "Break-even status is required.");
        Objects.requireNonNull(initiallyAhead, "Initial leader is required.");
        Objects.requireNonNull(aheadAtEnd, "Ending leader is required.");
        crossoverEvents = List.copyOf(
                Objects.requireNonNull(
                        crossoverEvents,
                        "Crossover events are required."));
        if (status == SocialSecurityBreakEvenStatus.CROSSOVER
                && crossoverEvents.isEmpty()) {
            throw new IllegalArgumentException(
                    "Crossover status requires at least one event.");
        }
        if (status != SocialSecurityBreakEvenStatus.CROSSOVER
                && !crossoverEvents.isEmpty()) {
            throw new IllegalArgumentException(
                    "Non-crossover status cannot contain crossover events.");
        }
    }

    public SocialSecurityBreakEvenEvent firstCrossover() {
        return crossoverEvents.isEmpty() ? null : crossoverEvents.getFirst();
    }
}
