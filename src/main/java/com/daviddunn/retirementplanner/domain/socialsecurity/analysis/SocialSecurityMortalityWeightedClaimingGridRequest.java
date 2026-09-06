package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Immutable whole-year claiming search under one fixed mortality model. */
public record SocialSecurityMortalityWeightedClaimingGridRequest(
        SocialSecurityStrategyRequest baseStrategy,
        List<Integer> primaryClaimAges,
        List<Integer> spouseClaimAges,
        SocialSecurityMortalityDistribution primaryMortality,
        SocialSecurityMortalityDistribution spouseMortality,
        LocalDate mortalityBaseDate,
        LocalDate presentValueBaseDate,
        BigDecimal realDiscountRate) {

    public SocialSecurityMortalityWeightedClaimingGridRequest {
        Objects.requireNonNull(baseStrategy, "Base strategy is required.");
        Objects.requireNonNull(primaryMortality, "Primary mortality is required.");
        Objects.requireNonNull(spouseMortality, "Spouse mortality is required.");
        Objects.requireNonNull(mortalityBaseDate, "Mortality base date is required.");

        SocialSecurityClaimingGridRequest gridBoundary =
                new SocialSecurityClaimingGridRequest(
                        baseStrategy,
                        primaryClaimAges,
                        spouseClaimAges,
                        presentValueBaseDate,
                        realDiscountRate);
        primaryClaimAges = gridBoundary.primaryClaimAges();
        spouseClaimAges = gridBoundary.spouseClaimAges();
        presentValueBaseDate = gridBoundary.presentValueBaseDate();
        realDiscountRate = gridBoundary.realDiscountRate();

        // Reuse the established mortality support/base-date validation.
        new SocialSecurityMortalityWeightedComparisonRequest(
                baseStrategy,
                baseStrategy,
                primaryMortality,
                spouseMortality,
                mortalityBaseDate,
                presentValueBaseDate,
                realDiscountRate);
    }
}
