package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/** A month-level transition between nonzero cumulative leaders. */
public record SocialSecurityBreakEvenEvent(
        YearMonth crossoverMonth,
        StrategyComparisonWinner initiallyAhead,
        StrategyComparisonWinner aheadAfterCrossover,
        BigDecimal cumulativeNominalDifferenceAtCrossover) {

    public SocialSecurityBreakEvenEvent {
        Objects.requireNonNull(crossoverMonth, "Crossover month is required.");
        Objects.requireNonNull(initiallyAhead, "Initial leader is required.");
        Objects.requireNonNull(
                aheadAfterCrossover,
                "Post-crossover leader is required.");
        Objects.requireNonNull(
                cumulativeNominalDifferenceAtCrossover,
                "Cumulative nominal difference is required.");
        if (initiallyAhead == StrategyComparisonWinner.TIE
                || aheadAfterCrossover == StrategyComparisonWinner.TIE
                || initiallyAhead == aheadAfterCrossover) {
            throw new IllegalArgumentException(
                    "A crossover must transition between different strategies.");
        }
    }
}
