package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloProgressTest {
    @Test
    void progressIsMonotonicBoundedAndPreservesSeededResults() {
        var plan = MonteCarloFundingTest.plan("10000", "100", 2);
        var settings = MonteCarloSettings.forPlan(plan, 250, 417, new BigDecimal("0.12"));
        var analyzer = new MonteCarloAnalyzer();
        List<AnalysisProgress> updates = new ArrayList<>();
        var observed = analyzer.analyze(plan, settings, null, updates::add, AnalysisCancellationToken.none());
        assertEquals(analyzer.analyze(plan, settings), observed);
        assertEquals(0, updates.getFirst().completedWork());
        assertEquals(250, updates.getLast().completedWork());
        assertTrue(updates.size() <= 101);
        int previous = -1;
        for (var update : updates) {
            assertEquals(AnalysisPhase.MONTE_CARLO_SIMULATIONS, update.phase());
            assertEquals(250, update.totalWork());
            assertTrue(update.completedWork() > previous);
            previous = update.completedWork();
        }
    }

    @Test
    void cancellationAtBoundaryStopsPathsAndPreservesPlan() throws Exception {
        var plan = MonteCarloFundingTest.plan("250", "100", 4);
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String before = mapper.writeValueAsString(plan);
        var stop = new AtomicBoolean();
        var paths = new AtomicInteger();
        assertThrows(AnalysisCancelledException.class, () -> new MonteCarloAnalyzer().analyze(
                plan, MonteCarloSettings.forPlan(plan, 10, 417, BigDecimal.ZERO), null,
                index -> {
                    paths.incrementAndGet();
                    return ProjectionEconomicPath.constant(BigDecimal.ZERO);
                }, progress -> {
                    if (progress.completedWork() == 3) {
                        stop.set(true);
                    }
                }, stop::get));
        assertEquals(3, paths.get());
        assertEquals(before, mapper.writeValueAsString(plan));
    }

    @Test
    void cancellationBeforeStartAndAtFinalProgressPublishesNoResult() {
        var plan = MonteCarloFundingTest.plan("10000", "100", 2);
        var paths = new AtomicInteger();
        var settings = MonteCarloSettings.forPlan(plan, 1, 417, BigDecimal.ZERO);
        assertThrows(AnalysisCancelledException.class, () -> new MonteCarloAnalyzer().analyze(
                plan, settings, null, index -> {
                    paths.incrementAndGet();
                    return ProjectionEconomicPath.constant(BigDecimal.ZERO);
                }, AnalysisProgressListener.none(), () -> true));
        assertEquals(0, paths.get());
        var stop = new AtomicBoolean();
        assertThrows(AnalysisCancelledException.class, () -> new MonteCarloAnalyzer().analyze(
                plan, settings, null, progress -> {
                    if (progress.completedWork() == 1) {
                        stop.set(true);
                    }
                }, stop::get));
    }

    @Test
    void suppliedUnderfundedReferenceIsReusedWithoutAnotherReferenceExecution() {
        var plan = MonteCarloFundingTest.plan("150", "100", 2);
        var reference = new ProjectionEngine().projectWithOutcome(plan);
        var references = new AtomicInteger();
        var engine = new ProjectionEngine() {
            @Override
            public ProjectionExecutionResult projectWithOutcome(
                    com.daviddunn.retirementplanner.domain.model.RetirementPlan source) {
                references.incrementAndGet();
                return super.projectWithOutcome(source);
            }
        };
        var result = new MonteCarloAnalyzer(engine).analyzeWithReferenceOutcome(plan,
                MonteCarloSettings.forPlan(plan, 2, 417, BigDecimal.ZERO), reference,
                AnalysisProgressListener.none(), AnalysisCancellationToken.none());
        assertEquals(0, references.get());
        assertEquals(2, result.fundingFailureCount());
        assertEquals(1, result.deterministicReference().annualInvestableAssets().size());
    }
}
