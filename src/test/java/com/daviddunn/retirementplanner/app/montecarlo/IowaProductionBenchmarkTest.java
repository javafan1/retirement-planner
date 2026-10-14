package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Matched immutable-rule workload; no performance assertion or production trial-count change. */
@EnabledIfSystemProperty(named = "iowa.performance", matches = "true")
class IowaProductionBenchmarkTest {
    private static volatile Object sink;

    @Test
    void matchedProjectionConversionAndFiveHundredTrialWorkloads() throws Exception {
        var lines = new ArrayList<String>();
        lines.add("workload,sample,units,milliseconds");
        System.out.println("IOWA PERF " + System.getProperty("java.runtime.version") + " heap="
                + Runtime.getRuntime().maxMemory() + " args=" + ManagementFactory.getRuntimeMXBean().getInputArguments());
        var engine = new ProjectionEngine();
        var analyzer = new MonteCarloAnalyzer();
        for (var state : List.of(StateTaxJurisdiction.FL, StateTaxJurisdiction.IA)) {
            var ordinary = IowaMonteCarloIntegrationTest.plan(state);
            ordinary.setRothConversionRequest(new RothConversionRequest(false, 2027, BigDecimal.ZERO,
                    RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
            measure(state.name() + "-ordinary30", 50, () -> engine.project(ordinary), lines);
            var conversion = IowaMonteCarloIntegrationTest.plan(state);
            measure(state.name() + "-conversion30", 50, () -> engine.project(conversion), lines);
            var settings = MonteCarloSettings.forPlan(conversion, 500, 417, new BigDecimal("0.12"));
            measure(state.name() + "-fixed500", 1, () -> analyzer.analyze(conversion, settings), lines);
            assertEquals(500, ((MonteCarloAnalysisResult) sink).completedCount());
            var mortality = IowaMonteCarloIntegrationTest.mortalityRequest(conversion, settings);
            measure(state.name() + "-longevity500", 1, () -> analyzer.analyzeMortality(conversion, mortality), lines);
            assertEquals(500, ((MonteCarloMortalityAnalysisResult) sink).completedCount());
        }
        Files.write(Path.of("target/phase6d5b-performance.csv"), lines);
    }

    private static void measure(String name, int units, Supplier<Object> operation, List<String> lines) {
        for (int warm = 0; warm < 3; warm++) for (int unit = 0; unit < units; unit++) sink = operation.get();
        for (int sample = 0; sample < 5; sample++) {
            long start = System.nanoTime();
            for (int unit = 0; unit < units; unit++) sink = operation.get();
            var elapsed = BigDecimal.valueOf(System.nanoTime() - start).movePointLeft(6);
            var line = name + "," + sample + "," + units + "," + elapsed.toPlainString();
            lines.add(line);
            System.out.println("IOWA MEASURE " + line);
        }
    }
}
