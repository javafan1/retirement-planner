package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
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
import static org.junit.jupiter.api.Assertions.*;

class WeightedRestorationTest {
    @TempDir Path dir;
    private final List<ApplicationController> controllers = new ArrayList<>();
    @AfterEach void drainParameterWritesBeforeTemporaryDirectoryRemoval() throws Exception {
        for (var controller : controllers) {
            var parameters = controller.getAnalysisParameters();
            try { assertTrue(parameters.drain(Duration.ofSeconds(10)).complete()); }
            finally { parameters.shutdown(); }
        }
    }
    static final Instant COMPLETED = Instant.parse("2026-01-02T03:04:05Z");
    @BeforeAll static void startFx() throws Exception { ClaimingStrategyHeatMapViewTest.startFx(); }
    static <T> T fx(Callable<T> action) throws Exception { return IndividualCompletionPersistenceTest.fx(action); }
    static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(owner));
    }
    static void await(Label label, String text) throws Exception {
        var ready = new CompletableFuture<Void>();
        fx(() -> {
            if (label.getText().contains(text)) ready.complete(null);
            else label.textProperty().addListener((o, a, b) -> { if (b.contains(text)) ready.complete(null); });
            return null;
        });
        try { ready.get(20, TimeUnit.SECONDS); }
        catch (TimeoutException timeout) { fail("Expected " + text + "; visible: " + fx(label::getText), timeout); }
    }
    ApplicationController controller(AnalysisResultStore store, String name) throws Exception {
        var controller = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve(name + ".settings")), store);
        controllers.add(controller);
        return controller;
    }
    static AnalysisFingerprintService.Inputs coupleInputs(RetirementPlan plan) {
        var context = CurrentStrategyBaseline.fromPlan(plan);
        return new AnalysisFingerprintService.Inputs(BigDecimal.ONE, BigDecimal.ONE,
                plan.getPlanningAssumptions().getProjectionStartDate(), plan.getPlanningAssumptions().getProjectionStartDate(),
                new BigDecimal("0.010"), null, null, List.of(), context.elections().stream()
                .map(e -> new SnapshotContext.Section(e.name(), List.of(e.value(), e.source()))).toList(), null);
    }
    static AcceptedAnalysisCompletion coupleCompletion(ApplicationController source) {
        var kind = AnalysisKind.COUPLE_WEIGHTED;
        var f = new AnalysisFingerprintService();
        return new AcceptedAnalysisCompletion(source.getAnalysisSessionId(), source.getCurrentFile(), kind, UUID.randomUUID(), COMPLETED,
                f.fingerprint(kind, source.getCurrentPlan(), coupleInputs(source.getCurrentPlan())),
                f.effectiveContextFingerprint(kind, source.getCurrentPlan()), AnalysisVersions.algorithms(kind), Map.of(),
                SnapshotFixtures.couple(), SnapshotFixtures.context());
    }
    static AcceptedAnalysisCompletion individualCompletion(ApplicationController source, SinglePersonMortalityAnalysis.Result result) {
        var kind = AnalysisKind.INDIVIDUAL_WEIGHTED;
        var f = new AnalysisFingerprintService(); var plan = source.getCurrentPlan();
        var request = result.mortality().individual().request();
        var inputs = new AnalysisFingerprintService.Inputs(request.mortalityAdjustment().factor(), null,
                request.mortalityBaseDate(), result.valuationDate(), result.discountRate(), null, null, List.of(), List.of(), null);
        var c = SnapshotFixtures.individualContext(plan);
        var context = new SnapshotContext(c.household(), c.primary(), null, COMPLETED.minusSeconds(5),
                c.assumptions(), c.request(), c.comparisons(), c.preferences());
        return new AcceptedAnalysisCompletion(source.getAnalysisSessionId(), source.getCurrentFile(), kind, UUID.randomUUID(), COMPLETED,
                f.fingerprint(kind, plan, inputs), f.effectiveContextFingerprint(kind, plan), AnalysisVersions.algorithms(kind), Map.of(),
                new WeightedSnapshots.Payload(null, WeightedSnapshots.Individual.from(result)), context);
    }
    void assertHistoricalPdf(com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport report, String filename) throws Exception {
        var file = dir.resolve(filename);
        new com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerPdfExporter().export(report, file);
        try (var document = org.apache.pdfbox.Loader.loadPDF(file.toFile())) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(text.contains("Historical Analysis")); assertTrue(text.contains("Outdated"));
            assertTrue(text.contains(COMPLETED.toString()));
        }
    }
    static void settle(AnalysisResultStore store, UUID id, AnalysisKind kind) throws Exception {
        assertEquals(AnalysisResultStore.Durability.SAVED, store.whenSettled(new AnalysisResultStore.Slot(id, kind))
                .toCompletableFuture().get(20, TimeUnit.SECONDS).durability());
    }
    static void stop(AnalysisResultStore store) {
        var drain = store.drain(Duration.ofSeconds(10));
        assertTrue(drain.safeToExit(), drain.toString()); store.shutdownAfterSuccessfulDrain();
    }
    record CoupleView(SocialSecurityStrategyAnalyzerDialog dialog, SocialSecurityAnalyzerJobController jobs,
            SocialSecurityAnalysisJobCoordinator coordinator, WeightedResultPersistence persistence,
            LongevityWeightedIntegratedView view, long generationAtLoad) implements AutoCloseable {
        public void close() throws Exception {
            fx(() -> {
                var close = dialog.getClass().getDeclaredMethod("close"); close.setAccessible(true); close.invoke(dialog);
                field(dialog, "stage", javafx.stage.Stage.class).hide(); return null;
            }); coordinator.close();
        }
    }
    static CoupleView coupleView(ApplicationController source) throws Exception {
        var coordinator = new SocialSecurityAnalysisJobCoordinator();
        return fx(() -> {
            var jobs = new SocialSecurityAnalyzerJobController(coordinator);
            var dialog = new SocialSecurityStrategyAnalyzerDialog(null, source, jobs);
            var persistence = field(dialog, "weightedPersistence", WeightedResultPersistence.class);
            long generation = jobs.generation();
            field(dialog, "navigation", AnalyzerNavigationView.class).select(SocialSecurityAnalyzerJobController.Mode.WEIGHTED);
            return new CoupleView(dialog, jobs, coordinator, persistence, field(dialog, "weightedView", LongevityWeightedIntegratedView.class), generation);
        });
    }
    @Test void coupleRestartRestoresExactRankingsHeatMapsFrozenContextAndCurrencyWithoutJobAdmission() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results"));
        var initial = new AnalysisResultStore(repo);
        var source = controller(initial, "first");
        source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        var completion = coupleCompletion(source); initial.accept(completion);
        settle(initial, completion.planId(), completion.kind()); stop(initial);
        var planBytes = Files.readAllBytes(source.getCurrentFile());
        // Restart: fresh repository, store, controller and view; only durable data survives.
        var restoredStore = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var reopened = controller(restoredStore, "second"); reopened.open(source.getCurrentFile());
        try (var session = coupleView(reopened)) {
            await(session.persistence.notice, "Current Results");
            fx(() -> {
                assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, session.jobs.state());
                assertNull(session.jobs.mode());
                assertEquals(session.generationAtLoad, session.jobs.generation());
                var model = field(session.dialog, "weightedPresentation", LongevityWeightedIntegratedPresentation.class);
                assertEquals(completion.payload().couple(), WeightedSnapshots.Couple.from(model.result()));
                assertEquals(List.of(1, 2), model.result().rankedSuccessfulEntries().stream().map(e -> e.inputOrder()).toList());
                assertEquals(1, model.result().failedStrategyCount());
                assertTrue(session.persistence.notice.getText().contains(COMPLETED.toString()));
                var map = LongevityWeightedHeatMapAdapter.from(model);
                assertEquals(map.cells(), session.view.heatMap.reportModel().cells());
                for (var metric : session.view.heatMap.metric.getItems()) {
                    session.view.heatMap.metric.setValue(metric);
                    assertEquals(map.cells(), session.view.heatMap.reportModel().cells());
                }
                assertEquals(2, session.view.reportSelection().inputOrder());
                assertEquals(ClaimingStrategyHeatMapMetric.EXPECTED_PV_AFTER_TAX_ESTATE,
                        ClaimingStrategyHeatMapMetric.valueOf(completion.context().preferences().get("metric")));
                assertTrue(session.persistence.exportable());
                var factor = field(session.dialog, "primaryMortalityAdjustment", TextField.class);
                factor.setText("0.80");
                assertTrue(session.persistence.notice.getText().contains("Previous Results — Outdated"));
                assertTrue(session.persistence.exportable());
                factor.setText("unfinished");
                assertTrue(session.persistence.notice.getText().contains("Inputs Need Attention"));
                factor.setText("1");
                assertTrue(session.persistence.current());
                assertEquals("1", factor.getText());
                assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, session.jobs.state());
                return null;
            });
            assertArrayEquals(planBytes, Files.readAllBytes(source.getCurrentFile()));
            assertFalse(reopened.isModified());
        } finally { stop(restoredStore); }
    }
    @Test void savedResultIsOutdatedAfterFinancialEditAndCurrentAgainAfterRevert() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = controller(store, "source"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("p.json"));
        var completion = coupleCompletion(source); store.accept(completion); settle(store, completion.planId(), completion.kind());
        try (var session = coupleView(source)) {
            await(session.persistence.notice, "Current Results");
            var historical = fx(() -> {
                var capture = session.dialog.getClass().getDeclaredMethod("capturePdfReport", boolean.class);
                capture.setAccessible(true);
                var currentReport = (com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport) capture.invoke(session.dialog, true);
                var rate = field(session.dialog, "discountRate", TextField.class);
                rate.setText("2.0"); assertFalse(session.persistence.current());
                var report = (com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport) capture.invoke(session.dialog, true);
                assertEquals(currentReport.rankedTables(), report.rankedTables());
                assertEquals(currentReport.results(), report.results());
                assertEquals(currentReport.visualization(), report.visualization());
                assertTrue(report.context().getFirst().contains("Historical Analysis — Outdated"));
                assertTrue(report.context().contains("Original analysis completed: " + COMPLETED));
                assertEquals(completion.context().startedAt(), report.runStarted());
                assertEquals(completion.context().assumptions().stream().map(s -> s.lines()).toList(), report.assumptions().stream().map(s -> s.lines()).toList());
                rate.setText("1.0"); assertTrue(session.persistence.current());
                return report;
            });
            var expense = new com.daviddunn.retirementplanner.domain.financial.Expense("Synthetic spending", new BigDecimal("123.4500"));
            fx(() -> { source.getCurrentPlan().getHousehold().addExpense(expense); source.markModified(); return null; });
            await(session.persistence.notice, "Outdated");
            fx(() -> { source.getCurrentPlan().getHousehold().removeExpense(expense); source.markModified(); return null; });
            await(session.persistence.notice, "Current Results");
            fx(() -> { source.getCurrentPlan().getHousehold().getPrimaryPerson().setFirstName("Renamed"); source.markModified(); return null; });
            fx(() -> {
                assertTrue(session.persistence.current());
                var picker = field(session.dialog, "pvDate", DatePicker.class);
                picker.getEditor().setText("not a date");
                assertTrue(session.persistence.notice.getText().contains("Inputs Need Attention"));
                assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, session.jobs.state());
                return null;
            });
            assertHistoricalPdf(historical, "couple-outdated.pdf");
        } finally { stop(store); }
    }
    @Test void unknownSchemaDoesNotRenderAndHistoricalBytesArePreserved() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = controller(store, "source"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("p.json"));
        var completion = coupleCompletion(source); store.accept(completion); settle(store, completion.planId(), completion.kind()); stop(store);
        var cache = Files.walk(dir.resolve("results")).filter(p -> p.toString().endsWith(".json.gz")).findFirst().orElseThrow();
        var codec = new AnalysisSnapshotCodec();
        var raw = new String(codec.raw(completion.snapshot(codec)), java.nio.charset.StandardCharsets.UTF_8);
        byte[] unsupported = AnalysisSnapshotCodecTest.gzip(raw.replace("\"schemaVersion\":1", "\"schemaVersion\":99"));
        Files.write(cache, unsupported);
        var reopenedStore = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var reopened = controller(reopenedStore, "reopened"); reopened.open(source.getCurrentFile());
        try (var session = coupleView(reopened)) {
            await(session.persistence.notice, "Incompatible or Unavailable");
            fx(() -> { assertFalse(session.persistence.exportable()); assertTrue(session.view.table.getItems().isEmpty()); return null; });
            assertArrayEquals(unsupported, Files.readAllBytes(cache));
        } finally { stop(reopenedStore); }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void lateLoadCannotPublishAfterPlanSwitchOrViewClose(boolean close) throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var repo = new AnalysisResultStoreTest.Memory() {
            @Override public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind kind) {
                entered.countDown();
                try { assertTrue(release.await(15, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return super.load(id, kind);
            }
        };
        var store = new AnalysisResultStore(repo); var source = controller(store, "source");
        source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("first.json"));
        repo.saved = coupleCompletion(source).snapshot(new AnalysisSnapshotCodec());
        try (var session = coupleView(source)) {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            fx(() -> { source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); if (close) session.persistence.close(); return null; });
            release.countDown();
            store.background(() -> null).toCompletableFuture().get(10, TimeUnit.SECONDS);
            fx(() -> { assertTrue(session.view.table.getItems().isEmpty()); assertFalse(session.persistence.exportable()); return null; });
        } finally { release.countDown(); stop(store); }
    }
    @Test void coupleEligibleCompletionPersistsAndFailedReplacementKeepsPreviousSuccess() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = controller(store, "source"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("p.json"));
        try (var session = coupleView(source)) {
            fx(() -> {
                session.dialog.weightedCalculation = request -> SnapshotFixtures.couple().couple().restore();
                session.view.run.fire(); return null;
            });
            await(session.persistence.notice, "Results saved.");
            var snapshot = repo.load(source.getAnalysisSessionId(), AnalysisKind.COUPLE_WEIGHTED).orElseThrow();
            assertEquals(SnapshotFixtures.couple(), snapshot.payload());
            assertEquals(1, snapshot.payload().couple().entries().stream().filter(e -> e.failure() != null).count());
            assertFalse(snapshot.context().historicalComparisons().isEmpty());
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var idle = new CompletableFuture<Void>();
            fx(() -> {
                var original = field(session.jobs, "changed", Runnable.class);
                session.jobs.onChanged(() -> {
                    original.run();
                    if (session.jobs.state() == SocialSecurityAnalyzerJobController.State.IDLE) idle.complete(null);
                });
                session.dialog.weightedCalculation = request -> {
                    entered.countDown();
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
                    return SnapshotFixtures.couple().couple().restore();
                };
                session.view.run.fire(); return null;
            });
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            fx(() -> { session.jobs.cancel(); assertEquals(3, session.view.table.getItems().size()); return null; });
            release.countDown(); idle.get(10, TimeUnit.SECONDS);
            assertEquals(snapshot.runId(), repo.load(source.getAnalysisSessionId(), AnalysisKind.COUPLE_WEIGHTED).orElseThrow().runId());
            fx(() -> {
                session.dialog.weightedCalculation = request -> {
                    var c = SnapshotFixtures.couple().couple();
                    var failures = c.entries().stream().map(e -> new WeightedSnapshots.Entry(e.order(), e.strategy(), null,
                            new WeightedSnapshots.Failure("Synthetic failed candidate", IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION),
                            null, null, null, null)).toList();
                    return new WeightedSnapshots.Couple(c.objective(), c.metadata(), failures, List.of(), c.baseline(), c.work(),
                            c.elapsed(), c.status(), c.equivalence(), c.evaluationsAvoided()).restore();
                };
                session.view.run.fire(); return null;
            });
            await(session.view.status, "No candidate completed successfully");
            assertEquals(snapshot.runId(), repo.load(source.getAnalysisSessionId(), AnalysisKind.COUPLE_WEIGHTED).orElseThrow().runId());
            fx(() -> {
                session.dialog.weightedCalculation = request -> { throw new IllegalStateException("Injected replacement failure"); };
                session.view.run.fire(); return null;
            });
            await(session.view.status, "Analysis failed before completion");
            assertEquals(snapshot.runId(), repo.load(source.getAnalysisSessionId(), AnalysisKind.COUPLE_WEIGHTED).orElseThrow().runId());
            fx(() -> { assertEquals(3, session.view.table.getItems().size()); assertTrue(session.persistence.exportable()); return null; });
        } finally { stop(store); }
    }
    @Test void individualRestartRestoresNineEntriesFrozenPdfAndIndependentInputs() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = controller(store, "source"); source.newPlan(SinglePersonMortalityAnalysisTest.plan()); source.saveAs(dir.resolve("p.json"));
        var plan = source.getCurrentPlan();
        var result = SinglePersonMortalityAnalysisTest.run(plan, SinglePersonMortalityAnalysisTest.mortality(plan, "1.00"),
                "0.010", SinglePersonMortalityAnalysis.Mode.INTEGRATED);
        var kind = AnalysisKind.INDIVIDUAL_WEIGHTED;
        var completion = individualCompletion(source, result);
        store.accept(completion); settle(store, completion.planId(), kind); stop(store);
        var restarted = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results")));
        var reopened = controller(restarted, "reopened"); reopened.open(source.getCurrentFile());
        var coordinator = new SocialSecurityAnalysisJobCoordinator();
        var jobs = fx(() -> new SocialSecurityAnalyzerJobController(coordinator));
        var view = fx(() -> {
            var v = new SinglePersonIntegratedView(reopened.getCurrentPlan(), jobs);
            IndividualCompletionPersistence.attach(v.weightedView, reopened);
            v.ssView.factor.setText("1.23");
            v.weightedView.persistence.load(); return v;
        });
        try {
            await(view.weightedView.persistence.notice, "Current Results");
            var historical = fx(() -> {
                assertEquals(result.ranked(), List.copyOf(view.weightedView.table.getItems()));
                assertEquals("1.23", view.ssView.factor.getText()); assertEquals("1.00", view.weightedView.factor.getText());
                var report = view.weightedView.preparePdfReport();
                assertEquals(completion.context().startedAt(), report.runStarted());
                assertTrue(report.context().contains("Original analysis completed: " + COMPLETED));
                view.weightedView.discount.setText("3.5");
                assertTrue(view.weightedView.canExportPdf());
                var outdated = view.weightedView.preparePdfReport();
                assertTrue(outdated.context().getFirst().contains("Historical Analysis — Outdated"));
                view.weightedView.discount.setText("1.0"); assertTrue(view.weightedView.persistence.current());
                assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, jobs.state()); assertNull(jobs.mode());
                return outdated;
            });
            assertHistoricalPdf(historical, "individual-outdated.pdf");
            assertTrue(repo.load(source.getAnalysisSessionId(), AnalysisKind.INDIVIDUAL_SOCIAL_SECURITY).isEmpty());
        } finally {
            fx(() -> { view.weightedView.persistence.close(); jobs.close(); return null; }); coordinator.close(); stop(restarted);
        }
    }
    @Test void saveFailureKeepsDurableSnapshotAndRetryDoesNotRecalculate() throws Exception {
        var repo = new AnalysisResultStoreTest.Memory(); var store = new AnalysisResultStore(repo);
        var source = controller(store, "source"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("p.json"));
        var first = coupleCompletion(source); store.accept(first); settle(store, first.planId(), first.kind());
        var original = repo.saved;
        try (var session = coupleView(source)) {
            await(session.persistence.notice, "Current Results");
            repo.fail = true;
            var replacement = coupleCompletion(source);
            fx(() -> { session.persistence.accept(replacement); return null; });
            await(session.persistence.notice, "could not be saved");
            await(session.persistence.notice, "Current Results");
            assertEquals(original, repo.saved);
            fx(() -> {
                assertTrue(session.persistence.retry.isVisible()); assertTrue(session.persistence.exportable());
                assertTrue(session.persistence.notice.getText().contains("Failure category: transactional file writing"));
                return null;
            });
            repo.fail = false;
            fx(() -> { session.persistence.retry.fire(); return null; });
            await(session.persistence.notice, "Results saved.");
            assertEquals(replacement.runId(), repo.saved.runId());
            fx(() -> { assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, session.jobs.state()); return null; });
        } finally { repo.fail = false; store.retryFailed(); stop(store); }
    }
    @Test void saveAsCloneRestoresHistoricalTimeAndIndependentIdentity() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = controller(store, "source"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("original.json"));
        var first = coupleCompletion(source); store.accept(first); settle(store, first.planId(), first.kind());
        var original = repo.load(first.planId(), first.kind()).orElseThrow();
        source.saveAs(dir.resolve("copy.json"));
        assertTrue(source.getSnapshotCopyCompletion().toCompletableFuture().get(15, TimeUnit.SECONDS).isEmpty());
        assertNotEquals(first.planId(), source.getAnalysisSessionId());
        var cloned = repo.load(source.getAnalysisSessionId(), first.kind()).orElseThrow();
        assertEquals(first.planId(), cloned.originPlanId()); assertEquals(original.completedAt(), cloned.completedAt());
        assertEquals(original.payload(), cloned.payload()); assertEquals(original.context(), cloned.context());
        assertEquals(original, repo.load(first.planId(), first.kind()).orElseThrow());
        try (var session = coupleView(source)) {
            await(session.persistence.notice, "Historical copy from another plan");
            fx(() -> { assertTrue(session.persistence.current()); assertTrue(session.persistence.exportable()); return null; });
        } finally { stop(store); }
    }
    @Test void externalDuplicateUuidCannotBypassBindingThroughApplicationMemory() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = controller(store, "source"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("original.json"));
        var first = coupleCompletion(source); store.accept(first); settle(store, first.planId(), first.kind());
        var copy = dir.resolve("external-copy.json"); Files.copy(source.getCurrentFile(), copy);
        var other = controller(store, "other"); other.open(copy);
        try (var session = coupleView(other)) {
            await(session.persistence.notice, "Incompatible or Unavailable");
            fx(() -> { assertTrue(session.view.table.getItems().isEmpty()); assertFalse(session.persistence.exportable()); return null; });
            assertEquals(first.runId(), repo.load(first.planId(), first.kind()).orElseThrow().runId());
        } finally { stop(store); }
    }
    @Test void unsavedCoupleReopensInMemoryWithoutWritingAPlanOrSnapshot() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = controller(store, "source"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan());
        try {
            try (var first = coupleView(source)) {
                fx(() -> {
                    first.dialog.weightedCalculation = request -> SnapshotFixtures.couple().couple().restore();
                    first.view.run.fire(); return null;
                });
                await(first.persistence.notice, "memory only");
            }
            try (var reopened = coupleView(source)) {
                await(reopened.persistence.notice, "Current Results");
                fx(() -> { assertTrue(reopened.persistence.exportable()); assertEquals(3, reopened.view.table.getItems().size()); return null; });
            }
            assertNull(source.getCurrentFile());
            assertTrue(repo.load(source.getAnalysisSessionId(), AnalysisKind.COUPLE_WEIGHTED).isEmpty());
            try (var files = Files.walk(dir)) { assertEquals(0, files.filter(Files::isRegularFile).count()); }
        } finally { stop(store); }
    }
    @Test void alreadyOpenViewRechecksBindingWhenSwitchingToADuplicateUuidFile() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = controller(store, "source"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan());
        var original = dir.resolve("original.json"); source.saveAs(original);
        var first = coupleCompletion(source); store.accept(first); settle(store, first.planId(), first.kind());
        var copy = dir.resolve("copy.json"); Files.copy(original, copy);
        try (var session = coupleView(source)) {
            await(session.persistence.notice, "Current Results");
            fx(() -> { source.open(copy); return null; });
            await(session.persistence.notice, "Incompatible or Unavailable");
            fx(() -> { assertTrue(session.view.table.getItems().isEmpty()); assertTrue(session.view.exportPdf.isDisabled()); return null; });
            fx(() -> { source.open(original); return null; });
            await(session.persistence.notice, "Current Results");
            fx(() -> { assertEquals(3, session.view.table.getItems().size()); return null; });
        } finally { stop(store); }
    }
    @Test void olderRestoreCannotOverwriteANewAcceptedCompletion() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var repo = new AnalysisResultStoreTest.Memory() {
            @Override public Optional<AnalysisSnapshot> load(UUID id, AnalysisKind kind) {
                var previous = super.load(id, kind);
                entered.countDown();
                try { assertTrue(release.await(15, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new RuntimeException(e); }
                return previous;
            }
        };
        var store = new AnalysisResultStore(repo); var source = controller(store, "source");
        source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        var old = coupleCompletion(source).snapshot(new AnalysisSnapshotCodec()); repo.saved = old;
        try (var session = coupleView(source)) {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            fx(() -> {
                session.dialog.weightedCalculation = request -> SnapshotFixtures.couple().couple().restore();
                session.view.run.fire(); return null;
            });
            await(session.view.status, "Complete: 2 successful");
            var acceptedStart = fx(() -> field(session.dialog, "weightedReportContext", IntegratedReportContext.class).runStarted());
            assertTrue(acceptedStart.isAfter(COMPLETED));
            release.countDown(); settle(store, source.getAnalysisSessionId(), AnalysisKind.COUPLE_WEIGHTED);
            store.background(() -> null).toCompletableFuture().get(10, TimeUnit.SECONDS);
            fx(() -> {
                assertEquals(acceptedStart, field(session.dialog, "weightedReportContext", IntegratedReportContext.class).runStarted());
                assertEquals(session.persistence.notice.getText(), session.persistence.notice.getAccessibleText());
                return null;
            });
            assertNotEquals(old.runId(), repo.saved.runId());
        } finally { release.countDown(); stop(store); }
    }
    @Test void unsupportedFinancialMeaningIsNeitherRenderedNorAutomaticallyReplaced() throws Exception {
        var repo = new JsonAnalysisSnapshotRepository(dir.resolve("results")); var store = new AnalysisResultStore(repo);
        var source = controller(store, "source"); source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json"));
        var current = coupleCompletion(source).snapshot(new AnalysisSnapshotCodec());
        var unsupported = new AnalysisSnapshot(current.schemaVersion(), current.fingerprintVersion(), current.planId(), current.kind(), current.shape(),
                current.runId(), current.completedAt(), current.applicationBuild(), Map.of("weighted", "v999"), current.references(),
                current.inputFingerprint(), current.contextFingerprint(), current.payloadDigest(), current.payload(), current.context(), null);
        repo.save(unsupported);
        Path path;
        try (var files = Files.walk(dir.resolve("results"))) { path = files.filter(p -> p.toString().endsWith(".json.gz")).findFirst().orElseThrow(); }
        byte[] before = Files.readAllBytes(path);
        try (var session = coupleView(source)) {
            await(session.persistence.notice, "Incompatible or Unavailable");
            fx(() -> { assertFalse(session.persistence.exportable()); assertFalse(session.view.run.isDisabled()); return null; });
            assertThrows(java.io.IOException.class, () -> repo.save(current));
            assertArrayEquals(before, Files.readAllBytes(path));
            assertEquals(unsupported, repo.load(current.planId(), current.kind()).orElseThrow());
        } finally { stop(store); }
    }
}
