package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.domain.analysis.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityAnalysisProgressModelTest {
    @Test
    void phasesAndCountsNeverMoveBackward() {
        var model = new SocialSecurityAnalysisProgressModel();
        model.accept(new AnalysisProgress(AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE, 2, 3));
        model.accept(new AnalysisProgress(AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE, 1, 3));
        assertEquals(2, model.update().completedWork());
        model.accept(new AnalysisProgress(AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON, 0, 5184));
        model.accept(new AnalysisProgress(AnalysisPhase.LONGEVITY_STRATEGY_EQUIVALENCE, 3, 3));
        assertEquals(AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON, model.update().phase());
        model.cancel();
        model.accept(new AnalysisProgress(AnalysisPhase.LONGEVITY_INTEGRATED_COMPARISON, 5184, 5184));
        assertEquals("Cancelling…", model.text());
    }
    @Test
    void failureExplanationsSeparateMissingBaselineUnsupportedAndFatal() {
        assertTrue(SocialSecurityAnalysisFailurePresentation.message(new IllegalArgumentException(
                "requires complete survivor policy")).contains("no survivor age is assumed"));
        assertTrue(SocialSecurityAnalysisFailurePresentation.message(new IllegalArgumentException(
                "modern-cohort advanced path")).contains("two supported modern-cohort people"));
        assertTrue(SocialSecurityAnalysisFailurePresentation.message(new IllegalStateException(
                "private debug detail")).startsWith("Analysis failed before completion"));
    }}
