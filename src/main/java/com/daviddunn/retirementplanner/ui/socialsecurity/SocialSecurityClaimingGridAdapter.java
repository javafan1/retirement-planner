package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import java.math.*;
import java.util.*;

/** Stage 1 values only. No complete-strategy elections or integrated objective are invented. */
final class SocialSecurityClaimingGridAdapter {
    record Cell(SocialSecurityMortalityWeightedClaimingGridCell source, Optional<BigDecimal> percent,
                boolean optimal, boolean retained) {
        ClaimingAgeGridView.Pair pair() { return new ClaimingAgeGridView.Pair(source.primaryClaimAge(), source.spouseClaimAge()); }
        ClaimingAgeGridView.Cell display() {
            String description = "Primary age " + source.primaryClaimAge() + " / Spouse age " + source.spouseClaimAge()
                    + "\nStage 1 expected Social Security PV: " + UIFormatters.money(source.expectedPresentValue())
                    + "\nPercentage of highest Stage 1 PV: " + percent.map(value -> value.setScale(1, RoundingMode.HALF_UP) + "%")
                            .orElse("Not Available — highest PV is zero or negative")
                    + "\n" + (optimal ? "Exact Stage 1 optimum" : "Not an exact Stage 1 optimum")
                    + "\n" + (retained ? "Retained for Stage 2" : "Not retained for Stage 2");
            return new ClaimingAgeGridView.Cell(pair(), UIFormatters.money(source.expectedPresentValue()), percent, optimal, retained,
                    description, description + "\nStage 1 real benefits: " + UIFormatters.money(source.expectedRealBenefits())
                    + "\nStage 1 nominal benefits: " + UIFormatters.money(source.expectedNominalBenefits())
                    + "\nStage 2 complete survivor-election values are separate; select to inspect available details.");
        }
    }
    record Model(List<Integer> primaryAges, List<Integer> spouseAges, List<Cell> cells) {
        Model { primaryAges = List.copyOf(primaryAges); spouseAges = List.copyOf(spouseAges); cells = List.copyOf(cells); }
    }
    static Model from(SocialSecurityStrategyAnalyzerPresentation presentation) {
        var grid = presentation.result().retirementGrid();
        return from(grid, presentation::advancedToStageTwo);
    }
    static Model from(SocialSecurityMortalityWeightedClaimingGridResult grid,
                      java.util.function.BiPredicate<Integer, Integer> retained) {
        var ranking = grid.highestExpectedPresentValue();
        return new Model(grid.primaryClaimAges(), grid.spouseClaimAges(), grid.cells().stream().map(cell -> new Cell(cell,
                ranking.highestValue().signum() <= 0 ? Optional.empty() : Optional.of(cell.expectedPresentValue()
                        // Fixed-scale truncation preserves every finite-decimal palette threshold;
                        // nearest rounding could promote a value infinitesimally below a boundary.
                        .multiply(BigDecimal.valueOf(100)).divide(ranking.highestValue(), 34, RoundingMode.DOWN)),
                ranking.highestCells().contains(cell), retained.test(cell.primaryClaimAge(), cell.spouseClaimAge()))).toList());
    }
}
