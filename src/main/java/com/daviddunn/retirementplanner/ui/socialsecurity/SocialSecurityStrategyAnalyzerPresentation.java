package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure presentation transformation for tables, ties, and grid details. */
public record SocialSecurityStrategyAnalyzerPresentation(
        SocialSecuritySurvivorClaimingOptimizationResult result,
        List<RankedStrategy> rankedStrategies) {

    public SocialSecurityStrategyAnalyzerPresentation {
        Objects.requireNonNull(result);
        rankedStrategies = List.copyOf(Objects.requireNonNull(rankedStrategies));
    }

    public static SocialSecurityStrategyAnalyzerPresentation from(
            SocialSecuritySurvivorClaimingOptimizationResult result) {
        List<SocialSecuritySurvivorClaimingOptimizationCell> ordered =
                result.evaluatedStrategies().stream()
                        .sorted(Comparator.comparing(
                                        SocialSecuritySurvivorClaimingOptimizationCell
                                                ::expectedPresentValue)
                                .reversed())
                        .toList();
        java.util.ArrayList<RankedStrategy> ranked = new java.util.ArrayList<>();
        BigDecimal previous = null;
        int rank = 0;
        for (int index = 0; index < ordered.size(); index++) {
            SocialSecuritySurvivorClaimingOptimizationCell cell = ordered.get(index);
            if (previous == null
                    || cell.expectedPresentValue().compareTo(previous) != 0) {
                rank = index + 1;
                previous = cell.expectedPresentValue();
            }
            ranked.add(new RankedStrategy(rank, cell));
        }
        return new SocialSecurityStrategyAnalyzerPresentation(result, ranked);
    }

    public Optional<SocialSecuritySurvivorClaimingOptimizationCell> bestForRetirement(
            int primaryAge,
            int spouseAge) {
        return rankedStrategies.stream()
                .map(RankedStrategy::cell)
                .filter(cell -> cell.strategy().primaryRetirementAge() == primaryAge
                        && cell.strategy().spouseRetirementAge() == spouseAge)
                .findFirst();
    }

    public boolean advancedToStageTwo(int primaryAge, int spouseAge) {
        return result.retainedRetirementCells().stream().anyMatch(
                cell -> cell.primaryClaimAge() == primaryAge
                        && cell.spouseClaimAge() == spouseAge);
    }

    public record RankedStrategy(
            int rank,
            SocialSecuritySurvivorClaimingOptimizationCell cell) {
    }
}
