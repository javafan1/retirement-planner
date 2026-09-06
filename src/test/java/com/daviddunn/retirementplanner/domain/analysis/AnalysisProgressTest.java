package com.daviddunn.retirementplanner.domain.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisProgressTest {

    @Test
    void calculatesBoundaryPercentagesWithoutDividingByZero() {
        assertEquals(100, new AnalysisProgress(
                AnalysisPhase.CURRENT_PLAN_BASELINE, 0, 0).wholePercent());
        assertEquals(0, new AnalysisProgress(
                AnalysisPhase.QUICK_COMPARISON_CANDIDATES, 0, 10).wholePercent());
        assertEquals(10, new AnalysisProgress(
                AnalysisPhase.QUICK_COMPARISON_CANDIDATES, 1, 10).wholePercent());
        assertEquals(50, new AnalysisProgress(
                AnalysisPhase.QUICK_COMPARISON_CANDIDATES, 5, 10).wholePercent());
        assertEquals(100, new AnalysisProgress(
                AnalysisPhase.QUICK_COMPARISON_CANDIDATES, 10, 10).wholePercent());
        assertThrows(IllegalArgumentException.class, () -> new AnalysisProgress(
                AnalysisPhase.QUICK_COMPARISON_CANDIDATES, 11, 10));
    }
}
