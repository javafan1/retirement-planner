package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.RmdLifeExpectancyFactor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class RmdCalculator {

    public BigDecimal calculateRmd(
            BigDecimal priorYearEndBalance,
            int age,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                priorYearEndBalance,
                "Prior year-end balance is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        if (priorYearEndBalance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Prior year-end balance cannot be negative.");
        }

        if (priorYearEndBalance.signum() == 0) {
            return BigDecimal.ZERO;
        }

        RmdLifeExpectancyFactor factor =
                governmentRules
                        .getRmdRules()
                        .getLifeExpectancyFactor(age);

        return priorYearEndBalance.divide(
                factor.getDistributionPeriod(),
                2,
                RoundingMode.HALF_UP);
    }
}