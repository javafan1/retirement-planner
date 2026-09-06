package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedStrategyValue;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingOptimizationCell;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegratedSocialSecurityCandidateSelectorTest {

    private final IntegratedSocialSecurityCandidateSelector selector =
            new IntegratedSocialSecurityCandidateSelector();

    @Test
    void preservesAnalyzerOrderAndCutoffTies() {
        List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> ranked = List.of(
                ranked(1, 62), ranked(2, 63), ranked(2, 64), ranked(4, 65));

        List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> selected =
                selector.select(ranked, 2);

        assertEquals(3, selected.size());
        assertSame(ranked.get(0), selected.get(0));
        assertSame(ranked.get(1), selected.get(1));
        assertSame(ranked.get(2), selected.get(2));
    }

    @Test
    void returnsAllWhenFewerThanRequestedAndReturnsImmutableList() {
        List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> ranked = List.of(
                ranked(1, 62), ranked(2, 63));

        List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> selected =
                selector.select(ranked, 10);

        assertEquals(ranked, selected);
        assertThrows(UnsupportedOperationException.class,
                () -> selected.add(ranked(3, 64)));
    }

    @Test
    void validatesUiRange() {
        assertThrows(IllegalArgumentException.class,
                () -> selector.select(List.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> selector.select(List.of(), 21));
    }

    private SocialSecurityStrategyAnalyzerPresentation.RankedStrategy ranked(
            int rank,
            int primaryAge) {
        LocalDate primaryBirth = LocalDate.of(1960, 1, 2);
        LocalDate spouseBirth = LocalDate.of(1962, 2, 3);
        SocialSecurityHouseholdClaimingStrategy strategy =
                new SocialSecurityHouseholdClaimingStrategy(
                        primaryAge,
                        62,
                        primaryBirth.plusYears(primaryAge),
                        spouseBirth.plusYears(62),
                        survivor(primaryBirth),
                        survivor(spouseBirth));
        SocialSecurityMortalityWeightedStrategyValue expected =
                new SocialSecurityMortalityWeightedStrategyValue(
                        new BigDecimal("100"), new BigDecimal("90"),
                        new BigDecimal("80"), new BigDecimal("60"),
                        new BigDecimal("40"), 1);
        return new SocialSecurityStrategyAnalyzerPresentation.RankedStrategy(
                rank, new SocialSecuritySurvivorClaimingOptimizationCell(strategy, expected));
    }

    private SocialSecuritySurvivorClaimingCandidate survivor(LocalDate birthDate) {
        return new SocialSecuritySurvivorClaimingCandidate(
                birthDate.plusYears(60), 60, 0, "Age 60");
    }
}
