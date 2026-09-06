package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ordered mortality-weighted claiming grid and its measure-specific maxima. */
public record SocialSecurityMortalityWeightedClaimingGridResult(
        SocialSecurityMortalityWeightedClaimingGridRequest request,
        List<Integer> primaryClaimAges,
        List<Integer> spouseClaimAges,
        List<SocialSecurityJointMortalityScenario> jointMortalityScenarios,
        List<SocialSecurityMortalityWeightedClaimingGridCell> cells,
        SocialSecurityMortalityWeightedClaimingGridRanking highestExpectedNominal,
        SocialSecurityMortalityWeightedClaimingGridRanking highestExpectedReal,
        SocialSecurityMortalityWeightedClaimingGridRanking highestExpectedPresentValue,
        int strategyEvaluationCount) {

    public SocialSecurityMortalityWeightedClaimingGridResult {
        Objects.requireNonNull(request, "Grid request is required.");
        primaryClaimAges = List.copyOf(Objects.requireNonNull(primaryClaimAges));
        spouseClaimAges = List.copyOf(Objects.requireNonNull(spouseClaimAges));
        jointMortalityScenarios = List.copyOf(Objects.requireNonNull(
                jointMortalityScenarios));
        cells = List.copyOf(Objects.requireNonNull(cells));
        Objects.requireNonNull(highestExpectedNominal);
        Objects.requireNonNull(highestExpectedReal);
        Objects.requireNonNull(highestExpectedPresentValue);
        int expectedCells = Math.multiplyExact(
                primaryClaimAges.size(), spouseClaimAges.size());
        if (cells.size() != expectedCells) {
            throw new IllegalArgumentException("Grid cell count must equal axis product.");
        }
        int expectedEvaluations = Math.multiplyExact(
                cells.size(), jointMortalityScenarios.size());
        if (strategyEvaluationCount != expectedEvaluations) {
            throw new IllegalArgumentException(
                    "Strategy evaluation count must equal cells times mortality scenarios.");
        }
    }

    public Optional<SocialSecurityMortalityWeightedClaimingGridCell> cellFor(
            int primaryClaimAge,
            int spouseClaimAge) {
        return cells.stream()
                .filter(cell -> cell.primaryClaimAge() == primaryClaimAge
                        && cell.spouseClaimAge() == spouseClaimAge)
                .findFirst();
    }
}
