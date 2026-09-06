package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Evaluates one supplied strategy across precomputed joint mortality scenarios. */
public final class SocialSecurityMortalityWeightedStrategyCalculator {

    private static final int MONEY_SCALE = 2;

    public SocialSecurityMortalityWeightedStrategyValue calculate(
            SocialSecurityStrategyRequest strategy,
            List<SocialSecurityJointMortalityScenario> scenarios,
            LocalDate presentValueBaseDate,
            BigDecimal realDiscountRate) {

        return calculate(
                strategy,
                strategy.primarySurvivorClaimDate(),
                strategy.spouseSurvivorClaimDate(),
                scenarios,
                presentValueBaseDate,
                realDiscountRate);
    }

    public SocialSecurityMortalityWeightedStrategyValue calculate(
            SocialSecurityStrategyRequest strategy,
            LocalDate primarySurvivorClaimDate,
            LocalDate spouseSurvivorClaimDate,
            List<SocialSecurityJointMortalityScenario> scenarios,
            LocalDate presentValueBaseDate,
            BigDecimal realDiscountRate) {

        Objects.requireNonNull(strategy, "Strategy is required.");
        Objects.requireNonNull(scenarios, "Joint mortality scenarios are required.");
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("Joint mortality scenarios cannot be empty.");
        }

        SocialSecurityStrategyValuationCalculator valuationCalculator =
                new SocialSecurityStrategyValuationCalculator();
        BigDecimal nominal = BigDecimal.ZERO;
        BigDecimal real = BigDecimal.ZERO;
        BigDecimal presentValue = BigDecimal.ZERO;
        BigDecimal primary = BigDecimal.ZERO;
        BigDecimal spouse = BigDecimal.ZERO;

        for (SocialSecurityJointMortalityScenario scenario : scenarios) {
            SocialSecurityStrategyRequest mortalityStrategy =
                    deriveStrategy(
                            strategy,
                            primarySurvivorClaimDate,
                            spouseSurvivorClaimDate,
                            scenario.primaryDeathDate(),
                            scenario.spouseDeathDate());
            SocialSecurityStrategyValuationSummary scenarioValue =
                    valuationCalculator.calculateSummary(
                    mortalityStrategy,
                    presentValueBaseDate,
                    realDiscountRate);
            BigDecimal probability = scenario.jointProbability();
            nominal = nominal.add(
                    scenarioValue.nominalLifetimeBenefits().multiply(probability));
            real = real.add(scenarioValue.realLifetimeBenefits().multiply(probability));
            presentValue = presentValue.add(
                    scenarioValue.presentValue().multiply(probability));
            primary = primary.add(
                    scenarioValue.primarySelectedBenefits().multiply(probability));
            spouse = spouse.add(
                    scenarioValue.spouseSelectedBenefits().multiply(probability));
        }

        BigDecimal roundedPrimary = money(primary);
        BigDecimal roundedSpouse = money(spouse);
        // Owner totals are the source of truth for the reported nominal total,
        // preserving its public reconciliation after cents rounding.
        BigDecimal roundedNominal = roundedPrimary.add(roundedSpouse);
        return new SocialSecurityMortalityWeightedStrategyValue(
                roundedNominal,
                money(real),
                money(presentValue),
                roundedPrimary,
                roundedSpouse,
                scenarios.size());
    }

    private static SocialSecurityStrategyRequest deriveStrategy(
            SocialSecurityStrategyRequest base,
            LocalDate primarySurvivorClaimDate,
            LocalDate spouseSurvivorClaimDate,
            LocalDate primaryDeathDate,
            LocalDate spouseDeathDate) {
        return new SocialSecurityStrategyRequest(
                base.analysisDate(),
                null,
                base.primaryElection(),
                base.spouseElection(),
                reachable(primarySurvivorClaimDate, primaryDeathDate),
                reachable(spouseSurvivorClaimDate, spouseDeathDate),
                primaryDeathDate,
                spouseDeathDate,
                base.socialSecurityColaRate());
    }

    private static LocalDate reachable(
            LocalDate intendedClaimDate,
            LocalDate survivorDeathDate) {
        return intendedClaimDate != null
                && intendedClaimDate.isBefore(survivorDeathDate)
                ? intendedClaimDate
                : null;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

}
