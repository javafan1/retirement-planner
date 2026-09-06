package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyComparisonEntry;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyComparisonResult;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyEvaluationFailure;
import com.daviddunn.retirementplanner.app.socialsecurity.IntegratedSocialSecurityStrategyResult;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityHouseholdClaimingStrategy;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityWeightedStrategyValue;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingCandidate;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecuritySurvivorClaimingOptimizationCell;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegratedSocialSecurityComparisonPresentationTest {

    @Test
    void retainsAnalyzerRanksAndStructuredCandidateFailures() {
        SocialSecurityStrategyAnalyzerPresentation.RankedStrategy first = ranked(1, 62);
        SocialSecurityStrategyAnalyzerPresentation.RankedStrategy second = ranked(2, 67);
        IntegratedSocialSecurityStrategyResult baseline = result(first.cell().strategy());
        IntegratedSocialSecurityStrategyComparisonEntry success =
                IntegratedSocialSecurityStrategyComparisonEntry.success(
                        1, first.cell().strategy(), Optional.of(first.cell().expectedValue()),
                        result(first.cell().strategy()), metrics());
        IntegratedSocialSecurityStrategyComparisonEntry failure =
                IntegratedSocialSecurityStrategyComparisonEntry.failure(
                        2, second.cell().strategy(), Optional.of(second.cell().expectedValue()),
                        new IntegratedSocialSecurityStrategyEvaluationFailure(
                                second.cell().strategy(), "Candidate cannot be evaluated.",
                                IntegratedSocialSecurityStrategyEvaluationFailure.Category.VALIDATION));

        IntegratedSocialSecurityComparisonPresentation presentation =
                IntegratedSocialSecurityComparisonPresentation.from(
                        new IntegratedSocialSecurityStrategyComparisonResult(
                                baseline, 2, List.of(success, failure)),
                        List.of(first, second));

        assertEquals(List.of(1, 2), presentation.rows().stream()
                .map(IntegratedSocialSecurityComparisonPresentation.Row::socialSecurityRank)
                .toList());
        assertEquals(1, presentation.successfulCount());
        assertEquals(1, presentation.failureCount());
        assertEquals("Candidate cannot be evaluated.",
                presentation.rows().get(1).entry().failure().orElseThrow().message());
    }

    private SocialSecurityStrategyAnalyzerPresentation.RankedStrategy ranked(
            int rank,
            int primaryAge) {
        LocalDate primaryBirth = LocalDate.of(1960, 1, 2);
        LocalDate spouseBirth = LocalDate.of(1962, 2, 3);
        SocialSecurityHouseholdClaimingStrategy strategy =
                new SocialSecurityHouseholdClaimingStrategy(
                        primaryAge, 62, primaryBirth.plusYears(primaryAge),
                        spouseBirth.plusYears(62), survivor(primaryBirth), survivor(spouseBirth));
        SocialSecurityMortalityWeightedStrategyValue expected =
                new SocialSecurityMortalityWeightedStrategyValue(
                        new BigDecimal("100"), new BigDecimal("90"), new BigDecimal("80"),
                        new BigDecimal("60"), new BigDecimal("40"), 1);
        return new SocialSecurityStrategyAnalyzerPresentation.RankedStrategy(
                rank, new SocialSecuritySurvivorClaimingOptimizationCell(strategy, expected));
    }

    private SocialSecuritySurvivorClaimingCandidate survivor(LocalDate birthDate) {
        return new SocialSecuritySurvivorClaimingCandidate(
                birthDate.plusYears(60), 60, 0, "Age 60");
    }

    private IntegratedSocialSecurityStrategyResult result(
            SocialSecurityHouseholdClaimingStrategy strategy) {
        return new IntegratedSocialSecurityStrategyResult(
                strategy, new Projection(), metrics(), true, List.of());
    }

    private ProjectionMetrics metrics() {
        return new ProjectionMetrics(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
