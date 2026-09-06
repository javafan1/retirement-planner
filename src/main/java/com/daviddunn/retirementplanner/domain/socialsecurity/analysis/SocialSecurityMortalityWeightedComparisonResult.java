package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Complete auditable mortality-weighted comparison of two supplied strategies. */
public record SocialSecurityMortalityWeightedComparisonResult(
        SocialSecurityMortalityWeightedComparisonRequest request,
        SocialSecurityDeathAgeMatrixResult deterministicMatrix,
        List<SocialSecurityMortalityWeightedScenario> scenarios,
        BigDecimal expectedNominalStrategyA,
        BigDecimal expectedNominalStrategyB,
        BigDecimal expectedNominalDifference,
        StrategyComparisonWinner expectedNominalWinner,
        BigDecimal expectedRealStrategyA,
        BigDecimal expectedRealStrategyB,
        BigDecimal expectedRealDifference,
        StrategyComparisonWinner expectedRealWinner,
        BigDecimal expectedPresentValueStrategyA,
        BigDecimal expectedPresentValueStrategyB,
        BigDecimal expectedPresentValueDifference,
        StrategyComparisonWinner expectedPresentValueWinner,
        BigDecimal probabilityStrategyAWinsPresentValue,
        BigDecimal probabilityStrategyBWinsPresentValue,
        BigDecimal probabilityPresentValueTie,
        BigDecimal totalJointProbability) {

    public SocialSecurityMortalityWeightedComparisonResult {
        Objects.requireNonNull(request, "Mortality-weighted request is required.");
        Objects.requireNonNull(deterministicMatrix, "Deterministic matrix is required.");
        scenarios = List.copyOf(
                Objects.requireNonNull(scenarios, "Weighted scenarios are required."));
        require(expectedNominalStrategyA, "Expected nominal Strategy A is required.");
        require(expectedNominalStrategyB, "Expected nominal Strategy B is required.");
        require(expectedNominalDifference, "Expected nominal difference is required.");
        require(expectedNominalWinner, "Expected nominal winner is required.");
        require(expectedRealStrategyA, "Expected real Strategy A is required.");
        require(expectedRealStrategyB, "Expected real Strategy B is required.");
        require(expectedRealDifference, "Expected real difference is required.");
        require(expectedRealWinner, "Expected real winner is required.");
        require(expectedPresentValueStrategyA, "Expected PV Strategy A is required.");
        require(expectedPresentValueStrategyB, "Expected PV Strategy B is required.");
        require(expectedPresentValueDifference, "Expected PV difference is required.");
        require(expectedPresentValueWinner, "Expected PV winner is required.");
        require(probabilityStrategyAWinsPresentValue, "Strategy A win probability is required.");
        require(probabilityStrategyBWinsPresentValue, "Strategy B win probability is required.");
        require(probabilityPresentValueTie, "PV tie probability is required.");
        require(totalJointProbability, "Total joint probability is required.");

        if (scenarios.size() != deterministicMatrix.cells().size()) {
            throw new IllegalArgumentException(
                    "Weighted scenarios must correspond one-for-one with matrix cells.");
        }
        if (totalJointProbability.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Total joint probability must equal 1.");
        }
        BigDecimal outcomeProbability = probabilityStrategyAWinsPresentValue
                .add(probabilityStrategyBWinsPresentValue)
                .add(probabilityPresentValueTie);
        if (outcomeProbability.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException(
                    "Present-value outcome probabilities must sum to 1.");
        }
        validateDifference(
                expectedNominalStrategyA,
                expectedNominalStrategyB,
                expectedNominalDifference,
                "Expected nominal difference");
        validateDifference(
                expectedRealStrategyA,
                expectedRealStrategyB,
                expectedRealDifference,
                "Expected real difference");
        validateDifference(
                expectedPresentValueStrategyA,
                expectedPresentValueStrategyB,
                expectedPresentValueDifference,
                "Expected present-value difference");
    }

    public Optional<SocialSecurityMortalityWeightedScenario> scenarioFor(
            int primaryDeathAge,
            int spouseDeathAge) {
        return scenarios.stream()
                .filter(scenario -> scenario.primaryDeathAge() == primaryDeathAge
                        && scenario.spouseDeathAge() == spouseDeathAge)
                .findFirst();
    }

    private static void require(Object value, String message) {
        Objects.requireNonNull(value, message);
    }

    private static void validateDifference(
            BigDecimal strategyA,
            BigDecimal strategyB,
            BigDecimal difference,
            String description) {
        if (strategyA.subtract(strategyB).compareTo(difference) != 0) {
            throw new IllegalArgumentException(description + " must equal A minus B.");
        }
    }
}
