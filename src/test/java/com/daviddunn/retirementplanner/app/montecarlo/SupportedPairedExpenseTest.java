package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicInteger;
import static com.daviddunn.retirementplanner.app.montecarlo.StochasticExpenseMonteCarloTest.*;
import static com.daviddunn.retirementplanner.app.montecarlo.StandaloneExpenseCapabilityTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SupportedPairedExpenseTest {
    static Stream<Arguments> matrix() {
        return Stream.of(true, false).flatMap(c -> Stream.of(true, false).flatMap(m -> Stream.of(
                new String[]{"0", "0"}, new String[]{".25", "0"}, new String[]{"0", ".17"},
                new String[]{".25", ".25"}, new String[]{".25", ".17"})
                .map(r -> Arguments.of(c, m, r[0], r[1]))));
    }

    @ParameterizedTest @MethodSource("matrix")
    void allModesAndVolatilityCombinationsMatchInternalFullEngineOracles(boolean couple, boolean longevity, String aRate, String bRate) throws Exception {
        var a = plan(couple); var b = plan(couple); enable(a, aRate); enable(b, bRate);
        String beforeA = JSON.writeValueAsString(a), beforeB = JSON.writeValueAsString(b);
        var settings = MonteCarloSettings.forPlan(a, 3, 417, new BigDecimal(".12")).withInflation(MonteCarloInflationTest.inflation(".0175"));
        int last = a.getPlanningAssumptions().getProjectionStartDate().getYear() + a.getPlanningAssumptions().getProjectionLengthYears() - 1;
        var request = comparison(a, b, settings, longevity, last);
        var analyzer = new MonteCarloStrategyComparisonAnalyzer();
        var actual = analyzer.analyzeComparison(request);
        var oracle = analyzer.analyzeInternalExpenses(request,
                InternalExpenseRun.capture(a, a.getHousehold().getExpenses().stream().map(Expense::getEffectiveAnnualExpenseVolatility).toList()),
                InternalExpenseRun.capture(b, b.getHousehold().getExpenses().stream().map(Expense::getEffectiveAnnualExpenseVolatility).toList()), PROGRESS, NEVER);
        assertEquals(oracle.outcomes(), actual.outcomes()); assertEquals(oracle.summary(), actual.summary());
        assertEquals(actual, analyzer.analyzeComparison(request));
        boolean enabled = new BigDecimal(aRate).signum() > 0 || new BigDecimal(bRate).signum() > 0;
        assertEquals(enabled, actual.expenseExecution() != null);
        if (new BigDecimal(aRate).compareTo(new BigDecimal(bRate)) == 0) {
            for (var outcome : actual.outcomes()) assertEquals(outcome.outcomeA(), outcome.outcomeB());
            actual.summary().terminalInvestableAssetsDifference().meanDifference().ifPresent(v -> assertEquals(0, v.signum()));
        }
        var run = new MonteCarloStrategyComparisonRun(actual, longevity ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN,
                "Original synthetic financial context", 0);
        var snapshot = MonteCarloSnapshotPresentationMapper.capture(run, START, START.plusSeconds(1));
        assertEquals(enabled ? 3 : 1, snapshot.version());
        var codec = new AnalysisSnapshotCodec(); var envelope = envelope(snapshot);
        assertEquals(enabled ? 4 : 1, envelope.fingerprintVersion());
        var restored = MonteCarloSnapshotPresentationMapper.restoreComparison((MonteCarloSnapshot) codec.decode(codec.encode(envelope)).payload());
        assertEquals(actual.outcomes(), restored.result().outcomes()); assertEquals(actual.summary(), restored.result().summary());
        assertEquals(run.details(), restored.details());
        if (enabled) {
            assertTrue(run.details().contains("not a maximum variation"));
            assertTrue(run.details().contains("intentionally unmatched"));
            assertEquals(MonteCarloPdfReportAdapter.from(run).context(), MonteCarloPdfReportAdapter.from(restored).context());
        }
        assertEquals(beforeA, JSON.writeValueAsString(a)); assertEquals(beforeB, JSON.writeValueAsString(b));
    }

    @ParameterizedTest @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void comparisonSharesOriginalWorldObjectsAndUsesEachCandidatesOwnImmutableExpensePath(boolean couple, boolean longevity) {
        var a = plan(couple); var b = plan(couple); enable(a, ".25"); enable(b, ".17");
        b.getHousehold().addExpense(new Expense("Independent extra", new BigDecimal("500"), GrowthCategory.NONE,
                java.time.LocalDate.of(2029, 1, 1), java.time.LocalDate.of(2034, 12, 31), ExpenseType.RECURRING, new BigDecimal(".3")));
        var settings = MonteCarloSettings.forPlan(a, 3, 417, new BigDecimal(".12")).withInflation(MonteCarloInflationTest.inflation(".0175"));
        var request = comparison(a, b, settings, longevity, 2031);
        var calls = new AtomicInteger();
        var worlds = new ArrayList<ProjectionEconomicPath>();
        var contexts = new ArrayList<ProjectionEvaluationContext>();
        var plans = Collections.newSetFromMap(new IdentityHashMap<com.daviddunn.retirementplanner.domain.model.RetirementPlan, Boolean>());
        var engine = new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(com.daviddunn.retirementplanner.domain.model.RetirementPlan p,
                    ProjectionEvaluationContext context, ProjectionEconomicPath path) {
                worlds.add(path); contexts.add(context); plans.add(p); calls.incrementAndGet();
                return super.projectWithOutcome(p, context, path);
            }
        };
        var result = new MonteCarloStrategyComparisonAnalyzer(engine).analyzeComparison(request);
        assertEquals(6, calls.get()); assertEquals(2, plans.size(), "One isolated plan per candidate, not per scenario");
        for (int i = 0; i < 6; i += 2) {
            assertSame(worlds.get(i), worlds.get(i + 1));
            assertEquals(contexts.get(i).inflationPath(), contexts.get(i + 1).inflationPath());
            assertNotEquals(contexts.get(i).expensePath(), contexts.get(i + 1).expensePath());
        }
        var suppliedCalls = new AtomicInteger();
        assertThrows(UnsupportedOperationException.class, () -> new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(request,
                index -> { suppliedCalls.incrementAndGet(); return request.worldSource().apply(index); }, PROGRESS, NEVER));
        assertEquals(0, suppliedCalls.get());
        assertNotEquals(result.outcomes(), new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(comparison(a, b,
                MonteCarloSettings.forPlan(a, 3, 418, new BigDecimal(".12")), longevity, 2031)).outcomes());
    }

    @Test void reorderingUnrelatedAndDuplicateExpensesPreservesCorrespondingFinancialResults() {
        var a = plan(false); enable(a, ".25");
        var duplicate = a.getHousehold().getExpenses().getFirst(); a.getHousehold().addExpense(duplicate);
        var b = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(a);
        var expenses = List.copyOf(b.getHousehold().getExpenses()); expenses.forEach(b.getHousehold()::removeExpense);
        b.getHousehold().addExpense(new Expense("Unrelated zero budget", BigDecimal.ZERO, GrowthCategory.NONE,
                null, null, ExpenseType.RECURRING, new BigDecimal(".5")));
        for (int i = expenses.size() - 1; i >= 0; i--) b.getHousehold().addExpense(expenses.get(i));
        var settings = MonteCarloSettings.forPlan(a, 4, 417, new BigDecimal(".12"));
        var result = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(comparison(a, b, settings, false, 2031));
        for (var pair : result.outcomes()) assertEquals(pair.outcomeA(), pair.outcomeB());
    }

    @Test void exactMatchesShareMultipliersWhileDefinitionEditsKeepIndependentKeysAndOwnBindings() {
        var a = plan(false); enable(a, ".25");
        var b = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(a);
        b.getHousehold().addExpense(new Expense("Unmatched", new BigDecimal("500"), GrowthCategory.NONE,
                java.time.LocalDate.of(2028, 1, 1), java.time.LocalDate.of(2030, 12, 31), ExpenseType.RECURRING, new BigDecimal(".17")));
        var settings = MonteCarloSettings.forPlan(a, 2, 417, new BigDecimal(".12"));
        var paths = new ArrayList<ProjectionExpensePath>();
        var analyzer = new MonteCarloStrategyComparisonAnalyzer(new ProjectionEngine() {
            @Override public ProjectionExecutionResult projectWithOutcome(com.daviddunn.retirementplanner.domain.model.RetirementPlan p,
                    ProjectionEvaluationContext c, ProjectionEconomicPath path) {
                var expenses = c.expensePath().orElseThrow();
                expenses.validateBinding(p.getHousehold().getExpenses(), 2027, 2031); paths.add(expenses);
                return super.projectWithOutcome(p, c, path);
            }
        });
        var first = analyzer.analyzeComparison(comparison(a, b, settings, false, 2031));
        for (int i = 0; i < paths.size(); i += 2) {
            var left = paths.get(i); var right = paths.get(i + 1);
            assertEquals(left.bindingForExpense(0), right.bindingForExpense(0));
            for (int year = 2027; year <= 2031; year++)
                assertEquals(left.multiplier(left.bindingForExpense(0).orElseThrow(), year),
                        right.multiplier(right.bindingForExpense(0).orElseThrow(), year));
        }
        paths.clear(); enable(b, ".17");
        analyzer.analyzeComparison(comparison(a, b, settings, false, 2031));
        assertNotEquals(paths.get(0).bindingForExpense(0), paths.get(1).bindingForExpense(0));
        assertNotEquals(paths.get(0).annualMultipliers(), paths.get(1).annualMultipliers());
        var swapped = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(comparison(b, a, settings, false, 2031));
        for (int i = 0; i < first.outcomes().size(); i++) {
            var originalA = first.outcomes().get(i).outcomeA();
            assertEquals(originalA, swapped.outcomes().get(i).outcomeB());
        }
    }

    @Test void boundedPairedWorkloadKeepsCapabilitySmallAndPersistsNoSampledPaths() throws Exception {
        var a = plan(true); var b = plan(true); enable(a, ".25"); enable(b, ".17");
        for (int i = 2; i < 10; i++) {
            a.getHousehold().addExpense(new Expense("A " + i, new BigDecimal(100 + i), GrowthCategory.GENERAL,
                    null, null, ExpenseType.RECURRING, new BigDecimal(".25")));
            b.getHousehold().addExpense(new Expense("B " + i, new BigDecimal(100 + i), GrowthCategory.GENERAL,
                    null, null, ExpenseType.RECURRING, new BigDecimal(".17")));
        }
        var settings = MonteCarloSettings.forPlan(a, 100, 417, new BigDecimal(".12"));
        long start = System.nanoTime();
        var result = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(comparison(a, b, settings, false, 2056));
        long elapsed = System.nanoTime() - start;
        var snapshot = MonteCarloSnapshotPresentationMapper.capture(new MonteCarloStrategyComparisonRun(result,
                MonteCarloMode.FIXED_LIFESPAN, "Bounded synthetic workload", 0), START, START.plusSeconds(1));
        var codec = new AnalysisSnapshotCodec(); var saved = envelope(snapshot);
        assertEquals(saved, codec.decode(codec.encode(saved)));
        String raw = new String(codec.raw(saved), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(raw.contains("annualMultipliers")); assertFalse(raw.contains("expensePath"));
        assertEquals(9, result.expenseExecution().current().bindings().size());
        assertEquals(9, result.expenseExecution().baseline().bindings().size());
        System.out.printf(java.util.Locale.ROOT, "PAIRED_EXPENSE_BOUND trials=100 expensesPerSide=10 years=30 seconds=%.3f rawBytes=%d compressedBytes=%d capabilityBytes=%d heapLimitMiB=%d%n",
                elapsed / 1e9, codec.raw(saved).length, codec.encode(saved).length,
                JSON.writeValueAsBytes(result.expenseExecution()).length, Runtime.getRuntime().maxMemory() / 1048576);
    }
}
