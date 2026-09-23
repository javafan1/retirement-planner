package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.*;
import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit developer benchmark, excluded by the normal non-benchmark suite.
 */
class MonteCarloBenchmarkTest {
    @Test
    void sequentialThirtyYearHousehold() throws Exception {
        var plan = MonteCarloFixtures.household();
        var reference = new ProjectionEngine().project(plan);
        var analyzer = new MonteCarloAnalyzer();
        analyzer.analyze(plan, MonteCarloSettings.forPlan(plan, 20, 417, new BigDecimal("0.12")), reference);
        Path file = Path.of(System.getProperty("montecarlo.benchmark.output", "target/monte-carlo-benchmark-1b-final.txt"));
        Files.writeString(file, "Sequential 30-year, 5-account, two-person household; seed 417; arithmetic mean 4.5%, standard deviation 12%.\n");
        for (int count : new int[]{100, 1000, 5000}) {
            System.gc();
            long before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
                pool.resetPeakUsage();
            }
            long start = System.nanoTime();
            var result = analyzer.analyze(plan, MonteCarloSettings.forPlan(plan, count, 417, new BigDecimal("0.12")), reference);
            double seconds = (System.nanoTime() - start) / 1e9;
            long peak = ManagementFactory.getMemoryPoolMXBeans().stream().filter(p -> p.getType() == java.lang.management.MemoryType.HEAP)
                    .mapToLong(p -> p.getPeakUsage().getUsed()).sum();
            System.gc();
            long retained = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() - before;
            String row = String.format(java.util.Locale.ROOT, "%d simulations: %.3f seconds, %.2f simulations/s, completed=%d, fundingFailures=%d, result retained heap delta=%.2f MiB, summed pool peak=%.2f MiB%n", count, seconds, count / seconds, result.completedCount(), result.fundingFailureCount(), retained / 1048576.0, peak / 1048576.0);
            Files.writeString(file, row, StandardOpenOption.APPEND);
            System.out.print(row);
            assertEquals(count, result.outcomes().size());
            assertEquals(count, result.completedCount() + result.fundingFailureCount());
            if (count == 1000 && seconds > 60) {
                Files.writeString(file, "5,000 skipped: estimated runtime exceeds five minutes.\n", StandardOpenOption.APPEND);
                break;
            }
        }
    }
}
