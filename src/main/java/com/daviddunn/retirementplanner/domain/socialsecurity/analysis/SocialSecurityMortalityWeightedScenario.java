package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.Objects;

/** One probability-weighted deterministic longevity scenario. */
public record SocialSecurityMortalityWeightedScenario(
        int primaryDeathAge,
        int spouseDeathAge,
        BigDecimal primaryDeathProbability,
        BigDecimal spouseDeathProbability,
        BigDecimal jointProbability,
        SocialSecurityDeathAgeMatrixCell matrixCell) {

    public SocialSecurityMortalityWeightedScenario {
        Objects.requireNonNull(primaryDeathProbability, "Primary probability is required.");
        Objects.requireNonNull(spouseDeathProbability, "Spouse probability is required.");
        Objects.requireNonNull(jointProbability, "Joint probability is required.");
        Objects.requireNonNull(matrixCell, "Deterministic matrix cell is required.");
        if (matrixCell.primaryDeathAge() != primaryDeathAge
                || matrixCell.spouseDeathAge() != spouseDeathAge) {
            throw new IllegalArgumentException(
                    "Mortality scenario coordinate must match its matrix cell.");
        }
    }

    public BigDecimal weightedNominalStrategyA() {
        return weight(matrixCell.comparison().strategyA().nominalLifetimeBenefits());
    }

    public BigDecimal weightedNominalStrategyB() {
        return weight(matrixCell.comparison().strategyB().nominalLifetimeBenefits());
    }

    public BigDecimal weightedNominalDifference() {
        return weight(matrixCell.nominalDifference());
    }

    public BigDecimal weightedRealStrategyA() {
        return weight(matrixCell.comparison().strategyA().realLifetimeBenefits());
    }

    public BigDecimal weightedRealStrategyB() {
        return weight(matrixCell.comparison().strategyB().realLifetimeBenefits());
    }

    public BigDecimal weightedRealDifference() {
        return weight(matrixCell.realDifference());
    }

    public BigDecimal weightedPresentValueStrategyA() {
        return weight(matrixCell.comparison().strategyA().presentValue());
    }

    public BigDecimal weightedPresentValueStrategyB() {
        return weight(matrixCell.comparison().strategyB().presentValue());
    }

    public BigDecimal weightedPresentValueDifference() {
        return weight(matrixCell.presentValueDifference());
    }

    private BigDecimal weight(BigDecimal value) {
        return jointProbability.multiply(
                value,
                SocialSecurityIndependentJointMortalityCalculator.MATH_CONTEXT);
    }
}
