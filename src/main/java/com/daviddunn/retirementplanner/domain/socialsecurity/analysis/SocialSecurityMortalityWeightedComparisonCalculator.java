package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies supplied mortality probabilities to one completed deterministic
 * death-age matrix. It contains no Social Security benefit or discount rules.
 */
public final class SocialSecurityMortalityWeightedComparisonCalculator {

    private static final int MONEY_SCALE = 2;

    private final SocialSecurityDeathAgeMatrixCalculator matrixCalculator;
    private final SocialSecurityIndependentJointMortalityCalculator jointCalculator;

    public SocialSecurityMortalityWeightedComparisonCalculator() {
        this(
                new SocialSecurityDeathAgeMatrixCalculator(),
                new SocialSecurityIndependentJointMortalityCalculator());
    }

    SocialSecurityMortalityWeightedComparisonCalculator(
            SocialSecurityDeathAgeMatrixCalculator matrixCalculator,
            SocialSecurityIndependentJointMortalityCalculator jointCalculator) {
        this.matrixCalculator = Objects.requireNonNull(
                matrixCalculator,
                "Death-age matrix calculator is required.");
        this.jointCalculator = Objects.requireNonNull(
                jointCalculator,
                "Joint mortality calculator is required.");
    }

    public SocialSecurityMortalityWeightedComparisonResult calculate(
            SocialSecurityMortalityWeightedComparisonRequest request) {

        Objects.requireNonNull(request, "Mortality-weighted request is required.");
        SocialSecurityDeathAgeMatrixResult matrix = matrixCalculator.calculate(
                new SocialSecurityDeathAgeMatrixRequest(
                        request.strategyA(),
                        request.strategyB(),
                        request.primaryMortality().deathAges(),
                        request.spouseMortality().deathAges(),
                        request.presentValueBaseDate(),
                        request.realDiscountRate()));

        List<SocialSecurityMortalityWeightedScenario> scenarios = new ArrayList<>();
        BigDecimal nominalA = BigDecimal.ZERO;
        BigDecimal nominalB = BigDecimal.ZERO;
        BigDecimal nominalDifference = BigDecimal.ZERO;
        BigDecimal realA = BigDecimal.ZERO;
        BigDecimal realB = BigDecimal.ZERO;
        BigDecimal realDifference = BigDecimal.ZERO;
        BigDecimal presentValueA = BigDecimal.ZERO;
        BigDecimal presentValueB = BigDecimal.ZERO;
        BigDecimal presentValueDifference = BigDecimal.ZERO;
        BigDecimal probabilityAWins = BigDecimal.ZERO;
        BigDecimal probabilityBWins = BigDecimal.ZERO;
        BigDecimal probabilityTie = BigDecimal.ZERO;
        BigDecimal totalProbability = BigDecimal.ZERO;

        for (SocialSecurityDeathAgeMatrixCell cell : matrix.cells()) {
            BigDecimal primaryProbability = probabilityFor(
                    request.primaryMortality(),
                    cell.primaryDeathAge());
            BigDecimal spouseProbability = probabilityFor(
                    request.spouseMortality(),
                    cell.spouseDeathAge());
            BigDecimal jointProbability = jointCalculator.calculate(
                    primaryProbability,
                    spouseProbability);
            SocialSecurityMortalityWeightedScenario scenario =
                    new SocialSecurityMortalityWeightedScenario(
                            cell.primaryDeathAge(),
                            cell.spouseDeathAge(),
                            primaryProbability,
                            spouseProbability,
                            jointProbability,
                            cell);
            scenarios.add(scenario);

            nominalA = nominalA.add(scenario.weightedNominalStrategyA());
            nominalB = nominalB.add(scenario.weightedNominalStrategyB());
            nominalDifference = nominalDifference.add(
                    scenario.weightedNominalDifference());
            realA = realA.add(scenario.weightedRealStrategyA());
            realB = realB.add(scenario.weightedRealStrategyB());
            realDifference = realDifference.add(scenario.weightedRealDifference());
            presentValueA = presentValueA.add(
                    scenario.weightedPresentValueStrategyA());
            presentValueB = presentValueB.add(
                    scenario.weightedPresentValueStrategyB());
            presentValueDifference = presentValueDifference.add(
                    scenario.weightedPresentValueDifference());
            totalProbability = totalProbability.add(jointProbability);

            switch (cell.presentValueWinner()) {
                case STRATEGY_A -> probabilityAWins = probabilityAWins.add(jointProbability);
                case STRATEGY_B -> probabilityBWins = probabilityBWins.add(jointProbability);
                case TIE -> probabilityTie = probabilityTie.add(jointProbability);
            }
        }

        validateRawDifference(nominalA, nominalB, nominalDifference, "nominal");
        validateRawDifference(realA, realB, realDifference, "real");
        validateRawDifference(
                presentValueA,
                presentValueB,
                presentValueDifference,
                "present-value");
        if (totalProbability.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalStateException(
                    "Joint mortality probabilities must sum exactly to 1.");
        }

        BigDecimal roundedNominalA = money(nominalA);
        BigDecimal roundedNominalB = money(nominalB);
        BigDecimal roundedRealA = money(realA);
        BigDecimal roundedRealB = money(realB);
        BigDecimal roundedPresentValueA = money(presentValueA);
        BigDecimal roundedPresentValueB = money(presentValueB);
        BigDecimal roundedNominalDifference = roundedNominalA.subtract(roundedNominalB);
        BigDecimal roundedRealDifference = roundedRealA.subtract(roundedRealB);
        BigDecimal roundedPresentValueDifference = roundedPresentValueA.subtract(
                roundedPresentValueB);

        return new SocialSecurityMortalityWeightedComparisonResult(
                request,
                matrix,
                scenarios,
                roundedNominalA,
                roundedNominalB,
                roundedNominalDifference,
                winner(roundedNominalDifference),
                roundedRealA,
                roundedRealB,
                roundedRealDifference,
                winner(roundedRealDifference),
                roundedPresentValueA,
                roundedPresentValueB,
                roundedPresentValueDifference,
                winner(roundedPresentValueDifference),
                probabilityAWins,
                probabilityBWins,
                probabilityTie,
                totalProbability);
    }

    private static BigDecimal probabilityFor(
            SocialSecurityMortalityDistribution distribution,
            int age) {
        return distribution.probabilityFor(age)
                .orElseThrow(() -> new IllegalStateException(
                        "No mortality probability exists for death age " + age + "."))
                .probability();
    }

    private static void validateRawDifference(
            BigDecimal strategyA,
            BigDecimal strategyB,
            BigDecimal weightedDifference,
            String description) {
        if (strategyA.subtract(strategyB).compareTo(weightedDifference) != 0) {
            throw new IllegalStateException(
                    "Weighted " + description + " difference did not reconcile.");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static StrategyComparisonWinner winner(BigDecimal difference) {
        return switch (difference.signum()) {
            case 1 -> StrategyComparisonWinner.STRATEGY_A;
            case -1 -> StrategyComparisonWinner.STRATEGY_B;
            default -> StrategyComparisonWinner.TIE;
        };
    }
}
