package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Complete deterministic gross-benefit comparison for two supplied strategies. */
public record SocialSecurityStrategyComparisonResult(
        SocialSecurityStrategyComparisonRequest request,
        SocialSecurityStrategyValuation strategyA,
        SocialSecurityStrategyValuation strategyB,
        List<SocialSecurityMonthlyComparison> monthlyComparisons,
        BigDecimal nominalDifference,
        BigDecimal realDifference,
        BigDecimal presentValueDifference,
        StrategyComparisonWinner nominalWinner,
        StrategyComparisonWinner realWinner,
        StrategyComparisonWinner presentValueWinner,
        SocialSecurityBreakEvenResult nominalBreakEven) {

    public SocialSecurityStrategyComparisonResult {
        Objects.requireNonNull(request, "Comparison request is required.");
        Objects.requireNonNull(strategyA, "Strategy A valuation is required.");
        Objects.requireNonNull(strategyB, "Strategy B valuation is required.");
        monthlyComparisons = List.copyOf(
                Objects.requireNonNull(
                        monthlyComparisons,
                        "Monthly comparisons are required."));
        Objects.requireNonNull(nominalDifference, "Nominal difference is required.");
        Objects.requireNonNull(realDifference, "Real difference is required.");
        Objects.requireNonNull(
                presentValueDifference,
                "Present-value difference is required.");
        Objects.requireNonNull(nominalWinner, "Nominal winner is required.");
        Objects.requireNonNull(realWinner, "Real winner is required.");
        Objects.requireNonNull(
                presentValueWinner,
                "Present-value winner is required.");
        Objects.requireNonNull(nominalBreakEven, "Break-even result is required.");
    }
}
