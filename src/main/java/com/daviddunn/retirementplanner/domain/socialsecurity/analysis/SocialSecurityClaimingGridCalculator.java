package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityRetirementDateCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates whole-year retirement claiming combinations. The underlying
 * strategy engine remains exact-date and month-precise.
 */
public final class SocialSecurityClaimingGridCalculator {

    private final SocialSecurityStrategyCalculator strategyCalculator;
    private final SocialSecurityStrategyValuationCalculator valuationCalculator;

    public SocialSecurityClaimingGridCalculator() {
        this(
                new SocialSecurityStrategyCalculator(),
                new SocialSecurityStrategyValuationCalculator());
    }

    SocialSecurityClaimingGridCalculator(
            SocialSecurityStrategyCalculator strategyCalculator,
            SocialSecurityStrategyValuationCalculator valuationCalculator) {
        this.strategyCalculator = Objects.requireNonNull(
                strategyCalculator,
                "Strategy calculator is required.");
        this.valuationCalculator = Objects.requireNonNull(
                valuationCalculator,
                "Strategy valuation calculator is required.");
    }

    public SocialSecurityClaimingGridResult calculate(
            SocialSecurityClaimingGridRequest request) {
        Objects.requireNonNull(request, "Claiming grid request is required.");
        List<SocialSecurityClaimingGridCell> cells = new ArrayList<>();

        for (int primaryAge : request.primaryClaimAges()) {
            LocalDate primaryClaimDate = claimDate(
                    request.baseStrategy().primaryElection().birthDate(),
                    primaryAge);
            for (int spouseAge : request.spouseClaimAges()) {
                LocalDate spouseClaimDate = claimDate(
                        request.baseStrategy().spouseElection().birthDate(),
                        spouseAge);
                try {
                    SocialSecurityStrategyRequest candidate = deriveStrategy(
                            request.baseStrategy(),
                            primaryClaimDate,
                            spouseClaimDate);
                    SocialSecurityLifetimeResult strategyResult =
                            strategyCalculator.calculate(candidate);
                    SocialSecurityStrategyValuation valuation =
                            valuationCalculator.calculate(
                                    strategyResult,
                                    request.presentValueBaseDate(),
                                    request.realDiscountRate());
                    cells.add(new SocialSecurityClaimingGridCell(
                            primaryAge,
                            spouseAge,
                            primaryClaimDate,
                            spouseClaimDate,
                            valuation));
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException(
                            "Unable to evaluate claiming strategy primary="
                                    + primaryAge
                                    + ", spouse="
                                    + spouseAge
                                    + ": "
                                    + exception.getMessage(),
                            exception);
                }
            }
        }

        return new SocialSecurityClaimingGridResult(
                request,
                request.primaryClaimAges(),
                request.spouseClaimAges(),
                cells,
                rank(cells, SocialSecurityClaimingGridMeasure.NOMINAL_LIFETIME),
                rank(cells, SocialSecurityClaimingGridMeasure.REAL_LIFETIME),
                rank(cells, SocialSecurityClaimingGridMeasure.PRESENT_VALUE));
    }

    static SocialSecurityStrategyRequest deriveStrategy(
            SocialSecurityStrategyRequest base,
            LocalDate primaryClaimDate,
            LocalDate spouseClaimDate) {
        return new SocialSecurityStrategyRequest(
                base.analysisDate(),
                base.analysisEndDate(),
                electionWithClaimDate(
                        base.primaryElection(),
                        primaryClaimDate),
                electionWithClaimDate(
                        base.spouseElection(),
                        spouseClaimDate),
                base.primarySurvivorClaimDate(),
                base.spouseSurvivorClaimDate(),
                base.primaryDeathDate(),
                base.spouseDeathDate(),
                base.socialSecurityColaRate());
    }

    private static SocialSecurityClaimingElection electionWithClaimDate(
            SocialSecurityClaimingElection base,
            LocalDate claimDate) {
        return new SocialSecurityClaimingElection(
                base.owner(),
                base.birthDate(),
                base.fullRetirementMonthlyBenefit(),
                base.benefitValuationYear(),
                claimDate);
    }

    private static LocalDate claimDate(LocalDate birthDate, int claimingAge) {
        return SocialSecurityRetirementDateCalculator
                .calculateRetirementClaimDate(birthDate, claimingAge);
    }

    private SocialSecurityClaimingGridRanking rank(
            List<SocialSecurityClaimingGridCell> cells,
            SocialSecurityClaimingGridMeasure measure) {
        BigDecimal maximum = cells.stream()
                .map(cell -> SocialSecurityClaimingGridRanking.value(cell, measure))
                .max(BigDecimal::compareTo)
                .orElseThrow();
        List<SocialSecurityClaimingGridCell> tied = cells.stream()
                .filter(cell -> SocialSecurityClaimingGridRanking
                        .value(cell, measure)
                        .compareTo(maximum) == 0)
                .toList();
        return new SocialSecurityClaimingGridRanking(measure, maximum, tied);
    }
}
