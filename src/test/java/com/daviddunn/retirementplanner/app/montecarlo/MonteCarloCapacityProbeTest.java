package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.SinglePersonMortalityAnalysisTest;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;
import com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloRunService;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

/** Test-only logical representation/measurement, NOT a production codec or wire contract. */
class MonteCarloCapacityProbeTest {
    private static final String HEADER = "shape\tmode\tpaired\ttrials\tconfiguredYears\tannualTrialRecords\tcompleted\tdistinctOutcomes\trawBytes\tgzipBytes\tmaxNumber\tmaxArray\ttokens\tcontainers\tarrays\tdepth\tengineAndCaptureMs\tencodeMs\tdecodeMs\trebuildMs\tobservedHeapGrowth\tsummedHeapPoolPeaks\twithinDocumentLimits\n";
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNumberLength(4096).maxNestingDepth(80).build())
            .build()).registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));

    @Test
    void smallIndependentlyCalculatedResultsRoundTripInAllEightCases() throws Exception {
        measureAll(4, "target/mc-capacity-small.tsv");
    }

    @Test
    @EnabledIfSystemProperty(named = "mc.capacity", matches = "true")
    void representativeProductionTrialCounts() throws Exception {
        int count = Integer.getInteger("mc.capacity.trials", 1000);
        assertTrue(count >= 1000 && count <= 10000);
        measureAll(count, "target/mc-capacity-production.tsv");
    }

    @Test
    @EnabledIfSystemProperty(named = "mc.capacity.maximum", matches = "true")
    void longestFixedPairedCaseAtUiTrialMaximum() throws Exception {
        String output = "target/mc-capacity-ui-maximum.tsv";
        Files.writeString(Path.of(output), HEADER);
        measure(10000, true, false, true, output);
    }

    @Test
    void fixturesActuallyExerciseFinancialFeaturesAndFundingFailures() throws Exception {
        for (boolean couple : new boolean[]{false, true}) {
            var plan = fixture(couple);
            var projection = new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine().project(plan);
            assertEquals(50, projection.getYears().size());
            assertTrue(projection.getYears().stream().anyMatch(y -> y.getFederalIncomeTax().signum() > 0));
            assertTrue(projection.getYears().stream().anyMatch(y -> y.stateTaxCalculation().isPresent()));
            assertTrue(projection.getYears().stream().anyMatch(y -> y.getRequiredMinimumDistribution().signum() > 0));
            assertTrue(projection.getYears().stream().anyMatch(y -> y.getRothConversion().signum() > 0));
            assertTrue(projection.getYears().stream().anyMatch(y -> y.getAnnualMedicarePremium().signum() > 0));
            plan.getAccountPortfolio().getAccounts().forEach(account -> account.setCurrentBalance(BigDecimal.ZERO));
            var failed = new MonteCarloAnalyzer().analyze(plan, MonteCarloSettings.forPlan(plan, 3, 417, new BigDecimal("0.12")));
            assertEquals(3, failed.fundingFailureCount());
            var restored = read(JSON.readTree(JSON.writeValueAsBytes(tree(failed))), MonteCarloAnalysisResult.class);
            assertEquals(failed, restored);
        }
    }

    @Test
    void decimalScaleAbsenceAndSingleLifeArePreserved() throws Exception {
        for (var value : List.of(new BigDecimal("1.2300"), new BigDecimal("0.0000"),
                new BigDecimal("1E+8"), new BigDecimal("0." + "1".repeat(1070)))) {
            var decoded = JSON.readTree(JSON.writeValueAsBytes(tree(value)));
            assertEquals(value, read(decoded, BigDecimal.class));
        }
        var life = new MonteCarloLifetime.Individual(new MonteCarloLifetime.Life(Optional.empty()));
        assertEquals(life, read(JSON.readTree(JSON.writeValueAsBytes(tree(life))), MonteCarloLifetime.class));
    }

    private void measureAll(int count, String output) throws Exception {
        Files.writeString(Path.of("target/mc-capacity-environment.txt"), "JDK=" + System.getProperty("java.runtime.version")
                + "\nVM=" + System.getProperty("java.vm.name") + "\nOS=" + System.getProperty("os.name")
                + " " + System.getProperty("os.arch") + "\nprocessors=" + Runtime.getRuntime().availableProcessors()
                + "\nmaxHeap=" + Runtime.getRuntime().maxMemory() + "\n");
        Files.writeString(Path.of(output), HEADER);
        for (boolean couple : new boolean[]{false, true}) {
            for (boolean mortality : new boolean[]{false, true}) {
                for (boolean paired : new boolean[]{false, true}) measure(count, couple, mortality, paired, output);
            }
        }
    }

    static RetirementPlan fixture(boolean couple) {
        var plan = couple ? MonteCarloWorldGeneratorTest.plan() : SinglePersonMortalityAnalysisTest.plan();
        var p = plan.getPlanningAssumptions();
        var death = p.getDeathScenarioAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(p.getEconomicAssumptions(), p.getTaxAssumptions(),
                p.getWithdrawalAssumptions(), new DeathScenarioAssumptions(death.getDeathScenario(), death.getDeathYear(),
                couple ? 67 : null, death.getPostDeathExpenseFactor()), 50, p.getProjectionStartDate(), p.getStateTaxAssumptions()));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("15000.01"),
                RothConversionStopRule.FIRST_HOUSEHOLD_RMD, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        return plan;
    }

    private void measure(int count, boolean couple, boolean mortality, boolean paired, String output) throws Exception {
        var plan = fixture(couple);
        var p = plan.getPlanningAssumptions();
        var alternative = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
        alternative.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("30000.02"),
                RothConversionStopRule.FIRST_HOUSEHOLD_RMD, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        var settings = MonteCarloSettings.forPlan(plan, count, 417, new BigDecimal("0.12"))
                .withInflation(new MonteCarloInflationSettings(new BigDecimal("0.02"), new BigDecimal("0.0175"), new BigDecimal("-0.02")));
        var request = couple ? new MonteCarloMortalityRequest(plan, settings,
                LongevitySessionSettings.defaults(p.getProjectionStartDate()), 67)
                : MonteCarloMortalityRequest.individual(plan, settings, SocialSecurityMortalityAdjustment.of(BigDecimal.ONE));
        var assumptions = mortality ? new MonteCarloStrategyComparisonRequest.Longevity(request)
                : new MonteCarloStrategyComparisonRequest.Fixed(settings, p.getProjectionStartDate(), 2076,
                couple ? MonteCarloLifetime.couple(com.daviddunn.retirementplanner.domain.projection.HouseholdLifetimeScenario.bothSurvive())
                        : new MonteCarloLifetime.Individual(new MonteCarloLifetime.Life(Optional.empty())));
        var comparison = new MonteCarloStrategyComparisonRequest(new MonteCarloStrategyCandidate("Current", plan),
                new MonteCarloStrategyCandidate("Alternative Roth", alternative), assumptions);
        var root = JSON.createObjectNode();
        root.put("probeVersion", 1);
        root.set("frozenPlan", JSON.valueToTree(plan));
        if (paired) root.set("frozenAlternative", JSON.valueToTree(alternative));
        root.set("settings", tree(settings));
        root.set("mortalityPrimary", tree(request.primary()));
        root.set("mortalityTable", tree(request.mortalityTable().metadata()));
        if (couple) { root.set("longevity", tree(request.longevityAssumptions())); root.set("survivorAge", tree(request.survivorClaimingAge())); }
        String planBefore = JSON.writeValueAsString(plan);
        System.gc();
        long before = used();
        ManagementFactory.getMemoryPoolMXBeans().forEach(pool -> pool.resetPeakUsage());
        long start = System.nanoTime();
        Object actual;
        com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloRun originalRun = null;
        long annual = 0;
        long completed;
        int distinct;
        if (paired) {
            var result = new MonteCarloStrategyComparisonAnalyzer().analyzeComparison(comparison);
            actual = result;
            root.set("outcomes", tree(result.outcomes()));
            root.set("summary", tree(result.summary()));
            root.put("worldSource", result.worldSource().name());
            annual = result.outcomes().stream().mapToLong(o -> o.outcomeA().annualInvestableAssets().size() + o.outcomeB().annualInvestableAssets().size()).sum();
            completed = result.summary().pairedStates().bothCompleted();
            distinct = new HashSet<>(result.outcomes().stream().map(MonteCarloPairedOutcome::outcomeA).toList()).size();
        } else {
            var service = new MonteCarloRunService();
            var run = mortality ? service.runMortality(plan, request, AnalysisProgressListener.none(), AnalysisCancellationToken.none())
                    : service.run(plan, settings, null, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
            originalRun = run;
            root.set("people", tree(run.people())); root.set("fan", tree(run.fan()));
            root.put("planDetails", run.planDetails()); root.put("firstYear", run.firstYear()); root.put("lastYear", run.lastYear());
            root.put("referenceIncomplete", run.referenceIncomplete()); root.put("elapsedNanos", run.elapsedNanos());
            if (mortality) {
                var result = run.mortalityResult(); actual = result;
                root.set("outcomes", tree(result.outcomes()));
                root.set("aggregates", mortalityAggregates(result));
                root.put("returnModel", result.returnModel()); root.put("mortalityModel", result.mortalityModel());
                annual = result.outcomes().stream().mapToLong(o -> o.annualInvestableAssets().size()).sum();
                completed = result.completedCount();
                distinct = new HashSet<>(result.outcomes().stream().map(o -> o.annualInvestableAssets()).toList()).size();
            } else {
                actual = run.result(); root.set("result", tree(actual));
                completed = run.result().completedCount();
                distinct = new HashSet<>(run.result().outcomes().stream().map(o -> List.of(o.metrics(), o.fundingFailure())).toList()).size();
            }
        }
        long engineMs = ms(start);
        assertTrue(distinct > 1, "Independently generated trials must differ");
        assertEquals(planBefore, JSON.writeValueAsString(plan), "Analysis must not change plan");
        start = System.nanoTime();
        byte[] raw = JSON.writeValueAsBytes(root);
        var zipped = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(zipped)) { gzip.write(raw); }
        long encodeMs = ms(start);
        start = System.nanoTime();
        JsonNode decoded;
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(zipped.toByteArray()))) { decoded = JSON.readTree(gzip); }
        long decodeMs = ms(start);
        exact(root, decoded);
        start = System.nanoTime();
        Object reconstructed;
        if (paired) {
            var original = (MonteCarloStrategyComparisonResult) actual;
            var restored = new MonteCarloStrategyComparisonResult(comparison, original.worldSource(),
                    records(decoded.get("outcomes"), MonteCarloPairedOutcome.class));
            assertEquals(original, restored);
            assertEquals(original.summary(), read(decoded.get("summary"), MonteCarloStrategyComparisonSummary.class));
            reconstructed = restored;
        } else if (mortality) {
            var original = (MonteCarloMortalityAnalysisResult) actual;
            var restored = new MonteCarloMortalityAnalysisResult(request, original.returnModel(), original.mortalityModel(),
                    records(decoded.get("outcomes"), MonteCarloMortalityAnalysisResult.WorldOutcome.class));
            assertEquals(original, restored); exact(mortalityAggregates(original), mortalityAggregates(restored));
            reconstructed = restored;
        } else {
            reconstructed = read(decoded.get("result"), MonteCarloAnalysisResult.class);
            assertEquals(actual, reconstructed);
        }
        long rebuildMs = ms(start);
        if (originalRun != null) {
            assertEquals(originalRun.people(), read(decoded.get("people"), com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.class));
            assertEquals(originalRun.fan(), read(decoded.get("fan"), com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloFanModel.class));
            if (count <= 4) {
                var run = new com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloRun(
                        mortality ? new com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloRun.Mortality((MonteCarloMortalityAnalysisResult) reconstructed)
                                : new com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloRun.Fixed((MonteCarloAnalysisResult) reconstructed),
                        (com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloFanModel) read(decoded.get("fan"), com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloFanModel.class),
                        (com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary) read(decoded.get("people"), com.daviddunn.retirementplanner.domain.breakeven.BreakEvenPlanSummary.class),
                        decoded.get("firstYear").intValue(), decoded.get("lastYear").intValue(), decoded.get("referenceIncomplete").booleanValue(),
                        decoded.get("elapsedNanos").longValue(), decoded.get("planDetails").asText());
                var originalReport = (ObjectNode) tree(com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloPdfReportAdapter.from(originalRun));
                var restoredReport = (ObjectNode) tree(com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloPdfReportAdapter.from(run));
                originalReport.remove("exportedAt"); restoredReport.remove("exportedAt");
                exact(originalReport, restoredReport);
            }
        }
        var structure = inspect(raw);
        long peak = ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.getType() == java.lang.management.MemoryType.HEAP)
                .mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
        boolean fits = raw.length <= AnalysisSnapshotCodec.MAX_RAW_BYTES && zipped.size() <= AnalysisSnapshotCodec.MAX_COMPRESSED_BYTES
                && structure.tokens <= AnalysisSnapshotCodec.MAX_TOKENS && structure.arrays <= AnalysisSnapshotCodec.MAX_ARRAYS
                && structure.maxArray <= 100000 && structure.maxNumber <= 4096 && structure.depth <= 80 && structure.maxString <= 256000;
        var row = String.join("\t", couple ? "couple" : "single", mortality ? "longevity" : "fixed", "" + paired,
                "" + count, "50", "" + annual, "" + completed, "" + distinct, "" + raw.length, "" + zipped.size(),
                "" + structure.maxNumber, "" + structure.maxArray, "" + structure.tokens, "" + structure.containers, "" + structure.arrays, "" + structure.depth,
                "" + engineMs, "" + encodeMs, "" + decodeMs, "" + rebuildMs, "" + (used() - before), "" + peak, "" + fits);
        Files.writeString(Path.of(output), row + "\n", StandardOpenOption.APPEND);
        System.out.println("MC_CAPACITY " + row);
        if (count == 10000) measurePrefixAlternative(root);
        java.lang.ref.Reference.reachabilityFence(actual);
    }

    /** Isolated test experiment: implicit consecutive year keys, not value interning. */
    private static void measurePrefixAlternative(ObjectNode original) throws Exception {
        long start = System.nanoTime();
        var packed = original.deepCopy();
        prefix(packed.get("outcomes"), true);
        byte[] raw = JSON.writeValueAsBytes(packed);
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(raw); }
        long encode = ms(start);
        start = System.nanoTime();
        JsonNode restored;
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { restored = JSON.readTree(gzip); }
        prefix(restored.get("outcomes"), false);
        exact(original, restored);
        long decodeAndExpand = ms(start);
        var s = inspect(raw);
        var row = "rawBytes=" + raw.length + " gzipBytes=" + bytes.size() + " encodeMs=" + encode
                + " decodeExpandAndCompareMs=" + decodeAndExpand + " tokens=" + s.tokens + " containers=" + s.containers
                + " arrays=" + s.arrays + " maxArray=" + s.maxArray + " depth=" + s.depth + " maxNumber=" + s.maxNumber;
        Files.writeString(Path.of("target/mc-capacity-prefix-experiment.txt"), row + "\n");
        System.out.println("MC_PREFIX_EXPERIMENT " + row);
    }

    private static void prefix(JsonNode node, boolean pack) {
        if (node.isObject() && node.has("annualInvestableAssets")) {
            var object = (ObjectNode) node;
            var annual = object.get("annualInvestableAssets");
            if (pack && !annual.isEmpty()) {
                var keys = new ArrayList<String>(); annual.fieldNames().forEachRemaining(keys::add);
                keys.sort(Comparator.comparingInt(Integer::parseInt));
                int first = Integer.parseInt(keys.getFirst());
                var values = JSON.createArrayNode();
                for (int i = 0; i < keys.size(); i++) {
                    assertEquals(first + i, Integer.parseInt(keys.get(i)), "Only contiguous prefixes may omit year keys");
                    values.add(annual.get(keys.get(i)));
                }
                object.set("annualInvestableAssets", JSON.createObjectNode().put("firstYear", first).set("values", values));
            } else if (!pack && annual.has("firstYear")) {
                assertEquals(2, annual.size());
                var values = annual.required("values");
                assertTrue(values.isArray());
                var expanded = JSON.createObjectNode();
                int first = annual.required("firstYear").intValue();
                for (int i = 0; i < values.size(); i++) expanded.set(Integer.toString(Math.addExact(first, i)), values.get(i));
                object.set("annualInvestableAssets", expanded);
            }
        }
        for (var child : node) if (child.isContainerNode()) prefix(child, pack);
    }

    private static JsonNode mortalityAggregates(MonteCarloMortalityAnalysisResult r) throws Exception {
        return tree(Map.ofEntries(Map.entry("annual", r.annualResults()), Map.entry("investable", r.endingInvestableAssets()),
                Map.entry("netWorth", r.endingNetWorth()), Map.entry("estate", r.afterTaxEstate()), Map.entry("taxes", r.lifetimeTaxes()),
                Map.entry("earliest", r.earliestSuccessfulTerminalBalanceDate()), Map.entry("latest", r.latestSuccessfulTerminalBalanceDate()),
                Map.entry("years", r.successfulTerminalYearCounts()), Map.entry("failures", r.fundingFailureStatistics()),
                Map.entry("completed", r.completedCount())));
    }

    // Reflection is confined to trusted test record classes. No class names are loaded from JSON.
    private static JsonNode tree(Object value) throws Exception {
        if (value == null) return NullNode.instance;
        if (value instanceof Optional<?> o) return tree(o.orElse(null));
        if (value instanceof BigDecimal d) return DecimalNode.valueOf(d);
        if (value instanceof Map<?, ?> map) {
            var node = JSON.createObjectNode();
            for (var key : map.keySet().stream().map(MonteCarloCapacityProbeTest::key).sorted().toList()) {
                var entry = map.entrySet().stream().filter(e -> key(e.getKey()).equals(key)).findFirst().orElseThrow();
                node.set(key, tree(entry.getValue()));
            }
            return node;
        }
        if (value instanceof Iterable<?> list) { var node = JSON.createArrayNode(); for (var e : list) node.add(tree(e)); return node; }
        if (value.getClass().isRecord()) {
            var node = JSON.createObjectNode();
            for (var field : value.getClass().getRecordComponents()) node.set(field.getName(), tree(field.getAccessor().invoke(value)));
            return node;
        }
        if (value instanceof Enum<?> e) return TextNode.valueOf(e.name());
        if (value instanceof java.time.temporal.TemporalAccessor) return TextNode.valueOf(value.toString());
        if (value instanceof Number || value instanceof Boolean || value instanceof String) return JSON.valueToTree(value);
        throw new IllegalArgumentException("Uninventoried test value type: " + value.getClass().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object read(JsonNode node, Type type) throws Exception {
        if (type instanceof ParameterizedType p) {
            var args = p.getActualTypeArguments();
            if (p.getRawType() == Optional.class) return node.isNull() ? Optional.empty() : Optional.of(read(node, args[0]));
            if (p.getRawType() == List.class) { var list = new ArrayList<>(); for (var n : node) list.add(read(n, args[0])); return list; }
            if (p.getRawType() == Map.class) {
                var map = new LinkedHashMap<>(); var fields = node.fields();
                while (fields.hasNext()) {
                    var e = fields.next();
                    Object key = args[0] == Integer.class ? Integer.valueOf(e.getKey())
                            : ((Class<?>) args[0]).isEnum() ? Enum.valueOf((Class) args[0], e.getKey()) : e.getKey();
                    map.put(key, read(e.getValue(), args[1]));
                }
                return map;
            }
        }
        var c = (Class<?>) type;
        if (node.isNull()) return null;
        if (c == BigDecimal.class) return node.decimalValue();
        if (c == MonteCarloLifetime.class) return read(node, node.has("spouse") ? MonteCarloLifetime.Couple.class : MonteCarloLifetime.Individual.class);
        if (c == LocalDate.class) return LocalDate.parse(node.asText());
        if (c == Year.class) return Year.parse(node.asText());
        if (c.isEnum()) return Enum.valueOf((Class) c, node.asText());
        if (c.isRecord()) {
            var fields = c.getRecordComponents(); var values = new Object[fields.length]; var types = new Class<?>[fields.length];
            for (int i = 0; i < fields.length; i++) { values[i] = read(node.required(fields[i].getName()), fields[i].getGenericType()); types[i] = fields[i].getType(); }
            return c.getDeclaredConstructor(types).newInstance(values);
        }
        if (c == Integer.class || c == int.class) return node.intValue();
        if (c == Long.class || c == long.class) return node.longValue();
        if (c == Boolean.class || c == boolean.class) return node.booleanValue();
        if (c == String.class) return node.asText();
        throw new IllegalArgumentException("Uninventoried test reconstruction: " + c);
    }

    private static <T> List<T> records(JsonNode node, Class<T> type) throws Exception {
        var list = new ArrayList<T>(); for (var entry : node) list.add(type.cast(read(entry, type))); return list;
    }
    private static void exact(JsonNode a, JsonNode b) {
        assertEquals(a.getNodeType(), b.getNodeType());
        if (a.isNumber()) { assertEquals(a.decimalValue(), b.decimalValue()); return; }
        assertEquals(a.size(), b.size());
        if (a.isObject()) { a.fieldNames().forEachRemaining(key -> exact(a.get(key), b.required(key))); }
        else if (a.isArray()) { for (int i = 0; i < a.size(); i++) exact(a.get(i), b.get(i)); }
        else assertEquals(a, b);
    }
    private record Structure(int tokens, int containers, int arrays, int depth, int maxNumber, int maxArray, int maxString) { }
    private static Structure inspect(byte[] raw) throws Exception {
        int tokens = 0, containers = 0, arrays = 0, depth = 0, maxDepth = 0, number = 0, string = 0;
        try (var parser = JSON.createParser(raw)) {
            while (parser.nextToken() != null) {
                tokens++;
                if (parser.currentToken().isStructStart()) { containers++; maxDepth = Math.max(maxDepth, ++depth); }
                if (parser.currentToken() == JsonToken.START_ARRAY) arrays++;
                if (parser.currentToken().isStructEnd()) depth--;
                if (parser.currentToken().isNumeric()) number = Math.max(number, parser.getTextLength());
                if (parser.currentToken() == JsonToken.VALUE_STRING) string = Math.max(string, parser.getTextLength());
            }
        }
        return new Structure(tokens, containers, arrays, maxDepth, number, largestArray(JSON.readTree(raw)), string);
    }
    private static int largestArray(JsonNode node) {
        int largest = node.isArray() ? node.size() : 0;
        for (var child : node) largest = Math.max(largest, largestArray(child)); return largest;
    }
    private static long used() { return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }
    private static String key(Object value) { return value instanceof Enum<?> e ? e.name() : value.toString(); }
    private static long ms(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
