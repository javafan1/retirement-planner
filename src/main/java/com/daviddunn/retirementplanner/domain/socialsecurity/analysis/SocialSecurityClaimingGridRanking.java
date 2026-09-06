package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Exact highest tested value and every cell tied at that value. */
public record SocialSecurityClaimingGridRanking(
        SocialSecurityClaimingGridMeasure measure,
        BigDecimal highestValue,
        List<SocialSecurityClaimingGridCell> highestCells) {

    public SocialSecurityClaimingGridRanking {
        Objects.requireNonNull(measure, "Ranking measure is required.");
        Objects.requireNonNull(highestValue, "Highest value is required.");
        highestCells = List.copyOf(
                Objects.requireNonNull(
                        highestCells,
                        "Highest tested cells are required."));
        if (highestCells.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ranking must contain at least one highest tested cell.");
        }
        if (highestCells.stream().anyMatch(
                cell -> value(cell, measure).compareTo(highestValue) != 0)) {
            throw new IllegalArgumentException(
                    "Every ranking cell must equal the highest tested value.");
        }
    }

    static BigDecimal value(
            SocialSecurityClaimingGridCell cell,
            SocialSecurityClaimingGridMeasure measure) {
        return switch (measure) {
            case NOMINAL_LIFETIME -> cell.nominalLifetimeBenefits();
            case REAL_LIFETIME -> cell.realLifetimeBenefits();
            case PRESENT_VALUE -> cell.presentValue();
        };
    }
}
