package com.daviddunn.retirementplanner.app.socialsecurity;

import com.daviddunn.retirementplanner.app.montecarlo.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.security.MessageDigest;
import jdk.jfr.*;

/** Opt-in real-workload measurements and an always-on, pre-audit financial characterization oracle. */
class StateTaxPerformanceAuditTest {
    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "state.tax.performance", matches = "true")
    void warmedRealApplicationWorkloads() throws Exception {
        var selectedStates = System.getProperty("perf.zero.states");
        if (selectedStates != null) {
            run(Path.of(System.getProperty("perf.output", "target/phase6d4-benchmark")),
                    Integer.getInteger("perf.warm", 3), Integer.getInteger("perf.samples", 5),
                    Boolean.getBoolean("perf.profile"), System.getProperty("perf.filter"), false, false,
                    Arrays.stream(selectedStates.split(",")).map(StateTaxJurisdiction::fromCode).toList());
            return;
        }
        run(Path.of(System.getProperty("perf.output", "target/phase6d1a-benchmark")),
                Integer.getInteger("perf.warm", 3), Integer.getInteger("perf.samples", 5),
                Boolean.getBoolean("perf.profile"), System.getProperty("perf.filter"));
    }

    @org.junit.jupiter.api.Test
    void financialFingerprintsRemainAtTheUnmodifiedStageOneBaseline(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        run(directory, 0, 1, false, null, false);
        var actual = new TreeMap<String, String>();
        for (String line : Files.readAllLines(directory.resolve("measurements.csv")).stream().skip(1).toList()) {
            String[] columns = line.split(",");
            actual.put(columns[0], columns[5]);
        }
        var expected = new TreeMap<String, String>();
        try (var stream = getClass().getResourceAsStream("/tax/state/phase6d1a-financial-fingerprints.txt")) {
            org.junit.jupiter.api.Assertions.assertNotNull(stream);
            for (String line : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] columns = line.split("\\|");
                expected.put(columns[0], columns[1]);
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual,
                "Performance work must not change any captured financial value; do not regenerate this baseline.");
    }
    // Each run keeps only its latest result; no shared mutable calculation state.
    @org.junit.jupiter.api.Test
    void illinoisWorkloadHashesRemainAtAcceptedPhase6D2Baseline(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        run(directory, 0, 1, false, null, true);
        var actual = new TreeMap<String, String>();
        for (var line : Files.readAllLines(directory.resolve("measurements.csv")).stream().skip(1).toList()) {
            var columns = line.split(",");
            actual.put(columns[0], columns[5]);
        }
        var expected = new TreeMap<String, String>();
        try (var stream = getClass().getResourceAsStream("/tax/state/phase6d2-illinois-financial-fingerprints.txt")) {
            org.junit.jupiter.api.Assertions.assertNotNull(stream);
            for (var line : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList()) {
                if (line.startsWith("#") || line.isBlank()) continue;
                var columns = line.split("\\|");
                expected.put(columns[0], columns[1]);
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual,
                "Existing Illinois workload values are frozen; do not regenerate the accepted hashes.");
    }

    static class ResultSink {
        volatile Object value;
    }

    @Name("planner.PerformanceBatch")
    static class Batch extends Event {
        String scenario;
    }
    static RetirementPlan fixture(String cls, String method, Class<?>[] types, Object... args) throws Exception {
        var m = Class.forName(cls).getDeclaredMethod(method, types);
        m.setAccessible(true);
        return (RetirementPlan) m.invoke(null, args);
    }
    static String projectionFingerprint(Object object) throws Exception {
        if (object instanceof Projection p) {
            var s = new StringBuilder();
            var methods = Arrays.stream(ProjectionYear.class.getMethods())
                    .filter(m -> m.getParameterCount() == 0 && m.getReturnType() == BigDecimal.class)
                    .sorted(Comparator.comparing(java.lang.reflect.Method::getName)).toList();
            for (var year : p.getYears()) {
                for (var method : methods) {
                    s.append(method.getName()).append('=');
                    try {
                        s.append(method.invoke(year));
                    } catch (java.lang.reflect.InvocationTargetException exception) {
                        if (!(exception.getCause() instanceof IllegalStateException)) {
                            throw exception;
                        }
                        // The single-person API deliberately has no spouse conversion value.
                        s.append(exception.getCause().getClass().getName());
                    }
                    s.append(';');
                }
            }
            return s.toString();
        }
        if (object instanceof IntegratedRetirementClaimingGridResult g) return g.cells().stream()
                .map(c -> c.primaryRetirementAge()+"/"+c.spouseRetirementAge()+":"+c.integratedResult().orElseThrow().metrics())
                .toList().toString();
        if (object instanceof LongevityWeightedIntegratedStrategyResult w) return w.expectedNominalEstateAtSecondDeath()+"|"+w.expectedPvAfterTaxEstate()+"|"+w.totalEvaluatedProbability();
        if (object instanceof MonteCarloAnalysisResult r) return r.outcomes().toString();
        if (object instanceof MonteCarloMortalityAnalysisResult r) return r.outcomes().toString();
        throw new IllegalArgumentException(object.getClass().getName());
    }
    public static void main(String[] args) throws Exception { run(Path.of(args[0]), Integer.getInteger("perf.warm", 3), Integer.getInteger("perf.samples", 5), Boolean.getBoolean("perf.profile"), System.getProperty("perf.filter")); }
    static void run(Path out, int warm, int samples, boolean profile, String filter) throws Exception {
        run(out, warm, samples, profile, filter, Boolean.getBoolean("perf.illinois"), Boolean.getBoolean("perf.pennsylvania"));
    }
    static void run(Path out, int warm, int samples, boolean profile, String filter, boolean illinois) throws Exception {
        run(out, warm, samples, profile, filter, illinois, false);
    }
    static void run(Path out, int warm, int samples, boolean profile, String filter, boolean illinois,
            boolean boundedPennsylvania) throws Exception {
        run(out, warm, samples, profile, filter, illinois, boundedPennsylvania,
                boundedPennsylvania ? List.of(StateTaxJurisdiction.MI, StateTaxJurisdiction.IL, StateTaxJurisdiction.PA)
                        : illinois ? List.of(StateTaxJurisdiction.IL) : List.of(StateTaxJurisdiction.MI, StateTaxJurisdiction.FL));
    }

    static void run(Path out, int warm, int samples, boolean profile, String filter, boolean illinois,
            boolean boundedPennsylvania, List<StateTaxJurisdiction> selectedStates) throws Exception {
        if (warm < 0 || samples <= 0) {
            throw new IllegalArgumentException("Warm-up count must be nonnegative and sample count positive.");
        }
        Files.createDirectories(out);
        var sink = new ResultSink();


        var workloads = new LinkedHashMap<String, Supplier<Object>>();
        var units = new LinkedHashMap<String, Integer>();
        var engine = new ProjectionEngine();
        for (var state : selectedStates) {
            for (boolean couple : new boolean[]{false,true}) {
                var plan = fixture(boundedPennsylvania
                        ? "com.daviddunn.retirementplanner.domain.projection.PennsylvaniaProductionProjectionTest"
                        : "com.daviddunn.retirementplanner.domain.projection.FloridaProductionProjectionTest", "plan", new Class<?>[]{boolean.class, StateTaxJurisdiction.class}, couple, state);
                String name = state+"-projection-"+(couple?"couple":"single");
                workloads.put(name, () -> engine.project(plan)); units.put(name, 150);
            }
            var mc = boundedPennsylvania ? PennsylvaniaMonteCarloIntegrationTest.plan(state)
                    : fixture("com.daviddunn.retirementplanner.app.montecarlo.MonteCarloMortalityExecutionTest", "plan", new Class<?>[]{});
            mc.setPlanningAssumptions(mc.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
            workloads.put(state+"-fixed-30years", () -> engine.project(mc)); units.put(state+"-fixed-30years", 50);
            var bracket = new RetirementPlanScenarioCopyService().copy(mc);
            bracket.setRothConversionRequest(new RothConversionRequest(true,2027,BigDecimal.ZERO,RothConversionStopRule.FIRST_HOUSEHOLD_RMD,RothConversionStrategy.FILL_22_PERCENT_BRACKET,RothConversionFrequency.ANNUAL));
            workloads.put(state+"-bracket-30years", () -> engine.project(bracket)); units.put(state+"-bracket-30years", 10);
            var grid = boundedPennsylvania ? PennsylvaniaIntegratedAnalysisTest.plan() : Stage4TestPlans.plan();
            grid.setPlanningAssumptions(grid.getPlanningAssumptions().withStateTaxAssumptions(StateTaxAssumptions.simplified(state)));
            var calc = new IntegratedRetirementClaimingGridCalculator();
            workloads.put(state+"-grid81", () -> calc.calculate(grid)); units.put(state+"-grid81", 1);
            var prepared = LongevityWeightedIntegratedStrategyEvaluatorTest.prepared(grid,
                    List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(74,"0.3"),LongevityWeightedIntegratedStrategyEvaluatorTest.prob(80,"0.4"),LongevityWeightedIntegratedStrategyEvaluatorTest.prob(85,"0.3")),
                    List.of(LongevityWeightedIntegratedStrategyEvaluatorTest.prob(73,"0.3"),LongevityWeightedIntegratedStrategyEvaluatorTest.prob(80,"0.4"),LongevityWeightedIntegratedStrategyEvaluatorTest.prob(85,"0.3")));
            var req = new LongevityWeightedIntegratedStrategyRequest(grid,Stage4TestPlans.strategy(grid),prepared,LocalDate.of(2030,1,1),new BigDecimal("0.03"));
            var weighted = new LongevityWeightedIntegratedStrategyEvaluator();
            workloads.put(state+"-weighted9", () -> weighted.evaluate(req)); units.put(state+"-weighted9", 2);
            var analyzer = new MonteCarloAnalyzer();
            var settings = MonteCarloSettings.forPlan(mc,500,417,new BigDecimal("0.12"));
            var method = Class.forName("com.daviddunn.retirementplanner.app.montecarlo.MonteCarloWorldGeneratorTest").getDeclaredMethod("request",RetirementPlan.class,MonteCarloSettings.class);
            method.setAccessible(true);
            var mortality = (MonteCarloMortalityRequest)method.invoke(null,mc,settings);
            workloads.put(state+"-MC-fixed500", () -> analyzer.analyze(mc,settings)); units.put(state+"-MC-fixed500",1);
            workloads.put(state+"-MC-longevity500", () -> analyzer.analyzeMortality(mc,mortality)); units.put(state+"-MC-longevity500",1);
        }
        var lines = new ArrayList<String>();
        lines.add("scenario,sample,units,millis,allocatedBytes,fingerprint");
        System.out.println(System.getProperty("java.runtime.version")+" "+System.getProperty("java.vm.name")+" heap="+Runtime.getRuntime().maxMemory()+" CPUs="+Runtime.getRuntime().availableProcessors()+" args="+ManagementFactory.getRuntimeMXBean().getInputArguments());
        var thread = (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        thread.setThreadAllocatedMemoryEnabled(true);
        for (var item : workloads.entrySet()) {
            String name=item.getKey(); int n=units.get(name);
            if (filter != null && !name.contains(filter)) continue;
            for (int i=0;i<warm;i++) { long t=System.nanoTime(); for(int j=0;j<n;j++) sink.value=item.getValue().get(); System.out.println("WARM "+name+" "+i+" "+(System.nanoTime()-t)/1e6); }
            Recording recording = null;
            if(profile) {recording = new Recording(Configuration.getConfiguration("profile")); recording.enable("jdk.ObjectAllocationSample").withStackTrace(); recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2)); recording.start();}
            for(int i=0;i<samples;i++) {
                Batch batch = new Batch(); batch.scenario=name; batch.begin(); long bytes=thread.getThreadAllocatedBytes(Thread.currentThread().threadId()),start=System.nanoTime();
                for(int j=0;j<n;j++) sink.value=item.getValue().get();
                double ms=(System.nanoTime()-start)/1e6; batch.end(); batch.commit(); bytes=thread.getThreadAllocatedBytes(Thread.currentThread().threadId())-bytes;
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(projectionFingerprint(sink.value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                lines.add(name+","+i+","+n+","+ms+","+bytes+","+hash);
                System.out.println("MEASURE "+lines.getLast());
            }
            if(recording!=null) {recording.stop(); recording.dump(out.resolve(name+".jfr"));recording.close();}
        }
        Files.write(out.resolve("measurements.csv"),lines);
    }
}
