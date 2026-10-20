package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.financial.*;
import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.WeightedRestorationTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ExhaustivePersistenceTest {
    static final AnalysisKind KIND = AnalysisKind.COUPLE_EXHAUSTIVE;
    static final Instant COMPLETED = Instant.parse("2026-10-20T12:30:00Z");
    @TempDir Path dir;
    static IntegratedSocialSecurityCompleteStrategySearchResult result;
    @BeforeAll static void fixture() throws Exception {
        ClaimingStrategyHeatMapViewTest.startFx();
        var plan = plan(); var standard = IntegratedSocialSecurityCompleteStrategySearchRequest.standard(plan);
        result = new IntegratedSocialSecurityCompleteStrategySearchCalculator().calculate(
                new IntegratedSocialSecurityCompleteStrategySearchRequest(plan, List.of(62, 67, 70), List.of(62, 67),
                        standard.primarySurvivorCandidates().subList(0, 1), standard.spouseSurvivorCandidates().subList(0, 1),
                        IntegratedStrategyRankingMeasure.AFTER_TAX_ESTATE, 3));
    }
    static RetirementPlan plan() {
        var p = LongevityWeightedAnalysisRequestFactoryTest.plan();
        p.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.TRADITIONAL_IRA, "Same name", AccountOwnership.PRIMARY, new BigDecimal("1000000.0000")));
        p.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.ROTH_IRA, "Same name", AccountOwnership.SPOUSE, new BigDecimal("123000.00100")));
        p.getAccountPortfolio().addAccount(AccountFactory.create(AccountType.CHECKING, "Cash", AccountOwnership.JOINT, new BigDecimal("500000.00")));
        return p;
    }
    ApplicationController source(AnalysisResultStore store, String name) throws Exception {
        return new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve(name + ".settings")), store);
    }
    ApplicationController saved(AnalysisResultStore store) throws Exception {
        var s = source(store, "settings"); s.newPlan(plan()); s.saveAs(dir.resolve("plan.json")); return s;
    }
    static AcceptedAnalysisCompletion completion(ApplicationController source) {
        assertFalse(Platform.isFxApplicationThread());
        var model = ExhaustiveIntegratedSearchPresentation.from(result, Duration.ofMillis(123), 20);
        var context = new IntegratedReportContext("Frozen household", "Original Primary", "Original Spouse", COMPLETED.minusSeconds(1),
                List.of(new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport.Section("Frozen assumptions", List.of("Original return 3%"))), "Original baseline");
        var first = result.rankedSuccessfulEntries().getFirst();
        var payload = ExhaustiveResultSnapshots.capture(model, context, new ExhaustiveResultSnapshots.SocialSecurityBasis(
                WeightedSnapshots.Strategy.from(first.strategy()), new BigDecimal("123456.0012300"), 5), COMPLETED,
                "DIFFERENCE_FROM_CURRENT", "heat-map");
        var fp = new AnalysisFingerprintService();
        return new AcceptedAnalysisCompletion(source.getAnalysisSessionId(), source.canPersistAnalysisResults() ? source.getCurrentFile() : null,
                KIND, UUID.randomUUID(), COMPLETED, fp.fingerprint(KIND, source.getCurrentPlan(), AnalysisFingerprintService.Inputs.none()),
                fp.effectiveContextFingerprint(KIND, source.getCurrentPlan()), AnalysisVersions.algorithms(KIND), AnalysisVersions.references(KIND), payload,
                new SnapshotContext("Frozen household", "Original Primary", "Original Spouse", context.runStarted(), List.of(), List.of(), List.of(), Map.of()));
    }
    record Session(SocialSecurityStrategyAnalyzerDialog dialog, SocialSecurityAnalyzerJobController jobs,
            SocialSecurityAnalysisJobCoordinator coordinator, WeightedResultPersistence persistence) implements AutoCloseable {
        public void close() throws Exception {
            fx(() -> { invoke(dialog, "close"); return null; }); coordinator.close();
        }
    }
    static Object invoke(Object owner, String name) throws Exception {
        var m = owner.getClass().getDeclaredMethod(name); m.setAccessible(true); return m.invoke(owner);
    }
    static com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport report(Session session) throws Exception {
        var method = session.dialog.getClass().getDeclaredMethod("capturePdfReport", boolean.class); method.setAccessible(true);
        return (com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport) method.invoke(session.dialog, false);
    }
    static Session session(ApplicationController source) throws Exception {
        var coordinator = new SocialSecurityAnalysisJobCoordinator();
        return fx(() -> {
            var jobs = new SocialSecurityAnalyzerJobController(coordinator);
            var d = new SocialSecurityStrategyAnalyzerDialog(null, source, jobs);
            d.exhaustiveCalculation = request -> (progress, token) -> { throw new AssertionError("Restore must not calculate"); };
            d.socialSecurityCalculation = request -> (progress, token) -> { throw new AssertionError("Restore must not optimize SS"); };
            field(d, "navigation", AnalyzerNavigationView.class).select(SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE);
            return new Session(d, jobs, coordinator, field(d, "exhaustivePersistence", WeightedResultPersistence.class));
        });
    }

    @Test void restartRestoresAllViewsWithoutCalculatingAndRevertsCurrency() throws Exception {
        var repository = new JsonAnalysisSnapshotRepository(dir.resolve("results"));
        var store = new AnalysisResultStore(repository); var source = saved(store);
        var accepted = completion(source); store.accept(accepted); settle(store, accepted.planId(), KIND); stop(store);
        var restarted = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var reopened = source(restarted, "reopened"); reopened.open(source.getCurrentFile());
        byte[] before = Files.readAllBytes(source.getCurrentFile());
        try (var s = session(reopened)) {
            await(s.persistence.notice, "Current Results");
            var originalReport = fx(() -> report(s));
            assertEquals(COMPLETED.minusSeconds(1), originalReport.runStarted());
            fx(() -> {
                assertEquals(0, s.jobs.generation()); assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, s.jobs.state());
                var restored = field(s.dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class).result();
                assertEquals(result.entries().stream().map(e -> e.metrics()).toList(), restored.entries().stream().map(e -> e.metrics()).toList());
                assertEquals(result.rankedSuccessfulEntries().stream().map(e -> e.generationOrder()).toList(), restored.rankedSuccessfulEntries().stream().map(e -> e.generationOrder()).toList());
                assertEquals(3, restored.entries().stream().filter(e -> e.retainedDetail().isPresent()).count());
                var restoredAccount = restored.currentPlanBaseline().projection().getFirstYear().getEndingAccountSnapshots().getFirst().getAccount();
                assertNotSame(reopened.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst(), restoredAccount);
                var heat = field(s.dialog, "deterministicHeatMap", ClaimingStrategyHeatMapView.class);
                assertEquals(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT, heat.metric.getValue());
                assertFalse(field(s.dialog, "deterministicExportPdf", Button.class).isDisabled());
                var reference = field(s.dialog, "historicalExhaustiveReference", ExhaustiveSnapshot.CrossReference.class);
                assertEquals(new BigDecimal("123456.0012300"), reference.expectedPresentValue());
                assertEquals("Original Primary", field(s.dialog, "deterministicReportContext", IntegratedReportContext.class).primary());
                assertTrue(s.persistence.notice.getText().contains(COMPLETED.toString()));
                var factor = field(s.dialog, "primaryMortalityAdjustment", TextField.class);
                factor.setText("invalid"); assertTrue(s.persistence.current(), "Irrelevant mortality text must not invalidate deterministic results");
                var account = reopened.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst();
                account.setCurrentBalance(new BigDecimal("999.00")); reopened.markModified(); return null;
            });
            await(s.persistence.notice, "Outdated");
            var historical = fx(() -> report(s));
            assertTrue(historical.analysisType().contains("Outdated"));
            assertEquals(originalReport.assumptions(), historical.assumptions());
            assertEquals(originalReport.rankedTables(), historical.rankedTables());
            assertEquals(originalReport.visualization(), historical.visualization());
            assertTrue(historical.context().stream().anyMatch(line -> line.contains(COMPLETED.toString())));
            fx(() -> { reopened.getCurrentPlan().getAccountPortfolio().getAccounts().getFirst().setCurrentBalance(new BigDecimal("1000000.0000")); reopened.markModified(); return null; });
            await(s.persistence.notice, "Current Results");
            fx(() -> { assertTrue(field(s.dialog, "deterministicResultTabs", TabPane.class).isVisible()); return null; });
            assertArrayEquals(before, Files.readAllBytes(source.getCurrentFile()));
        } finally { stop(restarted); }
    }

    @Test void actualEligibleCallbackBuildsOffFxSavesAndMemoryOnlySurvivesReopen() throws Exception {
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results"))); var source = saved(store);
        try {
            try (var s = session(source)) {
                fx(() -> { s.dialog.exhaustiveCalculation = request -> (progress, cancel) -> { assertFalse(Platform.isFxApplicationThread()); return result; };
                    invoke(s.dialog, "runExhaustiveSearch"); return null; });
                await(s.persistence.notice, "Results saved.");
                assertTrue(store.inMemory(new AnalysisResultStore.Slot(source.getAnalysisSessionId(), KIND)).isPresent());
            }
            try (var s = session(source)) { await(s.persistence.notice, "Current Results"); }
            fx(() -> { source.newPlan(plan()); return null; });
            try (var s = session(source)) {
                fx(() -> { s.dialog.exhaustiveCalculation = request -> (p, c) -> result; invoke(s.dialog, "runExhaustiveSearch"); return null; });
                await(s.persistence.notice, "memory only");
            }
            try (var s = session(source)) { await(s.persistence.notice, "Current Results"); assertTrue(fx(s.persistence.notice::getText).contains("memory only")); }
        } finally { stop(store); }
    }

    @Test void retryAfterVerificationFailureDoesNotRecalculateAndKeepsPriorDurable() throws Exception {
        var fail = new AtomicBoolean();
        var repository = AnalysisSnapshotFaults.failVerification(dir.resolve("results"), fail::get);
        var store = new AnalysisResultStore(repository); var source = saved(store);
        var old = completion(source); store.accept(old); settle(store, old.planId(), KIND);
        var oldBytes = Files.readAllBytes(repository.path(old.planId(), KIND));
        try (var s = session(source)) {
            await(s.persistence.notice, "Current Results"); fail.set(true);
            var calls = new AtomicInteger();
            fx(() -> { s.dialog.exhaustiveCalculation = request -> (p, c) -> { calls.incrementAndGet(); return result; }; invoke(s.dialog, "runExhaustiveSearch"); return null; });
            await(s.persistence.notice, "could not be saved");
            assertArrayEquals(oldBytes, Files.readAllBytes(repository.path(old.planId(), KIND)));
            fail.set(false); fx(() -> { s.persistence.retry.fire(); return null; });
            await(s.persistence.notice, "Results saved."); assertEquals(1, calls.get());
        } finally { fail.set(false); store.retryFailed(); stop(store); }
    }

    @Test void canceledFailedObsoleteAndAllFailedReplacementRetainHistoricalResult() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo); var source = saved(store);
        var old = completion(source); store.accept(old); settle(store, old.planId(), KIND);
        byte[] original = Files.readAllBytes(repo.path(old.planId(), KIND));
        try (var s = session(source)) {
            await(s.persistence.notice, "Current Results");
            var model = fx(() -> field(s.dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
            for (String outcome : List.of("cancel", "obsolete", "failure", "all-failed")) {
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
                // Dismiss only the expected error alert; exercise the real production failure callback.
                javafx.collections.ListChangeListener<javafx.stage.Window> dismiss = change -> {
                    while (change.next()) if (change.wasAdded()) for (var w : change.getAddedSubList())
                        Platform.runLater(w::hide);
                };
                fx(() -> {
                    javafx.stage.Window.getWindows().addListener(dismiss);
                    s.dialog.exhaustiveCalculation = request -> (p, c) -> {
                        entered.countDown();
                        try { if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("Work release timeout"); }
                        catch (InterruptedException e) { throw new AssertionError(e); }
                        if (outcome.equals("failure")) throw new IllegalStateException("Synthetic test failure");
                        if (outcome.equals("all-failed")) {
                            var failures = result.entries().stream().map(e -> new IntegratedSocialSecurityCompleteStrategySearchEntry(
                                    e.generationOrder(), e.strategy(), OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                                    Optional.of(new IntegratedSocialSecurityStrategyEvaluationFailure(e.strategy(), "Synthetic failure",
                                            IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION)))).toList();
                            return new IntegratedSocialSecurityCompleteStrategySearchResult(result.currentPlanBaseline(), result.rankingMeasure(),
                                    failures, List.of(), OptionalInt.empty());
                        }
                        return result;
                    };
                    invoke(s.dialog, "runExhaustiveSearch"); return null;
                });
                try {
                    assertTrue(entered.await(10, TimeUnit.SECONDS));
                    fx(() -> {
                        assertSame(model, field(s.dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
                        assertTrue(field(s.dialog, "deterministicExportPdf", Button.class).isDisabled());
                        if (outcome.equals("cancel")) s.jobs.cancel();
                        if (outcome.equals("obsolete")) s.jobs.invalidate(SocialSecurityAnalyzerJobController.Change.PLAN);
                        return null;
                    });
                    release.countDown();
                    String expected = switch (outcome) { case "cancel" -> "cancelled"; case "obsolete" -> "Inputs changed";
                        case "failure" -> "failed before completion"; default -> "No successful candidates"; };
                    await(field(s.dialog, "exhaustiveStatus", Label.class), expected);
                    fx(() -> { assertSame(model, field(s.dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class)); return null; });
                    assertArrayEquals(original, Files.readAllBytes(repo.path(old.planId(), KIND)));
                    assertEquals(old.runId(), store.inMemory(new AnalysisResultStore.Slot(old.planId(), KIND)).orElseThrow().runId());
                } finally {
                    release.countDown(); fx(() -> { javafx.stage.Window.getWindows().removeListener(dismiss); return null; });
                }
            }
        } finally { stop(store); }
    }

    static final class BlockingRepository implements AnalysisSnapshotRepository {
        final JsonAnalysisSnapshotRepository delegate;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean blocked = new AtomicBoolean();
        BlockingRepository(Path root) { delegate = new JsonAnalysisSnapshotRepository(root); }
        public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind kind) throws java.io.IOException {
            if (kind == KIND && blocked.compareAndSet(false, true)) {
                entered.countDown();
                try { if (!release.await(15, TimeUnit.SECONDS)) throw new java.io.IOException("Test blocked-load timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new java.io.IOException(e); }
            }
            return delegate.load(id, kind);
        }
        public void save(AnalysisSnapshot snapshot) throws java.io.IOException { delegate.save(snapshot); }
        public Optional<AnalysisSnapshot> loadBackup(UUID id, AnalysisKind kind) throws java.io.IOException { return delegate.loadBackup(id, kind); }
        public void claimPlan(UUID id, Path file) throws java.io.IOException { delegate.claimPlan(id, file); }
    }

    @Test void planSwitchWhileLoadingRejectsObsoletePublication() throws Exception {
        var root = dir.resolve("results"); var originalStore = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(root));
        var original = saved(originalStore); var a = completion(original); originalStore.accept(a); settle(originalStore, a.planId(), KIND);
        var firstFile = original.getCurrentFile();
        original.newPlan(plan()); original.saveAs(dir.resolve("other.json"));
        var b = completion(original); originalStore.accept(b); settle(originalStore, b.planId(), KIND); var otherFile = original.getCurrentFile(); stop(originalStore);
        var blocked = new BlockingRepository(root); var store = new AnalysisResultStore(blocked); var source = source(store, "restart"); source.open(firstFile);
        try (var s = session(source)) {
            assertTrue(blocked.entered.await(10, TimeUnit.SECONDS));
            fx(() -> { source.open(otherFile); assertNull(field(s.dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class)); return null; });
            blocked.release.countDown(); await(s.persistence.notice, "Current Results");
            fx(() -> {
                assertEquals(b.planId(), field(s.persistence, "snapshot", AnalysisSnapshot.class).planId());
                assertEquals(0, s.jobs.generation()); return null;
            });
        } finally { blocked.release.countDown(); stop(store); }
    }

    @Test void acceptedReplacementWinsOverBlockedOlderRestoration() throws Exception {
        var root = dir.resolve("results"); var originalStore = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(root));
        var original = saved(originalStore); var old = completion(original); originalStore.accept(old); settle(originalStore, old.planId(), KIND); stop(originalStore);
        var blocked = new BlockingRepository(root); var store = new AnalysisResultStore(blocked); var source = source(store, "restart"); source.open(original.getCurrentFile());
        try (var s = session(source)) {
            assertTrue(blocked.entered.await(10, TimeUnit.SECONDS));
            fx(() -> { s.dialog.exhaustiveCalculation = request -> (p, c) -> result; invoke(s.dialog, "runExhaustiveSearch"); return null; });
            await(field(s.dialog, "exhaustiveStatus", Label.class), "search complete");
            var accepted = store.inMemory(new AnalysisResultStore.Slot(old.planId(), KIND)).orElseThrow();
            assertNotEquals(old.runId(), accepted.runId());
            blocked.release.countDown(); await(s.persistence.notice, "Results saved.");
            await(s.persistence.notice, accepted.completedAt().toString());
            fx(() -> { assertEquals(accepted.runId(), field(s.persistence, "snapshot", AnalysisSnapshot.class).runId()); return null; });
        } finally { blocked.release.countDown(); stop(store); }
    }

    @Test void saveAsCloneRestoresOriginalAndCopyIndependently() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo); var source = saved(store);
        var accepted = completion(source); store.accept(accepted); settle(store, accepted.planId(), KIND);
        var originalFile = source.getCurrentFile(); var original = repo.load(accepted.planId(), KIND).orElseThrow();
        source.saveAs(dir.resolve("copy.json"));
        assertTrue(source.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS).isEmpty());
        var cloned = repo.load(source.getAnalysisSessionId(), KIND).orElseThrow();
        assertNotEquals(accepted.planId(), cloned.planId()); assertEquals(accepted.planId(), cloned.originPlanId());
        assertEquals(original.payload(), cloned.payload()); assertEquals(original.completedAt(), cloned.completedAt());
        try {
            try (var s = session(source)) { await(s.persistence.notice, "Historical copy from another plan"); assertTrue(fx(s.persistence::current)); }
            source.open(originalFile);
            try (var s = session(source)) {
                await(s.persistence.notice, "Current Results");
                fx(() -> { assertEquals(accepted.planId(), field(s.persistence, "snapshot", AnalysisSnapshot.class).planId()); return null; });
            }
            assertEquals(original, repo.load(accepted.planId(), KIND).orElseThrow());
        } finally { stop(store); }
    }

    @Test void corruptSnapshotIsUnavailableAndPreserved() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo); var source = saved(store);
        repo.claimPlan(source.getAnalysisSessionId(), source.getCurrentFile());
        var file = repo.path(source.getAnalysisSessionId(), KIND); Files.createDirectories(file.getParent());
        byte[] corrupt = {1, 2, 3, 4}; Files.write(file, corrupt);
        try (var s = session(source)) {
            await(s.persistence.notice, "Incompatible or Unavailable");
            assertFalse(fx(s.persistence::exportable)); assertArrayEquals(corrupt, Files.readAllBytes(file));
            assertNull(fx(() -> field(s.dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class)));
        } finally { stop(store); }
    }

    @Test void closeDuringLoadCannotPublishIntoDisposedDialog() throws Exception {
        var root = dir.resolve("results"); var firstStore = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(root));
        var original = saved(firstStore); var old = completion(original); firstStore.accept(old); settle(firstStore, old.planId(), KIND); stop(firstStore);
        var blocked = new BlockingRepository(root); var store = new AnalysisResultStore(blocked); var source = source(store, "restart"); source.open(original.getCurrentFile());
        var s = session(source);
        try {
            assertTrue(blocked.entered.await(10, TimeUnit.SECONDS)); s.close(); blocked.release.countDown();
            // Two queue barriers cover the read and its dependent reconstruction stage, then drain FX callbacks.
            store.background(() -> null).toCompletableFuture().get(10, TimeUnit.SECONDS);
            store.background(() -> null).toCompletableFuture().get(10, TimeUnit.SECONDS);
            fx(() -> {
                assertEquals(SocialSecurityAnalyzerJobController.State.CLOSED, s.jobs.state());
                assertNull(field(s.dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class)); return null;
            });
        } finally { blocked.release.countDown(); stop(store); }
    }

    @Test void partialSuccessPersistsEveryFailureWithoutChangingOtherSlots() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo); var source = saved(store);
        var last = result.rankedSuccessfulEntries().getLast();
        var entries = new ArrayList<>(result.entries());
        entries.set(last.generationOrder() - 1, new IntegratedSocialSecurityCompleteStrategySearchEntry(last.generationOrder(), last.strategy(),
                OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(new IntegratedSocialSecurityStrategyEvaluationFailure(
                last.strategy(), "Retained structured test failure", IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION))));
        var partial = new IntegratedSocialSecurityCompleteStrategySearchResult(result.currentPlanBaseline(), result.rankingMeasure(), entries,
                result.rankedSuccessfulEntries().subList(0, result.rankedSuccessfulEntries().size() - 1), result.currentPlanRank());
        try (var s = session(source)) {
            fx(() -> { s.dialog.exhaustiveCalculation = request -> (p, c) -> partial; invoke(s.dialog, "runExhaustiveSearch"); return null; });
            await(s.persistence.notice, "Results saved.");
            var saved = (ExhaustiveSnapshot) repo.load(source.getAnalysisSessionId(), KIND).orElseThrow().payload();
            assertEquals(1, saved.restore().failedStrategyCount());
            assertEquals(last.generationOrder(), saved.entries().stream().filter(e -> e.failure() != null).findFirst().orElseThrow().order());
            assertEquals("Retained structured test failure", saved.entries().stream().filter(e -> e.failure() != null).findFirst().orElseThrow().failure().message());
            assertTrue(repo.load(source.getAnalysisSessionId(), AnalysisKind.COUPLE_QUICK).isEmpty());
            assertTrue(repo.load(source.getAnalysisSessionId(), AnalysisKind.COUPLE_WEIGHTED).isEmpty());
            assertTrue(repo.load(source.getAnalysisSessionId(), AnalysisKind.COUPLE_SOCIAL_SECURITY).isEmpty());
        } finally { stop(store); }
    }

    @Test void acceptedPendingWriteSurvivesDialogClose() throws Exception {
        var root = dir.resolve("results"); var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(root)); var source = saved(store);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var s = session(source);
        try {
            store.background(() -> { entered.countDown(); if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("I/O release timeout"); return null; });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            fx(() -> { s.dialog.exhaustiveCalculation = request -> (p, c) -> result; invoke(s.dialog, "runExhaustiveSearch"); return null; });
            await(field(s.dialog, "exhaustiveStatus", Label.class), "search complete");
            var slot = new AnalysisResultStore.Slot(source.getAnalysisSessionId(), KIND);
            assertEquals(AnalysisResultStore.Durability.PENDING, store.status(slot).orElseThrow().durability());
            s.close(); release.countDown(); settle(store, slot.planId(), KIND);
            assertTrue(new JsonAnalysisSnapshotRepository(root).load(slot.planId(), KIND).isPresent());
            try (var reopened = session(source)) { await(reopened.persistence.notice, "Current Results"); }
        } finally { release.countDown(); stop(store); }
    }
}
