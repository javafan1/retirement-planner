package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable inputs for mortality-weighting exactly two claiming strategies. */
public record SocialSecurityMortalityWeightedComparisonRequest(
        SocialSecurityStrategyRequest strategyA,
        SocialSecurityStrategyRequest strategyB,
        SocialSecurityMortalityDistribution primaryMortality,
        SocialSecurityMortalityDistribution spouseMortality,
        LocalDate mortalityBaseDate,
        LocalDate presentValueBaseDate,
        BigDecimal realDiscountRate) {

    public SocialSecurityMortalityWeightedComparisonRequest {
        Objects.requireNonNull(strategyA, "Strategy A is required.");
        Objects.requireNonNull(strategyB, "Strategy B is required.");
        Objects.requireNonNull(primaryMortality, "Primary mortality is required.");
        Objects.requireNonNull(spouseMortality, "Spouse mortality is required.");
        Objects.requireNonNull(mortalityBaseDate, "Mortality base date is required.");
        Objects.requireNonNull(presentValueBaseDate, "Present-value base date is required.");
        Objects.requireNonNull(realDiscountRate, "Real discount rate is required.");

        // Reuse the matrix boundary for household compatibility and rate validation.
        new SocialSecurityDeathAgeMatrixRequest(
                strategyA,
                strategyB,
                primaryMortality.deathAges(),
                spouseMortality.deathAges(),
                presentValueBaseDate,
                realDiscountRate);

        validateSupportAfterBaseDate(
                "Primary",
                strategyA.primaryElection().birthDate(),
                primaryMortality,
                mortalityBaseDate);
        validateSupportAfterBaseDate(
                "Spouse",
                strategyA.spouseElection().birthDate(),
                spouseMortality,
                mortalityBaseDate);
    }

    private static void validateSupportAfterBaseDate(
            String owner,
            LocalDate birthDate,
            SocialSecurityMortalityDistribution distribution,
            LocalDate mortalityBaseDate) {
        for (SocialSecurityMortalityProbability probability
                : distribution.probabilities()) {
            LocalDate deathDate = SocialSecurityDeathDateCalculator.calculateDeathDate(
                    birthDate,
                    probability.deathAge());
            if (deathDate.isBefore(mortalityBaseDate)) {
                throw new IllegalArgumentException(
                        owner
                                + " mortality death age "
                                + probability.deathAge()
                                + " produces death date "
                                + deathDate
                                + " before mortality base date "
                                + mortalityBaseDate
                                + ".");
            }
        }
    }
}
