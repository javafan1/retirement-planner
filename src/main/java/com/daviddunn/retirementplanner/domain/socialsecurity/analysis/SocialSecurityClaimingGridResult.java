package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable row-major claiming grid: primary rows, spouse columns. */
public record SocialSecurityClaimingGridResult(
        SocialSecurityClaimingGridRequest request,
        List<Integer> primaryClaimAges,
        List<Integer> spouseClaimAges,
        List<SocialSecurityClaimingGridCell> cells,
        SocialSecurityClaimingGridRanking highestNominal,
        SocialSecurityClaimingGridRanking highestReal,
        SocialSecurityClaimingGridRanking highestPresentValue) {

    public SocialSecurityClaimingGridResult {
        Objects.requireNonNull(request, "Grid request is required.");
        primaryClaimAges = List.copyOf(
                Objects.requireNonNull(
                        primaryClaimAges,
                        "Primary claim ages are required."));
        spouseClaimAges = List.copyOf(
                Objects.requireNonNull(
                        spouseClaimAges,
                        "Spouse claim ages are required."));
        cells = List.copyOf(
                Objects.requireNonNull(cells, "Grid cells are required."));
        Objects.requireNonNull(highestNominal, "Nominal ranking is required.");
        Objects.requireNonNull(highestReal, "Real ranking is required.");
        Objects.requireNonNull(
                highestPresentValue,
                "Present-value ranking is required.");

        int expectedCells = Math.multiplyExact(
                primaryClaimAges.size(),
                spouseClaimAges.size());
        if (cells.size() != expectedCells) {
            throw new IllegalArgumentException(
                    "Grid must contain exactly one cell for every coordinate.");
        }
        int index = 0;
        for (int primaryAge : primaryClaimAges) {
            for (int spouseAge : spouseClaimAges) {
                SocialSecurityClaimingGridCell cell = cells.get(index++);
                if (cell.primaryClaimAge() != primaryAge
                        || cell.spouseClaimAge() != spouseAge) {
                    throw new IllegalArgumentException(
                            "Grid cells must be in primary-row/spouse-column order.");
                }
            }
        }
        validateRanking(highestNominal,
                SocialSecurityClaimingGridMeasure.NOMINAL_LIFETIME,
                cells);
        validateRanking(highestReal,
                SocialSecurityClaimingGridMeasure.REAL_LIFETIME,
                cells);
        validateRanking(highestPresentValue,
                SocialSecurityClaimingGridMeasure.PRESENT_VALUE,
                cells);
    }

    public Optional<SocialSecurityClaimingGridCell> cellFor(
            int primaryClaimAge,
            int spouseClaimAge) {
        return cells.stream()
                .filter(cell -> cell.primaryClaimAge() == primaryClaimAge
                        && cell.spouseClaimAge() == spouseClaimAge)
                .findFirst();
    }

    private static void validateRanking(
            SocialSecurityClaimingGridRanking ranking,
            SocialSecurityClaimingGridMeasure expectedMeasure,
            List<SocialSecurityClaimingGridCell> cells) {
        if (ranking.measure() != expectedMeasure
                || !cells.containsAll(ranking.highestCells())) {
            throw new IllegalArgumentException(
                    "Grid ranking must reference cells from the matching measure.");
        }
        for (SocialSecurityClaimingGridCell cell : cells) {
            if (SocialSecurityClaimingGridRanking.value(cell, expectedMeasure)
                    .compareTo(ranking.highestValue()) > 0) {
                throw new IllegalArgumentException(
                        "Grid ranking does not contain the highest tested value.");
            }
        }
    }
}
