package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Exact maximum expected value and every tested claiming cell tied at it. */
public record SocialSecurityMortalityWeightedClaimingGridRanking(
        SocialSecurityExpectedValueMeasure measure,
        BigDecimal highestValue,
        List<SocialSecurityMortalityWeightedClaimingGridCell> highestCells) {

    public SocialSecurityMortalityWeightedClaimingGridRanking {
        Objects.requireNonNull(measure, "Expected-value measure is required.");
        Objects.requireNonNull(highestValue, "Highest expected value is required.");
        highestCells = List.copyOf(Objects.requireNonNull(
                highestCells,
                "Highest tested cells are required."));
        if (highestCells.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ranking must contain at least one highest tested cell.");
        }
        if (highestCells.stream().anyMatch(
                cell -> value(cell, measure).compareTo(highestValue) != 0)) {
            throw new IllegalArgumentException(
                    "Every ranking cell must equal the highest expected value.");
        }
    }

    static BigDecimal value(
            SocialSecurityMortalityWeightedClaimingGridCell cell,
            SocialSecurityExpectedValueMeasure measure) {
        return switch (measure) {
            case EXPECTED_NOMINAL -> cell.expectedNominalBenefits();
            case EXPECTED_REAL -> cell.expectedRealBenefits();
            case EXPECTED_PRESENT_VALUE -> cell.expectedPresentValue();
        };
    }
}
