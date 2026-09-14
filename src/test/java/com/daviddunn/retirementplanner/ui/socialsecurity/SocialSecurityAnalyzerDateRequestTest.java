package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityAnalyzerDateRequestTest {
    static SocialSecurityStrategyAnalysisContext ss(LocalDate conditioning, LocalDate valuation) {
        return new SocialSecurityStrategyAnalysisRequestFactory().create(LongevityWeightedAnalysisRequestFactoryTest.plan(),
                SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard(),
                new BigDecimal("0.01"), conditioning, valuation);
    }

    @Test
    void ssDatesHaveIndependentRolesAndCompatibilityDefaultsRemainCoupledOnlyAtCapture() {
        var date = LocalDate.of(2026, 7, 1);
        var old = new SocialSecurityStrategyAnalysisRequestFactory().create(LongevityWeightedAnalysisRequestFactoryTest.plan(),
                SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityCategory.FEMALE, new BigDecimal("0.01"), date);
        var separate = ss(date, date.minusYears(1));
        assertEquals(date, old.longevityAssumptions().mortalityBaseDate());
        assertEquals(date, old.request().retirementGridRequest().presentValueBaseDate());
        assertEquals(date, separate.longevityAssumptions().mortalityBaseDate());
        assertEquals(date.minusYears(1), separate.request().retirementGridRequest().presentValueBaseDate());
        assertEquals(old.request().retirementGridRequest().baseStrategy(), separate.request().retirementGridRequest().baseStrategy());
        assertEquals(old.request().retirementGridRequest().primaryMortality().probabilities(), separate.request().retirementGridRequest().primaryMortality().probabilities());
        assertEquals(date, separate.request().retirementGridRequest().baseStrategy().analysisDate());
        assertThrows(NullPointerException.class, () -> ss(null, date));
        assertThrows(NullPointerException.class, () -> ss(date, null));
    }

    @Test
    void weightedCapturesIndependentDatesAndPlanStartWithoutMutatingFrozenInputs() throws Exception {
        var plan = LongevityWeightedAnalysisRequestFactoryTest.plan();
        var factory = new LongevityWeightedAnalysisRequestFactory();
        var conditioning = LocalDate.of(2027, 1, 1);
        var valuation = LocalDate.of(2025, 1, 1);
        var snapshot = factory.capture(plan, SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard(),
                conditioning, valuation, new BigDecimal("0.01"), 1, 2);
        var request = factory.create(snapshot, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(conditioning, request.longevityScenarios().assumptions().mortalityBaseDate());
        assertEquals(valuation, request.valuationDate());
        var frozenField = request.getClass().getDeclaredField("frozenPlan");
        frozenField.setAccessible(true);
        var frozenPlan = (com.daviddunn.retirementplanner.domain.model.RetirementPlan) frozenField.get(request);
        assertEquals(LocalDate.of(2026, 7, 1), frozenPlan.getPlanningAssumptions().getProjectionStartDate());
        factory.capture(plan, SocialSecurityMortalityCategory.MALE, SocialSecurityMortalityCategory.FEMALE,
                SocialSecurityMortalityAdjustment.standard(), SocialSecurityMortalityAdjustment.standard(),
                conditioning.plusYears(1), valuation.plusYears(1), new BigDecimal("0.01"), 1, 3);
        assertEquals(conditioning, snapshot.longevity.mortalityBaseDate());
        assertEquals(valuation, snapshot.valuationDate);
        assertEquals(LocalDate.of(2026, 7, 1), plan.getPlanningAssumptions().getProjectionStartDate());
    }
}
