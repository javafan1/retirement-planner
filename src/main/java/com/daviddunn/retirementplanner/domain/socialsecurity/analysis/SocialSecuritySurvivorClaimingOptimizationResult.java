package com.daviddunn.retirementplanner.domain.socialsecurity.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Auditable Stage-1 context and compact Stage-2 expected-PV ranking. */
public record SocialSecuritySurvivorClaimingOptimizationResult(
        SocialSecuritySurvivorClaimingOptimizationRequest request,
        SocialSecurityMortalityWeightedClaimingGridResult retirementGrid,
        List<SocialSecurityMortalityWeightedClaimingGridCell> retainedRetirementCells,
        List<SocialSecuritySurvivorClaimingCandidate> primarySurvivorCandidates,
        List<SocialSecuritySurvivorClaimingCandidate> spouseSurvivorCandidates,
        List<SocialSecuritySurvivorClaimingOptimizationCell> evaluatedStrategies,
        BigDecimal highestExpectedPresentValue,
        List<SocialSecuritySurvivorClaimingOptimizationCell> highestExpectedPresentValueStrategies,
        int stageTwoStrategyCount,
        int stageTwoDeterministicEvaluationCount) {

    public SocialSecuritySurvivorClaimingOptimizationResult {
        Objects.requireNonNull(request);
        Objects.requireNonNull(retirementGrid);
        retainedRetirementCells = List.copyOf(Objects.requireNonNull(retainedRetirementCells));
        primarySurvivorCandidates = List.copyOf(Objects.requireNonNull(primarySurvivorCandidates));
        spouseSurvivorCandidates = List.copyOf(Objects.requireNonNull(spouseSurvivorCandidates));
        evaluatedStrategies = List.copyOf(Objects.requireNonNull(evaluatedStrategies));
        Objects.requireNonNull(highestExpectedPresentValue);
        highestExpectedPresentValueStrategies = List.copyOf(Objects.requireNonNull(
                highestExpectedPresentValueStrategies));
        int expectedStrategies = Math.multiplyExact(
                retainedRetirementCells.size(),
                Math.multiplyExact(primarySurvivorCandidates.size(), spouseSurvivorCandidates.size()));
        if (stageTwoStrategyCount != expectedStrategies
                || evaluatedStrategies.size() != expectedStrategies) {
            throw new IllegalArgumentException("Stage-2 strategy count must reconcile.");
        }
        int expectedEvaluations = Math.multiplyExact(
                stageTwoStrategyCount,
                retirementGrid.jointMortalityScenarios().size());
        if (stageTwoDeterministicEvaluationCount != expectedEvaluations) {
            throw new IllegalArgumentException("Stage-2 evaluation count must reconcile.");
        }
        if (highestExpectedPresentValueStrategies.isEmpty()
                || highestExpectedPresentValueStrategies.stream().anyMatch(
                        cell -> cell.expectedPresentValue().compareTo(
                                highestExpectedPresentValue) != 0)) {
            throw new IllegalArgumentException("Highest expected-PV strategies must reconcile.");
        }
    }
}
