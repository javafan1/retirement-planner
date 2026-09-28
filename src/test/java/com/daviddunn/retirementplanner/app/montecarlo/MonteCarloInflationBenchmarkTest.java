package com.daviddunn.retirementplanner.app.montecarlo;

import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in sequential comparison using identical fixtures and seed on both revisions. */
class MonteCarloInflationBenchmarkTest {
    @Test
    void fiveThousandInBothModes() throws Exception {
        var plan = MonteCarloMortalityExecutionTest.plan();
        var analyzer = new MonteCarloAnalyzer();
        var file = Path.of(System.getProperty("inflation.benchmark.output", "target/inflation-before.txt"));
        Files.writeString(file, "Sequential seed 417, 5000 scenarios, same two-person fixture in both modes.\n");
        for (boolean mortality : new boolean[]{false, true}) {
            var warmup = inflation(MonteCarloSettings.forPlan(plan, 30, 417, new BigDecimal("0.12")));
            if (mortality) analyzer.analyzeMortality(plan, MonteCarloWorldGeneratorTest.request(plan, warmup));
            else analyzer.analyze(plan, warmup);
            System.gc();
            long before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            long start = System.nanoTime();
            var settings = inflation(MonteCarloSettings.forPlan(plan, 5000, 417, new BigDecimal("0.12")));
            Object result = mortality
                    ? analyzer.analyzeMortality(plan, MonteCarloWorldGeneratorTest.request(plan, settings))
                    : analyzer.analyze(plan, settings);
            double seconds = (System.nanoTime() - start) / 1e9;
            System.gc();
            long retained = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() - before;
            String row = String.format(java.util.Locale.ROOT, "%s: %.3f seconds, retained heap delta %.2f MiB%n",
                    mortality ? "longevity" : "fixed", seconds, retained / 1048576.0);
            Files.writeString(file, row, StandardOpenOption.APPEND);
            System.out.print(row);
            assertNotNull(result);
            java.lang.ref.Reference.reachabilityFence(result);
        }
    }

    private static MonteCarloSettings inflation(MonteCarloSettings settings) {
        return Boolean.getBoolean("inflation.benchmark.stochastic")
                ? settings.withInflation(MonteCarloInflationTest.inflation("0.0175")) : settings;
    }
}
