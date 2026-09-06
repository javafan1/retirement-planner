package com.daviddunn.retirementplanner.app.socialsecurity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Baseline plus deterministic row-major integrated retirement claiming cells. */
public record IntegratedRetirementClaimingGridResult(
        IntegratedSocialSecurityStrategyResult currentPlanBaseline,
        List<Integer> primaryRetirementAges,
        List<Integer> spouseRetirementAges,
        IntegratedRetirementClaimingGridSurvivorPolicy survivorPolicy,
        List<IntegratedRetirementClaimingGridCell> cells) {

    public IntegratedRetirementClaimingGridResult {
        Objects.requireNonNull(currentPlanBaseline, "Current-plan baseline is required.");
        primaryRetirementAges = List.copyOf(Objects.requireNonNull(primaryRetirementAges));
        spouseRetirementAges = List.copyOf(Objects.requireNonNull(spouseRetirementAges));
        Objects.requireNonNull(survivorPolicy, "Fixed survivor policy is required.");
        cells = List.copyOf(Objects.requireNonNull(cells, "Grid cells are required."));
        int expected = Math.multiplyExact(
                primaryRetirementAges.size(), spouseRetirementAges.size());
        if (cells.size() != expected) {
            throw new IllegalArgumentException(
                    "Grid requires " + expected + " cells but received " + cells.size() + ".");
        }
        for (int index = 0; index < cells.size(); index++) {
            int row = index / spouseRetirementAges.size();
            int column = index % spouseRetirementAges.size();
            IntegratedRetirementClaimingGridCell cell = cells.get(index);
            if (cell.primaryRetirementAge() != primaryRetirementAges.get(row)
                    || cell.spouseRetirementAge() != spouseRetirementAges.get(column)) {
                throw new IllegalArgumentException("Grid cells must be in row-major order.");
            }
        }
    }

    public Optional<IntegratedRetirementClaimingGridCell> cellFor(
            int primaryAge,
            int spouseAge) {
        return cells.stream()
                .filter(cell -> cell.primaryRetirementAge() == primaryAge
                        && cell.spouseRetirementAge() == spouseAge)
                .findFirst();
    }

    public long successfulCellCount() {
        return cells.stream().filter(IntegratedRetirementClaimingGridCell::successful).count();
    }

    public long failedCellCount() {
        return cells.size() - successfulCellCount();
    }
}
