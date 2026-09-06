package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable row-major matrix: primary ages are rows, spouse ages columns. */
public record SocialSecurityDeathAgeMatrixResult(
        SocialSecurityDeathAgeMatrixRequest request,
        List<Integer> primaryDeathAges,
        List<Integer> spouseDeathAges,
        List<SocialSecurityDeathAgeMatrixCell> cells) {

    public SocialSecurityDeathAgeMatrixResult {
        Objects.requireNonNull(request, "Matrix request is required.");
        primaryDeathAges = List.copyOf(
                Objects.requireNonNull(
                        primaryDeathAges,
                        "Primary death ages are required."));
        spouseDeathAges = List.copyOf(
                Objects.requireNonNull(
                        spouseDeathAges,
                        "Spouse death ages are required."));
        cells = List.copyOf(
                Objects.requireNonNull(cells, "Matrix cells are required."));

        int expectedCellCount = Math.multiplyExact(
                primaryDeathAges.size(),
                spouseDeathAges.size());
        if (cells.size() != expectedCellCount) {
            throw new IllegalArgumentException(
                    "Matrix must contain exactly one cell for every coordinate.");
        }

        int index = 0;
        for (int primaryAge : primaryDeathAges) {
            for (int spouseAge : spouseDeathAges) {
                SocialSecurityDeathAgeMatrixCell cell = cells.get(index++);
                if (cell.primaryDeathAge() != primaryAge
                        || cell.spouseDeathAge() != spouseAge) {
                    throw new IllegalArgumentException(
                            "Matrix cells must be in primary-row/spouse-column order.");
                }
            }
        }
    }

    public Optional<SocialSecurityDeathAgeMatrixCell> cellFor(
            int primaryDeathAge,
            int spouseDeathAge) {
        return cells.stream()
                .filter(cell -> cell.primaryDeathAge() == primaryDeathAge
                        && cell.spouseDeathAge() == spouseDeathAge)
                .findFirst();
    }

    public Map<StrategyComparisonWinner, Long> presentValueWinnerCounts() {
        Map<StrategyComparisonWinner, Long> counts = new EnumMap<>(
                StrategyComparisonWinner.class);
        for (StrategyComparisonWinner winner : StrategyComparisonWinner.values()) {
            counts.put(winner, 0L);
        }
        for (SocialSecurityDeathAgeMatrixCell cell : cells) {
            counts.compute(
                    cell.presentValueWinner(),
                    (winner, count) -> count + 1L);
        }
        return Map.copyOf(counts);
    }
}
