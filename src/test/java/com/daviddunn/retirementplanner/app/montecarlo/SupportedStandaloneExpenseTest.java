package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.daviddunn.retirementplanner.app.montecarlo.StochasticExpenseMonteCarloTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SupportedStandaloneExpenseTest {
    @Test
    void boundedEnabledWorkloadRecordsRuntimeAndCompactArtifactSizeWithoutRetainingPaths() throws Exception {
        var plan = plan(true);
        for (int index = 2; index < 10; index++) plan.getHousehold().addExpense(new Expense("Synthetic expense " + index,
                new BigDecimal(100 + index), GrowthCategory.GENERAL, null, null, ExpenseType.RECURRING, new BigDecimal(".25")));
        StandaloneExpenseCapabilityTest.enable(plan, ".25");
        var settings = MonteCarloSettings.forPlan(plan, 100, 417, new BigDecimal(".12"));
        long started = System.nanoTime();
        var run = new MonteCarloRunService().run(plan, settings, null, PROGRESS, NEVER);
        long elapsed = System.nanoTime() - started;
        var payload = MonteCarloSnapshotPresentationMapper.capture(run, plan, StandaloneExpenseCapabilityTest.START,
                StandaloneExpenseCapabilityTest.START.plusSeconds(1));
        var codec = new AnalysisSnapshotCodec();
        var saved = StandaloneExpenseCapabilityTest.envelope(payload);
        assertEquals(100, payload.fixed().outcomes().size());
        assertEquals(9, payload.expenseExecution().bindings().size());
        assertEquals(saved, codec.decode(codec.encode(saved)));
        System.out.printf(java.util.Locale.ROOT, "ENABLED_EXPENSE_BOUND trials=100 expenses=10 seconds=%.3f rawBytes=%d compressedBytes=%d capabilityBytes=%d heapLimitMiB=%d%n",
                elapsed / 1_000_000_000.0, codec.raw(saved).length, codec.encode(saved).length,
                JSON.writeValueAsBytes(payload.expenseExecution()).length, Runtime.getRuntime().maxMemory() / 1048576);
    }
    @ParameterizedTest @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void publicGeneratedResultsMatchTheExistingInternalFinancialEngineAndRestoreExactly(boolean couple, boolean longevity) throws Exception {
        var plan = plan(couple);
        StandaloneExpenseCapabilityTest.enable(plan, "0.25");
        var settings = MonteCarloSettings.forPlan(plan, 4, 417, new BigDecimal("0.12"))
                .withInflation(MonteCarloInflationTest.inflation(".0175"));
        var original = JSON.writeValueAsString(plan);
        var analyzer = new MonteCarloAnalyzer();
        var internal = InternalExpenseRun.capture(plan, plan.getHousehold().getExpenses().stream()
                .map(e -> e.getEffectiveAnnualExpenseVolatility()).toList());
        MonteCarloRun run;
        if (longevity) {
            var request = mortality(plan, settings);
            var result = analyzer.analyzeMortality(plan, request);
            var oracle = analyzer.analyzeMortalityInternalExpenses(plan, request, internal, PROGRESS, NEVER);
            assertNotNull(result.expenseExecution());
            assertEquals(oracle.outcomes(), result.outcomes()); assertEquals(oracle.annual(), result.annualResults());
            assertEquals(result.outcomes(), analyzer.analyzeMortality(plan, request).outcomes());
            assertEquals(new MonteCarloWorldGenerator(request).generate(0).lifetime(), result.outcomes().getFirst().lifetime());
            run = new MonteCarloRunService().runMortality(plan, request, PROGRESS, NEVER);
        } else {
            var result = analyzer.analyze(plan, settings);
            var oracle = analyzer.analyzeInternalExpenses(plan, settings, internal, PROGRESS, NEVER);
            assertNotNull(result.expenseExecution());
            assertEquals(oracle.outcomes(), result.outcomes()); assertEquals(oracle.annual(), result.annualInvestableAssets());
            assertEquals(result, analyzer.analyze(plan, settings));
            var zero = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
            StandaloneExpenseCapabilityTest.enable(zero, "0");
            var legacy = analyzer.analyze(zero, settings);
            assertNull(legacy.expenseExecution());
            assertEquals(legacy.deterministicReference(), result.deterministicReference());
            assertNotEquals(legacy.annualInvestableAssets(), result.annualInvestableAssets());
            run = new MonteCarloRunService().run(plan, settings, null, PROGRESS, NEVER);
        }
        assertEquals(original, JSON.writeValueAsString(plan), "Public simulation must not mutate inputs");
        var snapshot = MonteCarloSnapshotPresentationMapper.capture(run, plan, StandaloneExpenseCapabilityTest.START,
                StandaloneExpenseCapabilityTest.START.plusSeconds(1));
        assertEquals(2, snapshot.version());
        var codec = new AnalysisSnapshotCodec();
        var stored = StandaloneExpenseCapabilityTest.envelope(snapshot);
        var decoded = codec.decode(codec.encode(stored));
        assertEquals(stored, decoded);
        var restored = MonteCarloSnapshotPresentationMapper.restoreRun((MonteCarloSnapshot) decoded.payload());
        assertEquals(run.fan(), restored.fan());
        assertTrue(restored.planDetails().contains("expense 1 = 25%"));
        assertTrue(restored.planDetails().contains(ExpenseExecution.ALGORITHM.split("/")[0]));
        var report = MonteCarloPdfReportAdapter.from(restored);
        assertTrue(String.join("\n", report.context()).contains("not a maximum expense increase"));
        assertEquals(MonteCarloPdfReportAdapter.from(run).chart(), report.chart());
    }

    @Test
    void differentSeedsOrderChangesAndUnrelatedZeroBudgetExpenseDoNotShiftOtherInputs() {
        var plan = plan(false);
        StandaloneExpenseCapabilityTest.enable(plan, ".25");
        var settings = MonteCarloSettings.forPlan(plan, 3, 417, new BigDecimal(".12"));
        var analyzer = new MonteCarloAnalyzer();
        var first = analyzer.analyze(plan, settings);
        var changedSeed = analyzer.analyze(plan, MonteCarloSettings.forPlan(plan, 3, 418, new BigDecimal(".12")));
        assertNotEquals(first.outcomes(), changedSeed.outcomes());
        var expenses = List.copyOf(plan.getHousehold().getExpenses());
        expenses.forEach(plan.getHousehold()::removeExpense);
        plan.getHousehold().addExpense(new Expense("Unrelated zero budget", BigDecimal.ZERO, GrowthCategory.NONE,
                null, null, ExpenseType.RECURRING, new BigDecimal(".5")));
        for (int i = expenses.size() - 1; i >= 0; i--) plan.getHousehold().addExpense(expenses.get(i));
        assertEquals(first.outcomes(), analyzer.analyze(plan, settings).outcomes());
        assertEquals(first.annualInvestableAssets(), analyzer.analyze(plan, settings).annualInvestableAssets());
    }

    @ParameterizedTest @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void enabledSuppliedSourcesRemainRejectedBeforeAnyWorldIsRead(boolean couple, boolean longevity) {
        var plan = plan(couple); StandaloneExpenseCapabilityTest.enable(plan, ".25");
        var settings = MonteCarloSettings.forPlan(plan, 1, 417, new BigDecimal(".12"));
        var calls = new AtomicInteger();
        if (longevity) assertThrows(UnsupportedOperationException.class, () -> new MonteCarloAnalyzer().analyzeMortality(plan,
                mortality(plan, settings), index -> { calls.incrementAndGet(); throw new AssertionError(); }, PROGRESS, NEVER));
        else assertThrows(UnsupportedOperationException.class, () -> new MonteCarloAnalyzer().analyze(plan, settings, null,
                index -> { calls.incrementAndGet(); throw new AssertionError(); }, PROGRESS, NEVER));
        var pair = comparison(plan, plan, settings, longevity,
                plan.getPlanningAssumptions().getProjectionStartDate().getYear() + plan.getPlanningAssumptions().getProjectionLengthYears() - 1);
        assertThrows(UnsupportedOperationException.class, () -> new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(pair,
                index -> { calls.incrementAndGet(); throw new AssertionError(); }, PROGRESS, NEVER));
        assertEquals(0, calls.get());
    }
}
