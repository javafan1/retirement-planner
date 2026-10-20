package com.daviddunn.retirementplanner.domain.projection;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Investigation only. No proposed representation is enabled in production. */
class ExhaustiveCapacityInvestigationTest {
    static AnalysisSnapshot largeFixture;
    static AnalysisSnapshot fixture(int pensionColaScale) throws Exception {
        if (pensionColaScale == 21 && largeFixture != null) return largeFixture;
        var plan = ExhaustiveSnapshotTest.plan(40);
        plan.getHousehold().getPrimaryPerson().setBirthDate(LocalDate.of(1960, 2, 1));
        plan.getHousehold().getSpouse().setBirthDate(LocalDate.of(1962, 6, 1));
        plan.getAccountPortfolio().getAccounts().forEach(a -> a.setOpeningRmdAccountData(null));
        for (var person : List.of(plan.getHousehold().getPrimaryPerson(), plan.getHousehold().getSpouse()))
            for (var source : List.copyOf(person.getIncomeSources()))
                if (source instanceof com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome ss)
                    person.replaceIncomeSource(ss, new com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome(ss.getName(),
                            ss.getOwnership(), person.getBirthDate().plusYears(67), null, ss.getFullRetirementMonthlyBenefit(), 67, BigDecimal.ZERO, 2026));
        var a = plan.getPlanningAssumptions(); var e = a.getEconomicAssumptions();
        var inflation = new BigDecimal("0.02");
        plan.setPlanningAssumptions(new PlanningAssumptions(new EconomicAssumptions(e.getExpectedAnnualInvestmentReturn(), inflation,
                e.getHealthcareInflationRate(), e.getSocialSecurityColaRate()), a.getTaxAssumptions(), a.getWithdrawalAssumptions(),
                a.getDeathScenarioAssumptions(), 40, a.getProjectionStartDate(), a.getStateTaxAssumptions()));
        for (var person : List.of(plan.getHousehold().getPrimaryPerson(), plan.getHousehold().getSpouse()))
            for (var source : List.copyOf(person.getIncomeSources()))
                if (source instanceof com.daviddunn.retirementplanner.domain.income.Pension pension) {
                    var replacement = new com.daviddunn.retirementplanner.domain.income.Pension(pension.getName(), pension.getOwnership(),
                            pension.getStartDate(), pension.getEndDate(), pension.getMonthlyBenefit(),
                            pension.getAnnualColaRate().setScale(pensionColaScale), pension.getSurvivorMonthlyBenefit());
                    replacement.setRetirementParticipantFacts(pension.getRetirementParticipantFacts());
                    person.replaceIncomeSource(pension, replacement);
                }
        var evaluator = new IntegratedSocialSecurityStrategyEvaluator();
        var baseline = evaluator.evaluateCurrentStrategy(plan);
        var elections = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan).strategies();
        assertEquals(5184, elections.size());
        var details = new ArrayList<IntegratedSocialSecurityStrategyResult>();
        for (int i = 0; i < 20; i++) details.add(evaluator.evaluate(plan, elections.get(i)));
        // All first twenty elections share retirement dates; fixture is BOTH_SURVIVE. Assert, do not assume equality.
        for (var d : details) assertEquals(details.getFirst().metrics(), d.metrics());
        var metrics = details.getFirst().metrics(); var differences = ExhaustiveSnapshotTest.differences(metrics, baseline.metrics());
        var entries = new ArrayList<IntegratedSocialSecurityCompleteStrategySearchEntry>();
        for (int i = 0; i < elections.size(); i++) entries.add(new IntegratedSocialSecurityCompleteStrategySearchEntry(i + 1,
                elections.get(i), OptionalInt.of(1), Optional.of(metrics), Optional.of(differences),
                i < 20 ? Optional.of(details.get(i)) : Optional.empty(), Optional.empty()));
        // Full-size storage fixture, not a claim that 5,184 financial evaluations were performed.
        var result = new IntegratedSocialSecurityCompleteStrategySearchResult(baseline, IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE,
                entries, entries, OptionalInt.empty());
        var snapshot = new ExhaustiveSnapshotTest().envelope(ExhaustiveSnapshotTest.payload(result));
        if (pensionColaScale == 21) largeFixture = snapshot;
        return snapshot;
    }

    static ObjectMapper mapper() throws Exception {
        // Isolated test access to the exact existing mapper; does NOT alter production decoded-size/token gates.
        var field = AnalysisSnapshotCodec.class.getDeclaredField("mapper"); field.setAccessible(true);
        var mapper = ((ObjectMapper) field.get(new AnalysisSnapshotCodec())).copy();
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        return mapper;
    }
    static byte[] gzip(byte[] raw) throws Exception {
        var output = new ByteArrayOutputStream(); try (var gzip = new GZIPOutputStream(output)) { gzip.write(raw); } return output.toByteArray();
    }
    static byte[] gunzip(byte[] bytes) throws Exception {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) { return gzip.readAllBytes(); }
    }

    /** Test-only exact interning experiment. The application codec intentionally cannot read this wrapper. */
    static final class Experiment {
        final ObjectMapper mapper;
        final ArrayNode pool;
        final Map<String, Integer> identities = new LinkedHashMap<>();
        Experiment(ObjectMapper mapper) { this.mapper = mapper; pool = mapper.createArrayNode(); }
        void intern(ObjectNode owner, String field, String type) throws Exception {
            var value = owner.get(field); if (value == null || value.isNull()) return;
            // Exact lexical JSON is the equality key, retaining BigDecimal value AND scale.
            String key = type + ":" + mapper.writeValueAsString(value);
            Integer id = identities.get(key);
            if (id == null) { id = pool.size(); identities.put(key, id); pool.add(mapper.createObjectNode().put("type", type).set("value", value)); }
            owner.set(field, mapper.createObjectNode().put("$snapshotReference", id).put("type", type));
        }
        void projection(ObjectNode projection) throws Exception { intern(projection, "accounts", "accounts"); intern(projection, "years", "years"); }
        byte[] encode(byte[] original) throws Exception {
            var tree = (ObjectNode) mapper.readTree(original); var payload = (ObjectNode) tree.get("payload");
            projection((ObjectNode) payload.get("baseline"));
            for (var entryNode : payload.withArray("entries")) {
                var entry = (ObjectNode) entryNode;
                intern(entry, "metrics", "metrics"); intern(entry, "differences", "metrics");
                if (!entry.get("detail").isNull()) projection((ObjectNode) entry.get("detail"));
            }
            var wrapper = mapper.createObjectNode().put("investigationOnly", true);
            wrapper.set("pool", pool); wrapper.set("document", tree);
            return mapper.writeValueAsBytes(wrapper);
        }
        byte[] expand(byte[] encoded) throws Exception {
            var wrapper = mapper.readTree(encoded); var pool = (ArrayNode) wrapper.get("pool"); var tree = (ObjectNode) wrapper.get("document");
            var payload = (ObjectNode) tree.get("payload");
            expandProjection((ObjectNode) payload.get("baseline"), pool);
            for (var e : payload.withArray("entries")) {
                expandField((ObjectNode) e, "metrics", "metrics", pool); expandField((ObjectNode) e, "differences", "metrics", pool);
                if (!e.get("detail").isNull()) expandProjection((ObjectNode) e.get("detail"), pool);
            }
            return mapper.writeValueAsBytes(tree);
        }
        void expandProjection(ObjectNode owner, ArrayNode pool) { expandField(owner, "accounts", "accounts", pool); expandField(owner, "years", "years", pool); }
        void expandField(ObjectNode owner, String field, String type, ArrayNode pool) {
            var ref = owner.get(field); if (ref == null || ref.isNull()) return;
            if (ref.size() != 2 || !ref.path("$snapshotReference").isInt() || !type.equals(ref.path("type").asText())) throw new IllegalArgumentException("Invalid experiment reference");
            int id = ref.get("$snapshotReference").intValue();
            if (id < 0 || id >= pool.size() || !type.equals(pool.get(id).path("type").asText())) throw new IllegalArgumentException("Invalid experiment binding");
            owner.set(field, pool.get(id).get("value"));
        }
    }

    @Test void measurePrecisionDrivenCapacityAndLosslessAlternatives() throws Exception {
        var mapper = mapper();
        for (int pensionColaScale : List.of(2, 20, 21)) {
            var snapshot = fixture(pensionColaScale);
            long heapBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long start = System.nanoTime(); byte[] raw = mapper.writeValueAsBytes(snapshot); long serialized = System.nanoTime();
            var summary = AnalysisSnapshotSizeDiagnostics.measure(raw);
            byte[] gzip = gzip(raw); long compressed = System.nanoTime();
            var decoded = mapper.readerFor(AnalysisSnapshot.class).withAttribute("snapshot-kind", AnalysisKind.COUPLE_EXHAUSTIVE).readValue(gunzip(gzip));
            long restored = System.nanoTime(); assertEquals(snapshot, decoded);
            var experiment = new Experiment(mapper); byte[] pooled = experiment.encode(raw); long interned = System.nanoTime();
            byte[] pooledGzip = gzip(pooled); byte[] expanded = experiment.expand(gunzip(pooledGzip)); long expandedAt = System.nanoTime();
            assertArrayEquals(raw, expanded, "Lossless experiment must reproduce every original byte, not only numerical equality");
            assertEquals(5184, summary.candidates()); assertEquals(20, summary.retained()); assertEquals(21, summary.projections());
            assertEquals(40, summary.maximumYears()); assertEquals(6, summary.maximumAccounts()); assertTrue(summary.complete());
            if (pensionColaScale == 21) assertTrue(raw.length >= 44_692_375,
                    "The largest bounded fixture must reach or exceed the observed production document size");
            assertEquals(raw.length, summary.bytes().get("compactEntriesIncludingSyntax") + summary.bytes().get("retainedDetails")
                    + summary.bytes().get("baseline") + summary.bytes().get("envelopeAndOtherMetadata"));
            // Legacy logical bytes may exceed the physical budget; production v2 must fit without losing them.
            var production = new AnalysisSnapshotCodec();
            byte[] optimized = production.raw(snapshot);
            assertTrue(optimized.length < raw.length);
            assertTrue(optimized.length <= AnalysisSnapshotCodec.MAX_RAW_BYTES);
            assertEquals(snapshot, production.decode(production.encode(snapshot)));
            long heapAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            System.out.println("CAPACITY_BREAKDOWN pensionColaScale=" + pensionColaScale + " " + summary);
            System.out.printf(Locale.ROOT, "CAPACITY_ALTERNATIVES pensionColaScale=%d originalRaw=%d originalGzip=%d pooledRaw=%d pooledGzip=%d poolEntries=%d serializeMs=%.3f diagnosticsAndGzipMs=%.3f labDecodeMs=%.3f internMs=%.3f gzipAndExpandMs=%.3f observedHeapDelta=%d%n",
                    pensionColaScale, raw.length, gzip.length, pooled.length, pooledGzip.length, experiment.pool.size(), (serialized-start)/1e6,
                    (compressed-serialized)/1e6, (restored-compressed)/1e6, (interned-restored)/1e6, (expandedAt-interned)/1e6, heapAfter-heapBefore);
        }
    }

    @Test void retryReportsComponentsWithoutCalculationOrReplacingPriorSnapshot(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        var failVerification = new java.util.concurrent.atomic.AtomicBoolean();
        var repository = AnalysisSnapshotFaults.failVerification(dir.resolve("results"), failVerification::get);
        var prior = fixture(2); repository.save(prior);
        var file = repository.path(prior.planId(), prior.kind()); byte[] previousBytes = java.nio.file.Files.readAllBytes(file);
        var big = fixture(21);
        var accepted = new AcceptedAnalysisCompletion(big.planId(), dir.resolve("synthetic-plan.json"), big.kind(), big.runId(), big.completedAt(),
                big.inputFingerprint(), big.contextFingerprint(), big.algorithms(), big.references(), big.payload(), big.context());
        var store = new AnalysisResultStore(repository);
        var records = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var logger = java.util.logging.Logger.getLogger(AnalysisSnapshotCodec.class.getName());
        var handler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord r) { if (r.getMessage().startsWith("Exhaustive snapshot size breakdown:")) records.add(r.getMessage()); }
            public void flush() { } public void close() { }
        };
        logger.addHandler(handler);
        String previousDiagnosticFlag = System.getProperty("retirementplanner.snapshot.sizeDiagnostics");
        System.setProperty("retirementplanner.snapshot.sizeDiagnostics", "true");
        failVerification.set(true);
        try {
            var slot = new AnalysisResultStore.Slot(big.planId(), big.kind()); store.accept(accepted);
            assertEquals(AnalysisResultStore.Durability.SAVE_FAILED, store.whenSettled(slot).toCompletableFuture().get(20, java.util.concurrent.TimeUnit.SECONDS).durability());
            store.retry(slot);
            assertEquals(AnalysisResultStore.Durability.SAVE_FAILED, store.whenSettled(slot).toCompletableFuture().get(20, java.util.concurrent.TimeUnit.SECONDS).durability());
            assertSame(accepted, store.inMemory(slot).orElseThrow());
            assertArrayEquals(previousBytes, java.nio.file.Files.readAllBytes(file));
            assertEquals(2, records.size());
            for (String record : records) for (String category : List.of("compactEntriesIncludingSyntax", "retainedDetails", "baseline", "frozenReportAndContext", "socialSecurityCrossReference"))
                assertTrue(record.contains(category), category);
        } finally {
            failVerification.set(false);
            if (previousDiagnosticFlag == null) System.clearProperty("retirementplanner.snapshot.sizeDiagnostics");
            else System.setProperty("retirementplanner.snapshot.sizeDiagnostics", previousDiagnosticFlag);
            logger.removeHandler(handler);
            // Test teardown uses the already computed small fixture so the store can drain normally.
            store.accept(new AcceptedAnalysisCompletion(prior.planId(), dir.resolve("synthetic-plan.json"), prior.kind(), prior.runId(), prior.completedAt(),
                    prior.inputFingerprint(), prior.contextFingerprint(), prior.algorithms(), prior.references(), prior.payload(), prior.context()));
            assertTrue(store.drain(Duration.ofSeconds(10)).safeToExit()); store.shutdownAfterSuccessfulDrain();
        }
    }
}
