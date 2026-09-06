package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** One fully auditable deterministic longevity scenario. */
public record SocialSecurityDeathAgeMatrixCell(
        int primaryDeathAge,
        int spouseDeathAge,
        LocalDate primaryDeathDate,
        LocalDate spouseDeathDate,
        SocialSecurityStrategyComparisonResult comparison) {

    public SocialSecurityDeathAgeMatrixCell {
        SocialSecurityDeathDateCalculator.validateDeathAge(
                primaryDeathAge,
                "Primary death age");
        SocialSecurityDeathDateCalculator.validateDeathAge(
                spouseDeathAge,
                "Spouse death age");
        Objects.requireNonNull(primaryDeathDate, "Primary death date is required.");
        Objects.requireNonNull(spouseDeathDate, "Spouse death date is required.");
        Objects.requireNonNull(comparison, "Comparison result is required.");
    }

    public BigDecimal nominalDifference() {
        return comparison.nominalDifference();
    }

    public BigDecimal realDifference() {
        return comparison.realDifference();
    }

    public BigDecimal presentValueDifference() {
        return comparison.presentValueDifference();
    }

    public StrategyComparisonWinner nominalWinner() {
        return comparison.nominalWinner();
    }

    public StrategyComparisonWinner realWinner() {
        return comparison.realWinner();
    }

    public StrategyComparisonWinner presentValueWinner() {
        return comparison.presentValueWinner();
    }

    public SocialSecurityBreakEvenResult nominalBreakEven() {
        return comparison.nominalBreakEven();
    }
}
