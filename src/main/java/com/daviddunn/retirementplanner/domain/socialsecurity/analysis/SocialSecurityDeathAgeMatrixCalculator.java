package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Derives deterministic death dates and delegates every cell to the existing
 * two-strategy comparison engine. It contains no benefit or valuation rules.
 */
public final class SocialSecurityDeathAgeMatrixCalculator {

    private final SocialSecurityStrategyComparisonCalculator comparisonCalculator;

    public SocialSecurityDeathAgeMatrixCalculator() {
        this(new SocialSecurityStrategyComparisonCalculator());
    }

    SocialSecurityDeathAgeMatrixCalculator(
            SocialSecurityStrategyComparisonCalculator comparisonCalculator) {
        this.comparisonCalculator = Objects.requireNonNull(
                comparisonCalculator,
                "Strategy comparison calculator is required.");
    }

    public SocialSecurityDeathAgeMatrixResult calculate(
            SocialSecurityDeathAgeMatrixRequest request) {

        Objects.requireNonNull(request, "Matrix request is required.");
        List<SocialSecurityDeathAgeMatrixCell> cells = new ArrayList<>();

        for (int primaryDeathAge : request.primaryDeathAges()) {
            LocalDate primaryDeathDate =
                    SocialSecurityDeathDateCalculator.calculateDeathDate(
                            request.strategyA().primaryElection().birthDate(),
                            primaryDeathAge);

            for (int spouseDeathAge : request.spouseDeathAges()) {
                LocalDate spouseDeathDate =
                        SocialSecurityDeathDateCalculator.calculateDeathDate(
                                request.strategyA().spouseElection().birthDate(),
                                spouseDeathAge);
                try {
                    SocialSecurityStrategyRequest strategyA = deriveStrategy(
                            request.strategyA(),
                            primaryDeathDate,
                            spouseDeathDate);
                    SocialSecurityStrategyRequest strategyB = deriveStrategy(
                            request.strategyB(),
                            primaryDeathDate,
                            spouseDeathDate);
                    SocialSecurityStrategyComparisonResult comparison =
                            comparisonCalculator.calculate(
                                    new SocialSecurityStrategyComparisonRequest(
                                            strategyA,
                                            strategyB,
                                            request.presentValueBaseDate(),
                                            request.realDiscountRate()));

                    cells.add(new SocialSecurityDeathAgeMatrixCell(
                            primaryDeathAge,
                            spouseDeathAge,
                            primaryDeathDate,
                            spouseDeathDate,
                            comparison));
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(
                            "Unable to evaluate death-age scenario primary="
                                    + primaryDeathAge
                                    + ", spouse="
                                    + spouseDeathAge
                                    + ": "
                                    + exception.getMessage(),
                            exception);
                }
            }
        }

        return new SocialSecurityDeathAgeMatrixResult(
                request,
                request.primaryDeathAges(),
                request.spouseDeathAges(),
                cells);
    }

    static SocialSecurityStrategyRequest deriveStrategy(
            SocialSecurityStrategyRequest base,
            LocalDate primaryDeathDate,
            LocalDate spouseDeathDate) {

        LocalDate primarySurvivorClaimDate = reachableClaimDate(
                base.primarySurvivorClaimDate(),
                primaryDeathDate);
        LocalDate spouseSurvivorClaimDate = reachableClaimDate(
                base.spouseSurvivorClaimDate(),
                spouseDeathDate);

        return new SocialSecurityStrategyRequest(
                base.analysisDate(),
                null,
                base.primaryElection(),
                base.spouseElection(),
                primarySurvivorClaimDate,
                spouseSurvivorClaimDate,
                primaryDeathDate,
                spouseDeathDate,
                base.socialSecurityColaRate());
    }

    private static LocalDate reachableClaimDate(
            LocalDate claimDate,
            LocalDate claimantDeathDate) {
        return claimDate != null && claimDate.isBefore(claimantDeathDate)
                ? claimDate
                : null;
    }
}
