package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuickComparisonPrecisionTest {
    @Test
    void candidateCutoffUsesPrecisePvEvenWhenDisplayedDollarsAndCentsAreEqual() {
        var base = new SocialSecurityStrategyRequest(LocalDate.of(2025, 1, 1), null,
                new SocialSecurityClaimingElection(AccountOwnership.PRIMARY, LocalDate.of(1963, 6, 4),
                        new BigDecimal("3000"), 2025, LocalDate.of(2026, 6, 4)),
                new SocialSecurityClaimingElection(AccountOwnership.SPOUSE, LocalDate.of(1965, 2, 28),
                        new BigDecimal("2000"), 2025, LocalDate.of(2032, 2, 28)),
                LocalDate.of(2030, 6, 4), LocalDate.of(2032, 2, 28),
                LocalDate.of(2053, 6, 4), LocalDate.of(2055, 2, 28), new BigDecimal("0.02"));
        var mortality = new SocialSecurityMortalityDistribution(List.of(
                new SocialSecurityMortalityProbability(90, BigDecimal.ONE)));
        var primarySurvivor = new SocialSecuritySurvivorClaimingCandidate(base.primarySurvivorClaimDate(), 67, 0, "Age 67");
        var spouseSurvivor = new SocialSecuritySurvivorClaimingCandidate(base.spouseSurvivorClaimDate(), 67, 0, "Age 67");
        for (int year : List.of(2025, 2026)) {
            var request = new SocialSecurityMortalityWeightedClaimingGridRequest(base, List.of(63, 69), List.of(67),
                    mortality, mortality, LocalDate.of(2025, 1, 1), LocalDate.of(year, 1, 1),
                    new BigDecimal("0.05559856779873371124267578125"));
            var grid = new SocialSecurityMortalityWeightedClaimingGridCalculator().calculate(request);
            var cells = grid.cells().stream().map(cell -> new SocialSecuritySurvivorClaimingOptimizationCell(
                    new SocialSecurityHouseholdClaimingStrategy(cell.primaryClaimAge(), cell.spouseClaimAge(),
                            cell.primaryClaimDate(), cell.spouseClaimDate(), primarySurvivor, spouseSurvivor),
                    cell.expectedValue())).toList();
            var highest = cells.stream().max(Comparator.comparing(
                    SocialSecuritySurvivorClaimingOptimizationCell::expectedPresentValue)).orElseThrow();
            var result = new SocialSecuritySurvivorClaimingOptimizationResult(
                    new SocialSecuritySurvivorClaimingOptimizationRequest(request), grid, grid.cells(),
                    List.of(primarySurvivor), List.of(spouseSurvivor), cells, highest.expectedPresentValue(),
                    List.of(highest), 2, 2);
            var ranked = SocialSecurityStrategyAnalyzerPresentation.from(result).rankedStrategies();
            assertEquals(List.of(1, 2), ranked.stream().map(
                    SocialSecurityStrategyAnalyzerPresentation.RankedStrategy::rank).toList());
            var a = ranked.getFirst().cell().expectedPresentValue();
            var b = ranked.getLast().cell().expectedPresentValue();
            assertEquals(a.setScale(2, RoundingMode.HALF_UP), b.setScale(2, RoundingMode.HALF_UP));
            assertEquals(UIFormatters.money(a), UIFormatters.money(b));
            assertTrue(a.compareTo(b) > 0);
            var selected = new IntegratedSocialSecurityCandidateSelector().select(ranked, 1);
            assertEquals(1, selected.size());
            assertEquals(63, selected.getFirst().cell().strategy().primaryRetirementAge());
        }
    }
}
