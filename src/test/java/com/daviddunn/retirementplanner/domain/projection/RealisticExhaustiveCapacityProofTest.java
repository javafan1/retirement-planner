package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.income.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.roth.*;
import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.persistence.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in measurement: a complete search is expensive and is never a normal full-suite fixture. */
@EnabledIfSystemProperty(named = "retirementplanner.exhaustiveCapacityProof", matches = "true")
class RealisticExhaustiveCapacityProofTest {
    static final Path CACHE = Path.of("target", "gate3b1-full-calculated.json.gz");
    static final int LAB_MAX_BYTES = 128 * 1024 * 1024;

    static RetirementPlan plan() {
        var plan = SinglePersonCoreProjectionTest.plan(true, FilingStatus.MARRIED_FILING_JOINTLY);
        var primary = plan.getHousehold().getPrimaryPerson(); var spouse = plan.getHousehold().getSpouse();
        primary.setBirthDate(LocalDate.of(1960, 2, 1)); spouse.setBirthDate(LocalDate.of(1962, 6, 1));
        primary.setMortalityCategory(MortalityCategory.MALE); spouse.setMortalityCategory(MortalityCategory.FEMALE);
        for (var person : List.of(primary, spouse)) for (var source : List.copyOf(person.getIncomeSources())) {
            if (source instanceof SocialSecurityIncome ss) person.replaceIncomeSource(ss, new SocialSecurityIncome(ss.getName(), ss.getOwnership(),
                    person.getBirthDate().plusYears(67), null, new BigDecimal(person == primary ? "3000.00" : "2000.00"), 67, BigDecimal.ZERO, 2027));
            if (source instanceof Pension pension) person.replaceIncomeSource(pension, new Pension(pension.getName(), pension.getOwnership(),
                    pension.getStartDate(), pension.getEndDate(), new BigDecimal("2000.00"), new BigDecimal("0.0200"), new BigDecimal("1000.00")));
        }
        String[] balances = {"800000.00", "1500000.00", "250000.00", "1000000.00", "250000.00"};
        int i = 0;
        for (var account : plan.getAccountPortfolio().getAccounts()) { account.setCurrentBalance(new BigDecimal(balances[i++])); account.setOpeningRmdAccountData(null); }
        plan.getAccountPortfolio().addAccount(new CheckingAccount("Synthetic joint cash", AccountOwnership.JOINT, new BigDecimal("150000.00")));
        var a = plan.getPlanningAssumptions();
        plan.setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(new BigDecimal("0.045"), new BigDecimal("0.025"),
                new BigDecimal("0.045"), new BigDecimal("0.02")), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                new DeathScenarioAssumptions(DeathScenario.PRIMARY_DIES, 2045, 67, new BigDecimal("0.75")), 40,
                LocalDate.of(2027, 7, 1), StateTaxAssumptions.michigan()));
        plan.setRothConversionRequest(new RothConversionRequest(true, 2027, new BigDecimal("25000.00"),
                RothConversionStopRule.NEVER, RothConversionStrategy.FIXED_AMOUNT, RothConversionFrequency.ANNUAL));
        return plan;
    }

    static AnalysisSnapshot calculate(RetirementPlan plan, ObjectMapper mapper) throws Exception {
        var fingerprints = new AnalysisFingerprintService(); var kind = AnalysisKind.COUPLE_EXHAUSTIVE;
        var fp = fingerprints.fingerprint(kind, plan, AnalysisFingerprintService.Inputs.none());
        if (Boolean.getBoolean("retirementplanner.reuseCapacitySnapshot") && Files.exists(CACHE)) {
            byte[] raw;
            try (var gzip = new GZIPInputStream(Files.newInputStream(CACHE))) { raw = gzip.readNBytes(LAB_MAX_BYTES + 1); }
            assertTrue(raw.length <= LAB_MAX_BYTES);
            AnalysisSnapshot saved = mapper.readerFor(AnalysisSnapshot.class).withAttribute("snapshot-kind", kind).readValue(raw);
            assertEquals(fp, saved.inputFingerprint()); assertEquals(AnalysisVersions.algorithms(kind), saved.algorithms());
            assertEquals(AnalysisVersions.references(kind), saved.references());
            assertEquals(saved.payloadDigest(), new AnalysisSnapshotCodec().digest(saved.payload(), saved.context()));
            System.out.println("FULL_UNIVERSE_CACHE reused=true financialCalculations=0"); return saved;
        }
        var isolated = new RetirementPlanScenarioCopyService().copy(plan);
        var request = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(isolated);
        assertEquals(5184, request.strategyCount()); assertEquals(20, request.detailRetentionCount());
        long start = System.nanoTime(), deadline = start + TimeUnit.MINUTES.toNanos(10); var last = new AtomicLong(start);
        Instant began = Instant.now();
        var result = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(request, progress -> {
            long now = System.nanoTime();
            if (now - last.get() >= TimeUnit.SECONDS.toNanos(20) || progress.completedWork() == progress.totalWork()) {
                last.set(now); System.out.printf(Locale.ROOT, "FULL_UNIVERSE_PROGRESS phase=%s completed=%d total=%d elapsedSeconds=%.1f%n",
                        progress.phase(), progress.completedWork(), progress.totalWork(), (now-start)/1e9);
            }
        }, () -> System.nanoTime() >= deadline || Thread.currentThread().isInterrupted());
        Instant completed = Instant.now(); Duration runtime = Duration.ofNanos(System.nanoTime() - start);
        var grouped = new LinkedHashMap<List<BigDecimal>, List<IntegratedSocialSecurityCompleteStrategySearchEntry>>();
        for (var entry : result.rankedSuccessfulEntries()) {
            var m = entry.metrics().orElseThrow();
            grouped.computeIfAbsent(List.of(m.afterTaxEstate(), m.endingInvestableAssets(), m.totalTaxes(),
                    m.lifetimePortfolioWithdrawals(), m.lifetimeHouseholdSocialSecurity()), ignored -> new ArrayList<>()).add(entry);
        }
        var report = new ExhaustiveSnapshot.Report("Synthetic capacity household", "Primary fixture", "Spouse fixture", began,
                List.of(new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section("Configured financial assumptions", List.of(
                        "Projection: 2027-07-01, 40 years", "Investment return: 0.045; inflation: 0.025; healthcare: 0.045; SS COLA: 0.02",
                        "State: MI; filing: MFJ, then survivor SINGLE under the existing engine", "Primary death: 2045; survivor election: 67; spending factor: 0.75",
                        "Roth: fixed annual 25000.00, never stop; pension COLA: 0.0200"))), "Original configured current-plan baseline");
        var reference = new ExhaustiveSnapshot.CrossReference(ExhaustiveSnapshot.ReferenceStatus.NO_CURRENT_SOCIAL_SECURITY_RESULT,
                "No SS-only optimization was run for this independent exhaustive search.", null, null, null, null, null, null, null, null, null);
        var first = result.rankedSuccessfulEntries().getFirst();
        var payload = ExhaustiveSnapshot.capture(result, runtime, 20, grouped.values().stream().limit(20)
                .map(entries -> new ExhaustiveSnapshot.Group(entries.getFirst().generationOrder(), entries.size())).toList(), report,
                reference, completed, new ExhaustiveSnapshot.Selection(first.generationOrder(), WeightedSnapshots.Strategy.from(first.strategy()), "FUTURE_DOLLAR_ESTATE", "ranked"));
        var context = new SnapshotContext(report.household(), report.primary(), report.spouse(), began, report.assumptions().stream()
                .map(s -> new SnapshotContext.Section(s.title(), s.lines())).toList(), List.of(), List.of(), Map.of());
        var snapshot = new AnalysisSnapshot(1, 1, UUID.randomUUID(), kind, kind.shape(), UUID.randomUUID(), completed, "measurement-only",
                AnalysisVersions.algorithms(kind), AnalysisVersions.references(kind), fp, fingerprints.effectiveContextFingerprint(kind, plan),
                new AnalysisSnapshotCodec().digest(payload, context), payload, context, null);
        // Existing-format, synthetic-data artifact under ignored target; preserves the completed calculation for further measurements.
        try (var gzip = new GZIPOutputStream(Files.newOutputStream(CACHE))) { mapper.writeValue(gzip, snapshot); }
        System.out.printf(Locale.ROOT, "FULL_UNIVERSE_COMPLETE total=%d successful=%d failed=%d searchSeconds=%.3f retained=%d%n",
                result.totalStrategyCount(), result.successfulStrategyCount(), result.failedStrategyCount(), runtime.toNanos()/1e9,
                result.entries().stream().filter(e -> e.retainedDetail().isPresent()).count());
        return snapshot;
    }

    @Test @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void measureOneFullyCalculatedUniverse(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        var sampler = Executors.newSingleThreadScheduledExecutor(r -> { var t = new Thread(r, "capacity-heap-sampler"); t.setDaemon(true); return t; });
        var peak = new AtomicLong(); long heapAtStart = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        sampler.scheduleAtFixedRate(() -> peak.accumulateAndGet(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(), Math::max), 0, 100, TimeUnit.MILLISECONDS);
        try {
            var mapper = ExhaustiveCapacityInvestigationTest.mapper(); var snapshot = calculate(plan(), mapper);
            var payload = (ExhaustiveSnapshot) snapshot.payload();
            long s = System.nanoTime(); byte[] raw = mapper.writeValueAsBytes(snapshot); long serialized = System.nanoTime();
            var diagnostics = AnalysisSnapshotSizeDiagnostics.measure(raw); long diagnosed = System.nanoTime();
            byte[] gzip = ExhaustiveCapacityInvestigationTest.gzip(raw); long compressed = System.nanoTime();
            AnalysisSnapshot copy = mapper.readerFor(AnalysisSnapshot.class).withAttribute("snapshot-kind", snapshot.kind())
                    .readValue(ExhaustiveCapacityInvestigationTest.gunzip(gzip)); long decoded = System.nanoTime();
            assertEquals(snapshot, copy);
            long productionStart = System.nanoTime();
            if (raw.length <= AnalysisSnapshotCodec.MAX_RAW_BYTES) {
                var codec = new AnalysisSnapshotCodec(); var accepted = codec.encodeMeasured(snapshot); long productionEncoded = System.nanoTime();
                copy = codec.decode(accepted.bytes());
                long productionDecoded = System.nanoTime();
                assertEquals(snapshot, copy);
                assertTrue(accepted.rawBytes() < raw.length);
                var wire = mapper.readTree(ExhaustiveCapacityInvestigationTest.gunzip(accepted.bytes()));
                assertEquals(2, wire.path("payload").path("exhaustiveRepresentation").intValue());
                var frequencies = new HashMap<String, Integer>();
                countReferences(wire.path("payload").path("document"), "", frequencies);
                System.out.printf(Locale.ROOT, "PRODUCTION_CODEC accepted=true raw=%d gzip=%d encodeMs=%.3f decodeMs=%.3f tables=%s references=%d maxReferenceFanout=%d%n",
                        accepted.rawBytes(), accepted.bytes().length, (productionEncoded-productionStart)/1e6,
                        (productionDecoded-productionEncoded)/1e6,
                        List.of(wire.path("payload").path("tables").path("metrics").size(),
                                wire.path("payload").path("tables").path("accounts").size(),
                                wire.path("payload").path("tables").path("years").size()),
                        frequencies.values().stream().mapToInt(Integer::intValue).sum(),
                        frequencies.values().stream().mapToInt(Integer::intValue).max().orElse(0));
                var repository = new JsonAnalysisSnapshotRepository(directory);
                repository.save(snapshot);
                assertEquals(snapshot, new JsonAnalysisSnapshotRepository(directory).load(snapshot.planId(), snapshot.kind()).orElseThrow());
            } else {
                var failure = assertThrows(AnalysisSnapshotSaveException.class, () -> new AnalysisSnapshotCodec().encode(snapshot));
                assertEquals(AnalysisSnapshotSaveException.Stage.SIZE_LIMIT, failure.stage());
                System.out.println("PRODUCTION_CODEC accepted=false category=SIZE_LIMIT");
            }
            long rebuildStart = System.nanoTime();
            var restored = ((ExhaustiveSnapshot) copy.payload()).restore(); long rebuilt = System.nanoTime();
            RetainedProjectionCodecTest.assertGraph(payload.baseline().restoreStrategyResult(), restored.currentPlanBaseline(), new IdentityHashMap<>(), "baseline");
            for (var e : restored.entries()) {
                var original = payload.entries().get(e.generationOrder() - 1);
                assertEquals(original.metrics(), e.metrics().orElse(null)); assertEquals(original.differences(), e.differencesFromCurrentPlan().orElse(null));
                if (original.detail() != null) RetainedProjectionCodecTest.assertGraph(original.detail().restoreStrategyResult(), e.retainedDetail().orElseThrow(), new IdentityHashMap<>(), "detail");
            }
            var experiment = new ExhaustiveCapacityInvestigationTest.Experiment(mapper);
            long internStart = System.nanoTime(); byte[] pooled = experiment.encode(raw); long internEnd = System.nanoTime();
            byte[] pooledGzip = ExhaustiveCapacityInvestigationTest.gzip(pooled); long pooledCompressed = System.nanoTime();
            assertArrayEquals(raw, experiment.expand(ExhaustiveCapacityInvestigationTest.gunzip(pooledGzip))); long expanded = System.nanoTime();
            var years = restored.currentPlanBaseline().projection().getYears();
            assertEquals(5184, restored.totalStrategyCount()); assertEquals(20, payload.entries().stream().filter(e -> e.detail() != null).count());
            assertTrue(years.stream().anyMatch(y -> y.getRequiredMinimumDistribution().signum() > 0));
            assertTrue(years.stream().anyMatch(y -> y.getRothConversion().signum() > 0));
            assertTrue(years.stream().anyMatch(y -> y.getFederalIncomeTax().signum() > 0));
            assertTrue(years.stream().anyMatch(y -> y.getStateIncomeTax().signum() > 0));
            assertTrue(years.stream().anyMatch(y -> y.getInvestmentGrowth().signum() > 0));
            assertTrue(years.stream().anyMatch(y -> y.getAnnualExpenses().signum() > 0));
            long irmaaYears = years.stream().filter(y -> y.getMedicarePremiumCalculation().irmaaBracket().getMonthlyPartDPremium().signum() > 0).count();
            assertTrue(irmaaYears > 0);
            System.out.println("REALISTIC_BREAKDOWN " + diagnostics);
            System.out.printf(Locale.ROOT, "REALISTIC_MEASUREMENT candidates=%d success=%d distinctMetrics=%d distinctDifferences=%d retained=%d baselineIrmaaYears=%d raw=%d gzip=%d pooledRaw=%d pooledGzip=%d poolEntries=%d serializationMs=%.3f diagnosticsMs=%.3f gzipMs=%.3f decodeMs=%.3f reconstructionMs=%.3f interningMs=%.3f pooledGzipMs=%.3f expansionMs=%.3f heapStart=%d heapPeak=%d heapMax=%d%n",
                    payload.entries().size(), payload.rankedOrders().size(), payload.entries().stream().map(ExhaustiveSnapshot.Entry::metrics).filter(Objects::nonNull).distinct().count(),
                    payload.entries().stream().map(ExhaustiveSnapshot.Entry::differences).filter(Objects::nonNull).distinct().count(), diagnostics.retained(), irmaaYears,
                    raw.length, gzip.length, pooled.length, pooledGzip.length, experiment.pool.size(), (serialized-s)/1e6, (diagnosed-serialized)/1e6,
                    (compressed-diagnosed)/1e6, (decoded-compressed)/1e6, (rebuilt-rebuildStart)/1e6, (internEnd-internStart)/1e6,
                    (pooledCompressed-internEnd)/1e6, (expanded-pooledCompressed)/1e6, heapAtStart, peak.get(), Runtime.getRuntime().maxMemory());
            components(mapper, raw);
        } finally { sampler.shutdownNow(); }
    }

    static void countReferences(JsonNode node, String field, Map<String, Integer> counts) {
        if (node.isObject() && node.has("$ref")) {
            String type = field.equals("differences") ? "metrics" : field;
            counts.merge(type + ":" + node.get("$ref").intValue(), 1, Integer::sum);
        } else if (node.isObject()) {
            node.fields().forEachRemaining(e -> countReferences(e.getValue(), e.getKey(), counts));
        } else if (node.isArray()) for (var child : node) countReferences(child, field, counts);
    }

    static void components(ObjectMapper mapper, byte[] raw) throws Exception {
        // Measure independent values only. No component repository/manifest acceptance implementation is introduced.
        var tree = (ObjectNode) mapper.readTree(raw); var payload = (ObjectNode) tree.get("payload");
        var entries = (ArrayNode) payload.get("entries"); var pieces = new ArrayList<byte[]>();
        pieces.add(mapper.writeValueAsBytes(payload.get("baseline")));
        var compact = mapper.createArrayNode(); int details = 0;
        for (var node : entries) {
            var entry = ((ObjectNode) node).deepCopy();
            if (!entry.get("detail").isNull()) { pieces.add(mapper.writeValueAsBytes(entry.get("detail"))); details++; entry.putNull("detail"); }
            compact.add(entry);
        }
        for (int offset = 0; offset < compact.size(); offset += 128) {
            var chunk = mapper.createArrayNode(); for (int i = offset; i < Math.min(offset + 128, compact.size()); i++) chunk.add(compact.get(i));
            pieces.add(mapper.writeValueAsBytes(chunk));
        }
        payload.putNull("baseline"); payload.putNull("entries"); pieces.add(mapper.writeValueAsBytes(tree));
        long totalRaw = 0, totalGzip = 0; int largest = 0; long start = System.nanoTime();
        for (byte[] part : pieces) { totalRaw += part.length; totalGzip += ExhaustiveCapacityInvestigationTest.gzip(part).length; largest = Math.max(largest, part.length); }
        System.out.printf(Locale.ROOT, "COMPONENT_MEASUREMENT componentCount=%d detailComponents=%d dataRawSum=%d dataGzipSum=%d largestRaw=%d compressionMs=%.3f manifestOverhead=NOT_INCLUDED%n",
                pieces.size(), details, totalRaw, totalGzip, largest, (System.nanoTime()-start)/1e6);
    }
}
