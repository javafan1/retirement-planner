package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.app.montecarlo.MonteCarloStrategyComparisonTest.*;

/** Opt-in 5,000-world sequential measurement, matching the Phase 3 household, volatility and seed. */
class MonteCarloPairedBenchmarkTest {
    @Test
    void fiveThousandPairedWorldsInEachMode() throws Exception {
        var file = Path.of("target/phase4a-benchmark.txt");
        Files.writeString(file, "Sequential seed 417, volatility 0.12, stochastic inflation 0.0175, 5000 worlds.\n");
        var plan = MonteCarloMortalityExecutionTest.plan();
        for (boolean mortality : new boolean[]{false, true}) {
            for (int count : new int[]{30, 5000}) {
                var settings = MonteCarloSettings.forPlan(plan, count, 417, new BigDecimal("0.12"))
                        .withInflation(MonteCarloInflationTest.inflation("0.0175"));
                var mortalityRequest = MonteCarloWorldGeneratorTest.request(plan, settings);
                MonteCarloStrategyComparisonRequest.Assumptions assumptions = mortality
                        ? new MonteCarloStrategyComparisonRequest.Longevity(mortalityRequest)
                        : new MonteCarloStrategyComparisonRequest.Fixed(settings,
                        plan.getPlanningAssumptions().getProjectionStartDate(), 2056, HouseholdLifetimeScenario.bothSurvive());
                var request = request(plan, plan, assumptions);
                var single = measure(() -> mortality ? new MonteCarloAnalyzer().analyzeMortality(plan, mortalityRequest)
                        : new MonteCarloAnalyzer().analyze(plan, settings));
                var paired = measure(() -> run(request));
                if (count == 5000) {
                    var row = String.format(Locale.ROOT,
                            "%s: single %.3f s, paired %.3f s, ratio %.3f, single retained %.2f MiB, paired retained %.2f MiB%n",
                            mortality ? "longevity" : "fixed", single.seconds(), paired.seconds(),
                            paired.seconds() / single.seconds(), single.retainedBytes() / 1048576.0,
                            paired.retainedBytes() / 1048576.0);
                    Files.writeString(file, row, StandardOpenOption.APPEND);
                    System.out.print(row);
                }
            }
        }
    }

    private static Measurement measure(Supplier<Object> action) {
        System.gc();
        long before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        long start = System.nanoTime();
        Object result = action.get();
        double seconds = (System.nanoTime() - start) / 1e9;
        System.gc();
        long retained = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() - before;
        assertNotNull(result);
        Reference.reachabilityFence(result);
        return new Measurement(seconds, retained);
    }

    private record Measurement(double seconds, long retainedBytes) { }
}
