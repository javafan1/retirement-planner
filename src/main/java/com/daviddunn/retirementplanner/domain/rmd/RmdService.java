package com.daviddunn.retirementplanner.domain.rmd;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class RmdService {

    private final RmdEligibilityCalculator eligibilityCalculator;
    private final RmdCalculator rmdCalculator;

    public RmdService() {

        this(
                new RmdEligibilityCalculator(),
                new RmdCalculator());
    }

    /*
     * Constructor useful for testing and dependency injection.
     */
    public RmdService(
            RmdEligibilityCalculator eligibilityCalculator,
            RmdCalculator rmdCalculator) {

        this.eligibilityCalculator =
                Objects.requireNonNull(
                        eligibilityCalculator,
                        "RMD eligibility calculator is required.");

        this.rmdCalculator =
                Objects.requireNonNull(
                        rmdCalculator,
                        "RMD calculator is required.");
    }

    public BigDecimal calculateRmd(
            LocalDate dateOfBirth,
            int projectionYear,
            BigDecimal priorYearEndBalance,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                dateOfBirth,
                "Date of birth is required.");

        Objects.requireNonNull(
                priorYearEndBalance,
                "Prior year-end balance is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        boolean rmdRequired =
                eligibilityCalculator.isRmdRequired(
                        dateOfBirth,
                        projectionYear,
                        governmentRules);

        if (!rmdRequired) {
            return BigDecimal.ZERO;
        }

        int age =
                projectionYear -
                        dateOfBirth.getYear();

        return rmdCalculator.calculateRmd(
                priorYearEndBalance,
                age,
                governmentRules);
    }
}