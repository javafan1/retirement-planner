package com.daviddunn.retirementplanner.app.montecarlo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.util.ArrayList;

/** Explicit bounded sampling-only workload; no financial engine, no retained scenario collection. */
@EnabledIfSystemProperty(named = "expense.sampling.benchmark", matches = "true")
class MonteCarloExpenseSamplingBenchmarkTest {

    private static volatile BigDecimal sink;

    @Test
    void representativeScenarioLocalWorkloads() throws Exception {
        System.out.printf("EXPENSE_ENV java=%s heap=%d processors=%d%n", System.getProperty("java.version"),
                Runtime.getRuntime().maxMemory(), Runtime.getRuntime().availableProcessors());
        for (int expenses : new int[] {10, 50, 100}) {
            var definitions = new ArrayList<ExpenseSamplingSpecification>();
            for (int index = 0; index < expenses; index++) {
                definitions.add(MonteCarloExpenseGeneratorTest.specification(Integer.toString(1000 + index), ".25"));
            }
            var generator = new MonteCarloExpenseGenerator(definitions);
            var occurrence = generator.occurrencesInInputOrder().getFirst().orElseThrow();
            // Warm protocol/transforms before measuring; discard each path immediately.
            for (int index = 0; index < 100; index++) generator.generate(417, index, 2027, 2086);
            for (int years : new int[] {30, 60}) {
                for (int scenarios : new int[] {1000, 10000}) {
                    System.gc();
                    long before = usedHeap();
                    for (var pool : ManagementFactory.getMemoryPoolMXBeans()) pool.resetPeakUsage();
                    long started = System.nanoTime();
                    for (int scenario = 0; scenario < scenarios; scenario++) {
                        var path = generator.generate(417, scenario, 2027, 2027 + years - 1).orElseThrow();
                        sink = path.multiplier(occurrence, 2027);
                    }
                    double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
                    long poolPeaks = ManagementFactory.getMemoryPoolMXBeans().stream()
                            .filter(pool -> pool.getType() == java.lang.management.MemoryType.HEAP)
                            .mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
                    System.gc();
                    long retainedDelta = usedHeap() - before;
                    System.out.printf(java.util.Locale.ROOT,
                            "EXPENSE_BENCH expenses=%d years=%d scenarios=%d cells=%d seconds=%.3f summedHeapPoolPeakMiB=%.2f postGcDeltaMiB=%.3f%n",
                            expenses, years, scenarios, (long) expenses * years * scenarios, seconds,
                            poolPeaks / 1048576.0, retainedDelta / 1048576.0);
                }
            }
        }
    }

    private static long usedHeap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }
}
