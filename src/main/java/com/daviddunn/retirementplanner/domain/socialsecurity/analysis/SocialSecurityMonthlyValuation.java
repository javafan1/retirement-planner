package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/** One strategy month's gross nominal, real, and present-value amounts. */
public record SocialSecurityMonthlyValuation(
        YearMonth month,
        SocialSecurityMonthlyResult strategyMonth,
        BigDecimal nominalBenefit,
        BigDecimal realBenefit,
        BigDecimal presentValueContribution,
        BigDecimal cumulativeNominalBenefit,
        BigDecimal cumulativeRealBenefit,
        BigDecimal cumulativePresentValue) {

    public SocialSecurityMonthlyValuation {
        Objects.requireNonNull(month, "Month is required.");
        Objects.requireNonNull(strategyMonth, "Strategy month is required.");
        if (!month.equals(strategyMonth.month())) {
            throw new IllegalArgumentException(
                    "Valuation month must match the strategy month.");
        }
        Objects.requireNonNull(nominalBenefit, "Nominal benefit is required.");
        Objects.requireNonNull(realBenefit, "Real benefit is required.");
        Objects.requireNonNull(
                presentValueContribution,
                "Present-value contribution is required.");
        Objects.requireNonNull(
                cumulativeNominalBenefit,
                "Cumulative nominal benefit is required.");
        Objects.requireNonNull(
                cumulativeRealBenefit,
                "Cumulative real benefit is required.");
        Objects.requireNonNull(
                cumulativePresentValue,
                "Cumulative present value is required.");
    }
}
