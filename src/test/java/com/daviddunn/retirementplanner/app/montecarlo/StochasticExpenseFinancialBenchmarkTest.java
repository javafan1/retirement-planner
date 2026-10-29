package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.projection.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.lang.ref.WeakReference;

/** Opt-in full financial measurements, not wall-clock assertions or retained-path workloads. */
@EnabledIfSystemProperty(named = "expense.financial.benchmark", matches = "true")
class StochasticExpenseFinancialBenchmarkTest {
    private static volatile Object sink;

    @Test void standaloneFinancialWorkloads() {
        for (int expenses : new int[] {10, 50}) {
            for (boolean longevity : new boolean[] {false, true}) {
                var plan = StochasticExpenseMonteCarloTest.plan(true);
                for (var expense : List.copyOf(plan.getHousehold().getExpenses())) plan.getHousehold().removeExpense(expense);
                for (int i = 0; i < expenses; i++) {
                    plan.getHousehold().addExpense(new Expense("Synthetic " + i,
                            new BigDecimal("10000").divide(BigDecimal.valueOf(expenses)), GrowthCategory.GENERAL));
                }
                var settings = MonteCarloSettings.forPlan(plan, 1000, 417, new BigDecimal(".12"));
                var request = StochasticExpenseMonteCarloTest.mortality(plan, settings);
                for (boolean enabled : new boolean[] {false, true}) {
                    var input = StochasticExpenseMonteCarloTest.configuration(plan, enabled ? ".25" : "0");
                    var paths = new ArrayList<WeakReference<ProjectionExpensePath>>();
                    var engine = new ProjectionEngine() {
                        @Override public ProjectionExecutionResult projectWithOutcome(
                                com.daviddunn.retirementplanner.domain.model.RetirementPlan p,
                                ProjectionEvaluationContext context, ProjectionEconomicPath economic) {
                            context.expensePath().ifPresent(path -> paths.add(new WeakReference<>(path)));
                            return super.projectWithOutcome(p, context, economic);
                        }
                    };
                    var analyzer = new MonteCarloAnalyzer(engine);
                    System.gc(); long before = used();
                    for (var pool : ManagementFactory.getMemoryPoolMXBeans()) pool.resetPeakUsage();
                    long start = System.nanoTime();
                    sink = longevity ? analyzer.analyzeMortalityInternalExpenses(plan, request, input,
                            StochasticExpenseMonteCarloTest.PROGRESS, StochasticExpenseMonteCarloTest.NEVER)
                            : analyzer.analyzeInternalExpenses(plan, settings, input,
                            StochasticExpenseMonteCarloTest.PROGRESS, StochasticExpenseMonteCarloTest.NEVER);
                    double seconds = (System.nanoTime() - start) / 1e9;
                    long peak = ManagementFactory.getMemoryPoolMXBeans().stream()
                            .filter(p -> p.getType() == java.lang.management.MemoryType.HEAP)
                            .mapToLong(p -> p.getPeakUsage().getUsed()).sum();
                    System.gc(); long retained = used() - before;
                    long livePaths = paths.stream().filter(path -> path.get() != null).count();
                    System.out.printf(java.util.Locale.ROOT,
                            "EXPENSE_FINANCIAL expenses=%d longevity=%s enabled=%s seconds=%.3f heapPoolPeakMiB=%.2f retainedResultDeltaMiB=%.2f generatedPaths=%d observedLivePathsAfterGc=%d%n",
                            expenses, longevity, enabled, seconds, peak / 1048576.0, retained / 1048576.0, paths.size(), livePaths);
                    sink = null;
                }
                var input = StochasticExpenseMonteCarloTest.configuration(plan, ".25");
                var worlds = longevity ? new MonteCarloWorldGenerator(request) : null;
                long samplingNanos = 0; long years = 0;
                for (int index = 0; index < 1000; index++) {
                    int last = longevity ? worlds.generate(index).lifetime().terminalDeathYear() - 1 : 2056;
                    if (last < 2027) continue;
                    long start = System.nanoTime();
                    sink = input.apply(plan, ProjectionEvaluationContext.empty(), settings.seed(), index, 2027, last);
                    samplingNanos += System.nanoTime() - start; years += last - 2027 + 1;
                }
                sink = null;
                System.out.printf(java.util.Locale.ROOT, "EXPENSE_PREPARATION expenses=%d longevity=%s seconds=%.3f annualRows=%d%n",
                        expenses, longevity, samplingNanos / 1e9, years);
            }
        }
    }

    private static long used() { return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }
}
