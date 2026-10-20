package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisSuccessPolicyTest {
    @Test void quickAndDeterministicFamiliesRequireAtLeastOneSuccessfulCandidate() {
        var r = IntegratedAnalyzerPdfReportTest.deterministic().result();
        assertTrue(AnalysisSuccessPolicy.accepted(r));
        assertTrue(AnalysisSuccessPolicy.accepted(new SinglePersonIntegratedAnalysis(r, Map.of(), "fixture", 2026, 2027, Optional.empty())));
        var strategy = r.entries().getFirst().strategy();
        var failure = new IntegratedSocialSecurityStrategyEvaluationFailure(strategy, "Unavailable", IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION);
        var failed = IntegratedSocialSecurityStrategyComparisonEntry.failure(1, strategy, Optional.empty(), failure);
        var successful = IntegratedSocialSecurityStrategyComparisonEntry.success(2, strategy, Optional.empty(), r.currentPlanBaseline(), r.currentPlanBaseline().metrics());
        assertFalse(AnalysisSuccessPolicy.accepted(new IntegratedSocialSecurityStrategyComparisonResult(r.currentPlanBaseline(), 1, List.of(failed))));
        assertTrue(AnalysisSuccessPolicy.accepted(new IntegratedSocialSecurityStrategyComparisonResult(r.currentPlanBaseline(), 2, List.of(failed, successful))));
        var bad = new IntegratedSocialSecurityCompleteStrategySearchEntry(1, strategy, OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(failure));
        var allFailed = new IntegratedSocialSecurityCompleteStrategySearchResult(r.currentPlanBaseline(), r.rankingMeasure(), List.of(bad), List.of(), OptionalInt.empty());
        assertFalse(AnalysisSuccessPolicy.accepted(allFailed));
        assertFalse(AnalysisSuccessPolicy.accepted(new SinglePersonIntegratedAnalysis(allFailed, Map.of(), "fixture", 2026, 2027, Optional.empty())));
    }
    @Test void socialSecurityFamilyUsesACompletedOptimizationNotPartialWorkerOutput() {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var context = new SocialSecurityStrategyAnalysisRequestFactory().create(plan, SocialSecurityMortalityAdjustment.standard(),
                SocialSecurityMortalityAdjustment.standard(), BigDecimal.ZERO, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1));
        var old = context.request().retirementGridRequest();
        var mortality = new SocialSecurityMortalityDistribution(List.of(new SocialSecurityMortalityProbability(120, BigDecimal.ONE)));
        var grid = new SocialSecurityMortalityWeightedClaimingGridRequest(old.baseStrategy(), List.of(67), List.of(67), mortality, mortality,
                old.mortalityBaseDate(), old.presentValueBaseDate(), old.realDiscountRate());
        var result = new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(new SocialSecuritySurvivorClaimingOptimizationRequest(grid));
        assertTrue(AnalysisSuccessPolicy.accepted(result));
        assertEquals(result.stageTwoStrategyCount(), result.evaluatedStrategies().size());
    }
}
