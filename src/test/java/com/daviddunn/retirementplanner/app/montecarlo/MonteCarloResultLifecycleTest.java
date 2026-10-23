package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService;
import com.daviddunn.retirementplanner.domain.analysis.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.montecarlo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloResultLifecycleTest {
    @TempDir Path dir;
    static final Map<AnalysisKind, MonteCarloSnapshot> FIXTURES = new EnumMap<>(AnalysisKind.class);
    static final Instant START = Instant.parse("2026-10-20T12:00:00Z");

    @BeforeAll static void calculateIndependentFixturesOnce() {
        if (!FIXTURES.isEmpty()) return;
        for (boolean couple : new boolean[]{false, true}) {
            var plan = MonteCarloCapacityProbeTest.fixture(couple);
            var settings = MonteCarloSettings.forPlan(plan, 2, 417, new BigDecimal("0.12"));
            for (boolean longevity : new boolean[]{false, true}) {
                var mortality = !longevity ? null : couple ? new MonteCarloMortalityRequest(plan, settings,
                        com.daviddunn.retirementplanner.domain.socialsecurity.analysis.LongevitySessionSettings.defaults(
                                plan.getPlanningAssumptions().getProjectionStartDate()), 67)
                        : MonteCarloMortalityRequest.individual(plan, settings,
                        com.daviddunn.retirementplanner.domain.socialsecurity.analysis.SocialSecurityMortalityAdjustment.standard());
                var run = mortality == null ? new MonteCarloRunService().run(plan, settings, null,
                        AnalysisProgressListener.none(), AnalysisCancellationToken.none())
                        : new MonteCarloRunService().runMortality(plan, mortality, AnalysisProgressListener.none(), AnalysisCancellationToken.none());
                var payload = MonteCarloSnapshotPresentationMapper.capture(run, plan, START, START.plusSeconds(2));
                FIXTURES.put(payload.kind(), payload);
                var b = new RetirementPlanScenarioCopyService().copy(plan);
                var request = new MonteCarloStrategyComparisonRequest(new MonteCarloStrategyCandidate("A", plan),
                        new MonteCarloStrategyCandidate("B", b), mortality == null
                        ? new MonteCarloStrategyComparisonRequest.Fixed(settings, plan.getPlanningAssumptions().getProjectionStartDate(),
                        plan.getPlanningAssumptions().getProjectionStartDate().getYear() + plan.getPlanningAssumptions().getProjectionLengthYears() - 1,
                        payload.request().fixedLife().restore())
                        : new MonteCarloStrategyComparisonRequest.Longevity(mortality));
                var pair = new MonteCarloStrategyComparisonRunService().run(new MonteCarloStrategyComparisonRunService.Prepared(request,
                        longevity ? MonteCarloMode.LONGEVITY_ADJUSTED : MonteCarloMode.FIXED_LIFESPAN, "Frozen paired fixture"),
                        AnalysisProgressListener.none(), AnalysisCancellationToken.none());
                var paired = MonteCarloSnapshotPresentationMapper.capture(pair, START, START.plusSeconds(2));
                FIXTURES.put(paired.kind(), paired);
            }
        }
    }

    static class Ui implements Consumer<Runnable> {
        final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        public void accept(Runnable r) { tasks.add(r); }
        void until(BooleanSupplier done) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!done.getAsBoolean()) {
                var task = tasks.poll(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (task == null) fail("Timed out waiting for lifecycle callback");
                task.run();
            }
        }
        void drain() { Runnable r; while ((r = tasks.poll()) != null) r.run(); }
    }
    static class Memory implements AnalysisSnapshotRepository {
        final Map<AnalysisResultStore.Slot, AnalysisSnapshot> saved = new ConcurrentHashMap<>();
        volatile boolean fail;
        public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind kind) { return Optional.ofNullable(saved.get(new AnalysisResultStore.Slot(id, kind))); }
        public Optional<AnalysisSnapshot> loadBackup(UUID id, AnalysisKind kind) { return Optional.empty(); }
        public void save(AnalysisSnapshot s) throws java.io.IOException {
            if (fail) throw new java.io.IOException("injected disk failure");
            saved.put(new AnalysisResultStore.Slot(s.planId(), s.kind()), s);
        }
    }
    static MonteCarloFingerprintService.Inputs inputs(MonteCarloSnapshot s) {
        return new MonteCarloFingerprintService.Inputs(s.kind(), s.settings(), s.request());
    }
    static Object restore(MonteCarloSnapshot s) {
        return s.paired() == null ? MonteCarloSnapshotPresentationMapper.restoreRun(s)
                : MonteCarloSnapshotPresentationMapper.restoreComparison(s);
    }
    static void close(AnalysisResultStore s) { assertTrue(s.drain(Duration.ofSeconds(15)).safeToExit()); s.shutdownAfterSuccessfulDrain(); }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_ALL)
    void olderLoadsCannotPublishAfterReplacementPlanSwitchOrClose(AnalysisKind kind) throws Exception {
        var payload = FIXTURES.get(kind); var repo = new Memory(); var store = new AnalysisResultStore(repo);
        var first = new MonteCarloResultLifecycle.Binding(UUID.randomUUID(), dir.resolve("a.json"), kind);
        var second = new MonteCarloResultLifecycle.Binding(UUID.randomUUID(), dir.resolve("b.json"), kind);
        repo.save(MonteCarloResultLifecycle.completion(first, payload).presentationSnapshot(new AnalysisSnapshotCodec()));
        repo.save(MonteCarloResultLifecycle.completion(second, payload).presentationSnapshot(new AnalysisSnapshotCodec()));
        var ui = new Ui(); var attached = new AtomicReference<Object>(); var publications = new AtomicInteger();
        var lifecycle = new MonteCarloResultLifecycle<Object>(store, ui, MonteCarloResultLifecycleTest::restore,
                r -> { attached.set(r); publications.incrementAndGet(); }, () -> attached.set(null), () -> { });
        lifecycle.bind(first); lifecycle.assess(() -> () -> inputs(payload));
        // Keep UI callbacks queued while the binding changes.
        store.background(() -> null).toCompletableFuture().get(15, TimeUnit.SECONDS);
        lifecycle.bind(second);
        ui.until(() -> attached.get() != null && lifecycle.current());
        assertEquals(1, publications.get());
        var previous = attached.get();
        lifecycle.beginReplacement();
        assertTrue(lifecycle.status().contains("previous accepted result"));
        lifecycle.finishReplacement(); // Cancel/failure leaves the original historical graph intact.
        assertSame(previous, attached.get());
        lifecycle.bind(first);
        lifecycle.beginReplacement();
        lifecycle.accept(MonteCarloResultLifecycle.completion(first, payload));
        ui.until(() -> lifecycle.current() && lifecycle.status().contains("SAVED"));
        assertNull(attached.get(), "The rejected older load cannot attach while the new completion is published by its session");
        lifecycle.bind(second); lifecycle.close();
        store.background(() -> null).toCompletableFuture().get(15, TimeUnit.SECONDS);
        ui.drain(); assertNull(attached.get());
        close(store);
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_ALL)
    void incompatibleAndCorruptSourcesNeverRenderFinancialData(AnalysisKind kind) throws Exception {
        var payload = FIXTURES.get(kind); var binding = new MonteCarloResultLifecycle.Binding(UUID.randomUUID(), dir.resolve("p.json"), kind);
        var original = MonteCarloResultLifecycle.completion(binding, payload).presentationSnapshot(new AnalysisSnapshotCodec());
        var incompatible = new AnalysisSnapshot(original.schemaVersion(), original.fingerprintVersion(), original.planId(), kind,
                original.shape(), original.runId(), original.completedAt(), original.applicationBuild(), Map.of("monte-carlo", "v999"),
                original.references(), original.inputFingerprint(), original.contextFingerprint(), original.payloadDigest(), payload, original.context(), null);
        for (boolean corrupt : new boolean[]{false, true}) {
            var repo = new Memory() {
                @Override public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind k) {
                    if (corrupt) throw new IllegalArgumentException("corrupt snapshot");
                    return Optional.of(incompatible);
                }
            };
            var store = new AnalysisResultStore(repo); var ui = new Ui();
            var lifecycle = new MonteCarloResultLifecycle<Object>(store, ui, s -> { fail("Unsafe result must not reconstruct"); return null; },
                    r -> fail("Unsafe result must not attach"), () -> { }, () -> { });
            lifecycle.bind(binding);
            ui.until(() -> lifecycle.status().contains("Incompatible or Unavailable"));
            assertFalse(lifecycle.current()); lifecycle.close(); close(store);
        }
    }

    @Test void allEightSlotsCoexistAndFingerprintsExcludeIdentityAndDisplayNames() throws Exception {
        var repo = new Memory(); var store = new AnalysisResultStore(repo); var id = UUID.randomUUID();
        for (var payload : FIXTURES.values()) {
            store.accept(MonteCarloResultLifecycle.completion(new MonteCarloResultLifecycle.Binding(id, dir.resolve("same.json"), payload.kind()), payload));
            store.whenSettled(new AnalysisResultStore.Slot(id, payload.kind())).toCompletableFuture().get(15, TimeUnit.SECONDS);
        }
        assertEquals(8, repo.saved.size());
        var service = new MonteCarloFingerprintService();
        for (var payload : FIXTURES.values()) {
            var plan = MonteCarloPlanDocument.read(payload.request().planA());
            plan.assignPlanId(UUID.randomUUID()); plan.getHousehold().getPrimaryPerson().setFirstName("Different display name");
            var r = payload.request();
            var renamed = new MonteCarloSnapshot.Request(MonteCarloPlanDocument.capture(plan), r.planB(), r.labelA(), r.labelB(),
                    r.start(), r.endingYear(), r.fixedLife(), r.mortality());
            assertEquals(service.assess(payload), service.assess(new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), renamed)));
            plan.getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("123.45"));
            var changed = new MonteCarloSnapshot.Request(MonteCarloPlanDocument.capture(plan), r.planB(), r.labelA(), r.labelB(), r.start(), r.endingYear(), r.fixedLife(), r.mortality());
            assertNotEquals(service.assess(payload).fingerprint(), service.assess(new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), changed)).fingerprint());
        }
        close(store);
    }

    @Test void fingerprintParserHonorsTheExisting4096DigitContractAndPreservesInputScale() {
        var plan = MonteCarloCapacityProbeTest.fixture(false);
        var number = new BigDecimal("1." + "0".repeat(1065) + "1");
        var account = plan.getAccountPortfolio().getAccounts().getFirst();
        account.setCurrentBalance(number);
        var settings = MonteCarloSettings.forPlan(plan, 2, 417, new BigDecimal("0.12"));
        var service = new MonteCarloFingerprintService();
        var first = service.assess(MonteCarloFingerprintService.standalone(plan, settings, null));
        assertTrue(first.valid());
        account.setCurrentBalance(number.setScale(number.scale() + 1));
        var scaled = service.assess(MonteCarloFingerprintService.standalone(plan, settings, null));
        assertTrue(scaled.valid()); assertNotEquals(first.fingerprint(), scaled.fingerprint());
        account.setCurrentBalance(number);
        assertEquals(first, service.assess(MonteCarloFingerprintService.standalone(plan, settings, null)));
    }

    @Test void sampledHorizonsAreFrozenContextAndFixedMortalityCategoriesAreIrrelevant() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper()
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
        var service = new MonteCarloFingerprintService();
        for (var payload : FIXTURES.values()) {
            var r = payload.request();
            var plan = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(r.planA());
            var assumptions = (com.fasterxml.jackson.databind.node.ObjectNode) plan.get("planningAssumptions");
            assumptions.put("projectionLengthYears", assumptions.get("projectionLengthYears").intValue() + 1);
            var changed = new MonteCarloSnapshot.Request(json.writeValueAsString(plan), r.planB(), r.labelA(), r.labelB(),
                    r.start(), r.endingYear(), r.fixedLife(), r.mortality());
            var original = service.assess(payload);
            var changedAssessment = service.assess(new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), changed));
            if (r.mortality() != null) {
                assertEquals(original.fingerprint(), changedAssessment.fingerprint());
                assertNotEquals(original.contextFingerprint(), changedAssessment.contextFingerprint());
            } else assertNotEquals(original.fingerprint(), changedAssessment.fingerprint());
            if (r.mortality() == null) {
                plan = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(r.planA());
                var primary = (com.fasterxml.jackson.databind.node.ObjectNode) plan.path("household").path("primaryPerson");
                primary.put("mortalityCategory", "FEMALE");
                var category = new MonteCarloSnapshot.Request(json.writeValueAsString(plan), r.planB(), r.labelA(), r.labelB(), r.start(),
                        r.endingYear(), r.fixedLife(), null);
                assertEquals(original, service.assess(new MonteCarloFingerprintService.Inputs(payload.kind(), payload.settings(), category)));
            }
        }
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_ALL)
    void dialogCloseCannotAbandonAnAcceptedSlowWrite(AnalysisKind kind) throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var repo = new Memory() {
            @Override public void save(AnalysisSnapshot snapshot) throws java.io.IOException {
                entered.countDown();
                try { if (!release.await(15, TimeUnit.SECONDS)) throw new java.io.IOException("injected timeout"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted); }
                super.save(snapshot);
            }
        };
        var store = new AnalysisResultStore(repo); var ui = new Ui();
        var binding = new MonteCarloResultLifecycle.Binding(UUID.randomUUID(), dir.resolve("slow.json"), kind);
        var lifecycle = new MonteCarloResultLifecycle<Object>(store, ui, MonteCarloResultLifecycleTest::restore,
                r -> fail("Closed dialog must not publish"), () -> { }, () -> { });
        lifecycle.bind(binding);
        var completion = MonteCarloResultLifecycle.completion(binding, FIXTURES.get(kind));
        lifecycle.accept(completion);
        assertTrue(entered.await(15, TimeUnit.SECONDS));
        assertTrue(lifecycle.status().contains("PENDING"));
        lifecycle.close();
        assertFalse(store.drain(Duration.ofMillis(1)).complete(), "Shutdown cannot claim a pending write is durable");
        release.countDown();
        assertEquals(AnalysisResultStore.Durability.SAVED, store.whenSettled(new AnalysisResultStore.Slot(binding.planId(), kind))
                .toCompletableFuture().get(15, TimeUnit.SECONDS).durability());
        ui.drain();
        assertEquals(completion.runId(), repo.load(binding.planId(), kind).orElseThrow().runId());
        close(store);
    }

    @Test void successfulBindingNotificationsDoNotChangeFinancialRevisionsOrFireForFailedSaveAs() throws Exception {
        var store = new AnalysisResultStore(new Memory());
        var controller = new com.daviddunn.retirementplanner.ui.controller.ApplicationController(new JsonRetirementPlanRepository(),
                new JsonApplicationSettingsRepository(dir.resolve("settings.json")), store);
        var bindings = new ArrayList<String>();
        var detach = controller.addAnalysisBindingListener(() -> bindings.add(controller.getAnalysisSessionId() + "|" + controller.getCurrentFile()));
        long revision = controller.getSourcePlanRevision();
        controller.saveAs(dir.resolve("original.json"));
        controller.save();
        assertEquals(2, bindings.size());
        assertEquals(revision, controller.getSourcePlanRevision());
        assertThrows(java.io.IOException.class, () -> controller.saveAs(dir.resolve("missing/fail.json")));
        assertEquals(2, bindings.size());
        controller.saveAs(dir.resolve("copy.json"));
        assertEquals(3, bindings.size());
        assertNotEquals(bindings.getFirst(), bindings.getLast());
        assertEquals(revision, controller.getSourcePlanRevision());
        detach.run(); controller.save(); assertEquals(3, bindings.size());
        close(store);
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_ALL)
    void newPlanRestoreWaitsForTheOldSessionToFinishCancelling(AnalysisKind kind) throws Exception {
        var payload = FIXTURES.get(kind); var repo = new Memory(); var store = new AnalysisResultStore(repo);
        var binding = new MonteCarloResultLifecycle.Binding(UUID.randomUUID(), dir.resolve("next.json"), kind);
        repo.save(MonteCarloResultLifecycle.completion(binding, payload).presentationSnapshot(new AnalysisSnapshotCodec()));
        var ui = new Ui(); var busy = new AtomicBoolean(true); var attached = new AtomicReference<Object>();
        var lifecycle = new MonteCarloResultLifecycle<Object>(store, ui, MonteCarloResultLifecycleTest::restore,
                attached::set, () -> attached.set(null), () -> { }, busy::get);
        lifecycle.bind(binding); lifecycle.assess(() -> () -> inputs(payload));
        ui.until(lifecycle::current);
        assertNull(attached.get(), "Do not publish into a cancelling financial session");
        busy.set(false); lifecycle.finishReplacement();
        assertNotNull(attached.get(), "The selected plan's historical result must not be dropped when the old run finishes");
        lifecycle.close(); close(store);
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_ALL)
    void restartExactRestoreCurrencyAndIndependentSaveAs(AnalysisKind kind) throws Exception {
        var payload = FIXTURES.get(kind); var id = UUID.randomUUID();
        var file = dir.resolve("plan.json");
        var plan = MonteCarloPlanDocument.read(payload.request().planA()); plan.assignPlanId(id);
        new JsonRetirementPlanRepository().save(plan, file);
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results"));
        var store = new AnalysisResultStore(repo);
        var binding = new MonteCarloResultLifecycle.Binding(id, file, kind);
        var accepted = MonteCarloResultLifecycle.completion(binding, payload);
        store.accept(accepted);
        close(store);
        var restart = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var ui = new Ui(); var attached = new AtomicReference<Object>(); var calls = new AtomicInteger();
        var lifecycle = new MonteCarloResultLifecycle<Object>(restart, ui, s -> {
            calls.incrementAndGet(); return restore(s); // No calculation factory is supplied to the restore graph.
        }, attached::set, () -> attached.set(null), () -> { });
        lifecycle.bind(binding);
        lifecycle.assess(() -> () -> inputs(payload));
        ui.until(() -> lifecycle.current() && attached.get() != null);
        assertEquals(1, calls.get());
        var restored = attached.get();
        if (restored instanceof MonteCarloRun r) {
            var original = MonteCarloSnapshotPresentationMapper.restoreRun(payload);
            assertEquals(original.fan(), r.fan());
            if (payload.fixed() != null) assertEquals(original.result(), r.result());
            else assertEquals(original.mortalityResult().outcomes(), r.mortalityResult().outcomes());
        } else {
            var original = MonteCarloSnapshotPresentationMapper.restoreComparison(payload);
            assertEquals(original.result().outcomes(), ((MonteCarloStrategyComparisonRun) restored).result().outcomes());
            assertEquals(original.result().summary(), ((MonteCarloStrategyComparisonRun) restored).result().summary());
        }
        var changedSettings = new MonteCarloSnapshot.Settings(payload.settings().trials(), payload.settings().seed() + 1,
                payload.settings().expectedReturn(), payload.settings().volatility(), payload.settings().inflation());
        lifecycle.assess(() -> () -> new MonteCarloFingerprintService.Inputs(kind, changedSettings, payload.request()));
        ui.until(() -> lifecycle.status().contains("Outdated")); assertSame(restored, attached.get());
        lifecycle.assess(() -> { throw new IllegalArgumentException("Invalid pending seed"); });
        assertTrue(lifecycle.status().contains("Inputs Need Attention")); assertSame(restored, attached.get());
        lifecycle.assess(() -> () -> inputs(payload)); ui.until(lifecycle::current);
        var target = UUID.randomUUID(); var targetFile = dir.resolve("clone.json");
        var clone = new RetirementPlanScenarioCopyService().copy(plan); clone.assignPlanId(target);
        new JsonRetirementPlanRepository().save(clone, targetFile);
        assertTrue(restart.cloneSnapshots(id, target, targetFile, clone).get(15, TimeUnit.SECONDS).isEmpty());
        var copied = repo.load(target, kind).orElseThrow();
        assertEquals(id, copied.originPlanId()); assertEquals(accepted.completedAt(), copied.completedAt());
        assertEquals(accepted.runId(), repo.load(id, kind).orElseThrow().runId());
        assertEquals(new AnalysisSnapshotCodec().digest(payload, accepted.context()), copied.payloadDigest());
        lifecycle.bind(new MonteCarloResultLifecycle.Binding(target, targetFile, kind));
        ui.until(() -> lifecycle.current() && attached.get() != null);
        assertTrue(lifecycle.status().contains("Historical copy"));
        lifecycle.close(); close(restart);
    }

    @ParameterizedTest @EnumSource(value = AnalysisKind.class, names = ".*MONTE_CARLO.*", mode = EnumSource.Mode.MATCH_ALL)
    void failedSaveRetryCloseAndMemoryOnlyRetainAcceptedResult(AnalysisKind kind) throws Exception {
        var payload = FIXTURES.get(kind); var repo = new Memory(); var store = new AnalysisResultStore(repo);
        var ui = new Ui(); var id = UUID.randomUUID(); var binding = new MonteCarloResultLifecycle.Binding(id, dir.resolve("p.json"), kind);
        var lifecycle = new MonteCarloResultLifecycle<Object>(store, ui, MonteCarloResultLifecycleTest::restore,
                r -> { }, () -> { }, () -> { });
        lifecycle.bind(binding); lifecycle.assess(() -> () -> inputs(payload));
        lifecycle.accept(MonteCarloResultLifecycle.completion(binding, payload));
        ui.until(() -> lifecycle.status().contains("SAVED") && lifecycle.current());
        var previous = repo.load(id, kind).orElseThrow();
        repo.fail = true; lifecycle.beginReplacement();
        var replacement = MonteCarloResultLifecycle.completion(binding, payload);
        lifecycle.accept(replacement);
        ui.until(lifecycle::retryAvailable);
        assertEquals(previous.runId(), repo.load(id, kind).orElseThrow().runId());
        assertEquals(replacement.runId(), store.inMemory(new AnalysisResultStore.Slot(id, kind)).orElseThrow().runId());
        repo.fail = false; lifecycle.retry(); lifecycle.close();
        // Closing the dialog does not cancel application-owned accepted I/O.
        assertEquals(AnalysisResultStore.Durability.SAVED, store.whenSettled(new AnalysisResultStore.Slot(id, kind)).toCompletableFuture().get(15, TimeUnit.SECONDS).durability());
        assertEquals(replacement.runId(), repo.load(id, kind).orElseThrow().runId());
        var memory = new MonteCarloResultLifecycle.Binding(UUID.randomUUID(), null, kind);
        store.accept(MonteCarloResultLifecycle.completion(memory, payload));
        assertEquals(AnalysisResultStore.Durability.MEMORY_ONLY, store.status(new AnalysisResultStore.Slot(memory.planId(), kind)).orElseThrow().durability());
        close(store);
    }
}
