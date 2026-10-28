package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionMetrics;
import com.daviddunn.retirementplanner.domain.socialsecurity.analysis.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import javafx.scene.control.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.socialsecurity.WeightedRestorationTest.*;

class SocialSecurityAndQuickPdfTest {
    @TempDir Path dir;
    static final Path EVIDENCE = Path.of("target/phase11c-pdf");
    @BeforeAll static void prepare() throws Exception { CoupleSocialSecurityPersistenceTest.prepare(); SharedClaimingAgeGridTest.prepare(); }
    ApplicationController source(String settings) throws Exception {
        return new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve(settings)),
                new AnalysisResultStore(new JsonAnalysisSnapshotRepository(dir.resolve("results"))));
    }
    ApplicationController saved() throws Exception {
        var source = source("settings");
        fx(() -> { source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json")); return null; }); return source;
    }
    static IntegratedAnalyzerReport capture(SocialSecurityStrategyAnalyzerDialog dialog, boolean ss) throws Exception {
        Supplier<IntegratedAnalyzerReport> prepared = fx(() -> {
            var method = dialog.getClass().getDeclaredMethod("prepareAdditionalPdf", boolean.class); method.setAccessible(true);
            return (Supplier<IntegratedAnalyzerReport>) method.invoke(dialog, ss);
        });
        assertFalse(javafx.application.Platform.isFxApplicationThread());
        return prepared.get();
    }
    static boolean ready(SocialSecurityStrategyAnalyzerDialog dialog, boolean ss) throws Exception {
        return fx(() -> { var method = dialog.getClass().getDeclaredMethod("canExportAdditionalPdf", boolean.class); method.setAccessible(true); return (boolean) method.invoke(dialog, ss); });
    }
    static String pdf(IntegratedAnalyzerReport report, String name) throws Exception {
        Files.createDirectories(EVIDENCE); var destination = EVIDENCE.resolve(name + ".pdf");
        new IntegratedAnalyzerPdfExporter().export(report, destination);
        try (var document = Loader.loadPDF(destination.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Page 1 of " + document.getNumberOfPages()));
            for (int page : new LinkedHashSet<>(List.of(0, Math.min(1, document.getNumberOfPages() - 1), Math.min(2, document.getNumberOfPages() - 1), Math.min(3, document.getNumberOfPages() - 1), document.getNumberOfPages() - 1))) {
                javax.imageio.ImageIO.write(new PDFRenderer(document).renderImageWithDPI(page, 96), "png",
                        EVIDENCE.resolve(name + "-page-" + (page + 1) + ".png").toFile());
            }
            return text;
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void legacyCurrentAndOutdatedPdfUseOriginalNamesValuesAndDisclosure(boolean outdated) throws Exception {
        var source = saved(); var accepted = CoupleSocialSecurityPersistenceTest.completion(source);
        source.getAnalysisResults().accept(accepted); settle(source.getAnalysisResults(), accepted.planId(), accepted.kind());
        byte[] original = Files.readAllBytes(new JsonAnalysisSnapshotRepository(dir.resolve("results")).path(accepted.planId(), accepted.kind()));
        try (var view = CoupleSocialSecurityPersistenceTest.session(source)) {
            await(view.persistence().notice, "Current Results");
            if (outdated) fx(() -> { field(view.dialog(), "primaryMortalityAdjustment", TextField.class).setText("0.8"); return null; });
            await(view.persistence().notice, outdated ? "Outdated" : "Current Results");
            assertTrue(ready(view.dialog(), true));
            var session = fx(() -> field(view.dialog(), "socialSecurityReport", SocialSecurityReportSession.class));
            assertEquals(CoupleSocialSecurityPersistenceTest.result.evaluatedStrategies(), session.presentation().result().evaluatedStrategies());
            var report = capture(view.dialog(), true);
            assertEquals(accepted.completedAt(), session.completed()); assertEquals(accepted.context().startedAt(), report.runStarted());
            assertEquals(SocialSecurityReportSession.MISSING_NAME, report.household());
            var grid = (IntegratedAnalyzerReport.SocialSecurityGrid) report.visualization();
            for (var cell : grid.cells()) assertEquals(session.presentation().result().retirementGrid().cellFor(cell.primaryAge(), cell.spouseAge()).orElseThrow().expectedPresentValue(), cell.presentValue());
            assertEquals(session.presentation().rankedStrategies().size(), report.rankedTables().getFirst().rows().size());
            long revision = source.getSourcePlanRevision();
            String text = pdf(report, outdated ? "ss-legacy-outdated" : "ss-legacy-current");
            assertTrue(text.contains(SocialSecurityReportSession.MISSING_NAME));
            assertTrue(text.contains(session.context().primaryName())); assertTrue(text.contains(session.context().spouseName()));
            assertTrue(text.contains("Stage 2")); assertTrue(text.contains("Expected PV"));
            if (outdated) assertTrue(text.contains("Historical Analysis"));
            assertEquals(revision, source.getSourcePlanRevision());
            assertArrayEquals(original, Files.readAllBytes(new JsonAnalysisSnapshotRepository(dir.resolve("results")).path(accepted.planId(), accepted.kind())));
            fx(() -> { field(view.dialog(), "primaryMortalityAdjustment", TextField.class).setText("invalid pending"); return null; });
            assertTrue(ready(view.dialog(), true));
            assertTrue(capture(view.dialog(), true).context().getFirst().contains("Inputs Need Attention"));
        } finally { SocialSecurityParameterPersistenceTest.stop(source); }
    }

    @Test void fullStageOneGridAndEveryRankedRowPreserveScalesTiesAndRetention() throws Exception {
        var model = SharedClaimingAgeGridTest.presentation;
        var context = CoupleSocialSecurityPersistenceTest.fixture(true, false);
        var session = new SocialSecurityReportSession(UUID.randomUUID(), UUID.randomUUID(), "Original household with a deliberately long name for readable wrapping in the landscape report", true,
                context, model, COMPLETED.minusSeconds(2), COMPLETED);
        var pair = model.result().retirementGrid().highestExpectedPresentValue().highestCells().getFirst();
        var report = SocialSecurityOnlyReportAdapter.capture(session, new ClaimingAgeGridView.Pair(pair.primaryClaimAge(), pair.spouseClaimAge()), List.of("Current Results", "Pending - not durably saved"), COMPLETED.plusSeconds(5));
        var grid = (IntegratedAnalyzerReport.SocialSecurityGrid) report.visualization();
        assertEquals(81, grid.cells().size());
        assertEquals(model.result().retirementGrid().primaryClaimAges(), grid.primaryAges());
        for (var cell : grid.cells()) {
            var original = model.result().retirementGrid().cellFor(cell.primaryAge(), cell.spouseAge()).orElseThrow();
            assertEquals(original.expectedPresentValue(), cell.presentValue());
            assertEquals(model.advancedToStageTwo(cell.primaryAge(), cell.spouseAge()), cell.retained());
            assertEquals(model.result().retirementGrid().highestExpectedPresentValue().highestCells().contains(original), cell.optimal());
        }
        assertTrue(grid.cells().stream().anyMatch(c -> c.optimal() && c.retained() && c.selected()));
        assertEquals(model.rankedStrategies().stream().map(r -> Integer.toString(r.rank())).toList(), report.rankedTables().getFirst().rows().stream().map(r -> r.get(1)).toList());
        String text = pdf(report, "ss-full-grid");
        assertTrue(text.contains("Pending")); assertTrue(text.contains("Stage 1"));
        // Independently computed zero-benefit fixture produces exact ties across the complete ranking.
        var zeroContext = CoupleSocialSecurityPersistenceTest.fixture(true, true);
        var zero = SocialSecurityStrategyAnalyzerPresentation.from(new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(zeroContext.request()));
        var tied = SocialSecurityOnlyReportAdapter.capture(new SocialSecurityReportSession(UUID.randomUUID(), UUID.randomUUID(), "Ties", true, zeroContext, zero, COMPLETED, COMPLETED), null, List.of("Memory Only"), COMPLETED);
        assertEquals(zero.rankedStrategies().size(), tied.rankedTables().getFirst().rows().size());
        assertTrue(((IntegratedAnalyzerReport.SocialSecurityGrid) tied.visualization()).cells().stream().allMatch(c -> c.optimal() && c.tier() == com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette.UNAVAILABLE));
        assertTrue(tied.rankedTables().getFirst().rows().stream().allMatch(row -> row.get(1).equals("1")));
    }

    @Test void newAcceptedNameAndTimestampsSurviveRestartPlanRenameAndSaveAsWithoutCalculation() throws Exception {
        var source = saved(); String originalName = source.getCurrentPlan().getHousehold().getHouseholdName();
        IntegratedAnalyzerReport originalReport;
        try (var view = CoupleSocialSecurityPersistenceTest.session(source)) {
            fx(() -> {
                var date = field(view.dialog(), "mortalityDate", DatePicker.class); date.setValue(LocalDate.of(2082, 1, 1));
                date.getEditor().setText(date.getConverter().toString(date.getValue()));
                view.dialog().socialSecurityCalculation = request -> (p, c) -> new SocialSecuritySurvivorClaimingOptimizationCalculator().calculate(request, p, c);
                field(view.dialog(), "runButton", Button.class).fire(); return null;
            });
            await(view.persistence().notice, "Results saved");
            var accepted = fx(() -> field(view.dialog(), "socialSecurityReport", SocialSecurityReportSession.class));
            assertEquals(originalName, accepted.household());
            var stored = new JsonAnalysisSnapshotRepository(dir.resolve("results")).load(source.getAnalysisSessionId(), AnalysisKind.COUPLE_SOCIAL_SECURITY).orElseThrow();
            assertEquals(originalName, stored.context().household()); assertEquals(accepted.completed(), stored.completedAt());
            var fresh = capture(view.dialog(), true); assertEquals(originalName, fresh.household());
            originalReport = fresh;
            fx(() -> { source.getCurrentPlan().getHousehold().getPrimaryPerson().setLastName("Later renamed"); source.markModified(); source.save(); return null; });
            assertEquals(originalName, capture(view.dialog(), true).household());
        }
        var originalFile = source.getCurrentFile();
        fx(() -> { source.saveAs(dir.resolve("clone.json")); return null; });
        SocialSecurityParameterPersistenceTest.stop(source);
        for (var file : List.of(originalFile, dir.resolve("clone.json"))) {
            var reopened = source("reopened-" + file.getFileName()); fx(() -> { reopened.open(file); return null; });
            try (var view = CoupleSocialSecurityPersistenceTest.session(reopened)) {
                await(view.persistence().notice, "Current Results");
                var report = capture(view.dialog(), true);
                assertEquals(originalName, report.household());
                assertEquals(originalReport.rankedTables(), report.rankedTables());
                assertEquals(originalReport.assumptions(), report.assumptions());
                assertEquals(originalReport.runStarted(), report.runStarted());
                assertEquals(((IntegratedAnalyzerReport.SocialSecurityGrid) originalReport.visualization()).cells().stream().map(c -> c.presentValue()).toList(),
                        ((IntegratedAnalyzerReport.SocialSecurityGrid) report.visualization()).cells().stream().map(c -> c.presentValue()).toList());
                assertFalse(report.context().contains(SocialSecurityReportSession.MISSING_NAME));
                assertEquals(0, view.jobs().generation());
                pdf(report, "ss-restored-new-name-" + file.getFileName());
            } finally { SocialSecurityParameterPersistenceTest.stop(reopened); }
        }
    }

    @Test void actualCoupleNameIsOnlyTrustedWithRecordedProvenance() throws Exception {
        var source = CoupleSocialSecurityPersistenceTest.context;
        var model = SocialSecurityStrategyAnalyzerPresentation.from(CoupleSocialSecurityPersistenceTest.result);
        var fresh = new SocialSecurityReportSession(UUID.randomUUID(), UUID.randomUUID(), "Couple", true, source, model, COMPLETED, COMPLETED);
        assertEquals("Couple", fresh.reportHousehold());
        assertEquals(SocialSecurityReportSession.MISSING_NAME, new SocialSecurityReportSession(fresh.planId(), fresh.runId(), "Couple", false, source, model, COMPLETED, COMPLETED).reportHousehold());
        var controller = saved();
        try {
            var legacy = CoupleSocialSecurityPersistenceTest.completion(controller).snapshot(new AnalysisSnapshotCodec());
            var codec = new AnalysisSnapshotCodec();
            for (boolean recorded : List.of(false, true)) {
                var original = legacy.context();
                var context = new SnapshotContext("Couple", original.primary(), original.spouse(), original.startedAt(), original.assumptions(),
                        recorded ? List.of(new SnapshotContext.Section(SocialSecurityReportSession.HOUSEHOLD_SOURCE, List.of("Couple"))) : List.of(),
                        original.comparisons(), original.preferences());
                var saved = new AnalysisSnapshot(legacy.schemaVersion(), legacy.fingerprintVersion(), legacy.planId(), legacy.kind(), legacy.shape(), legacy.runId(), legacy.completedAt(),
                        legacy.applicationBuild(), legacy.algorithms(), legacy.references(), legacy.inputFingerprint(), legacy.contextFingerprint(), codec.digest(legacy.payload(), context), legacy.payload(), context, null);
                var copy = codec.decode(codec.encode(saved));
                assertEquals(saved, copy);
                assertEquals(recorded ? "Couple" : SocialSecurityReportSession.MISSING_NAME, SocialSecurityReportSession.restored(copy, source, model).reportHousehold());
                assertEquals(legacy.inputFingerprint(), copy.inputFingerprint());
            }
        } finally { SocialSecurityParameterPersistenceTest.stop(controller); }
    }

    static ProjectionMetrics metrics(String value) {
        var n = new BigDecimal(value);
        return new ProjectionMetrics(n, n, n, n, n, n, n, n, n, n, n, n, n, n);
    }
    @Test void snapshotWriteFailureStillAllowsSafeExportAndRetryWithoutCalculation() throws Exception {
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var repository = AnalysisSnapshotFaults.failVerification(dir.resolve("results"), fail::get);
        var store = new AnalysisResultStore(repository);
        var source = new ApplicationController(new JsonRetirementPlanRepository(), new JsonApplicationSettingsRepository(dir.resolve("settings")), store);
        fx(() -> { source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); source.saveAs(dir.resolve("plan.json")); return null; });
        var old = CoupleSocialSecurityPersistenceTest.completion(source); store.accept(old); settle(store, old.planId(), old.kind());
        byte[] bytes = Files.readAllBytes(repository.path(old.planId(), old.kind()));
        try (var view = CoupleSocialSecurityPersistenceTest.session(source)) {
            await(view.persistence().notice, "Current Results");
            fail.set(true);
            var newer = CoupleSocialSecurityPersistenceTest.completion(source);
            fx(() -> { view.persistence().accept(newer); return null; });
            await(view.persistence().notice, "could not be saved");
            assertTrue(ready(view.dialog(), true));
            assertTrue(capture(view.dialog(), true).context().stream().anyMatch(s -> s.startsWith("Save Failed")));
            assertArrayEquals(bytes, Files.readAllBytes(repository.path(old.planId(), old.kind())));
            fail.set(false); fx(() -> { view.persistence().retry.fire(); return null; });
            settle(store, newer.planId(), newer.kind());
            await(view.persistence().notice, "Results saved");
            assertTrue(capture(view.dialog(), true).context().stream().anyMatch(s -> s.startsWith("Saved")));
            assertEquals(0, fx(view.jobs()::generation));
        } finally { fail.set(false); store.retryFailed(); SocialSecurityParameterPersistenceTest.stop(source); }
    }

    @Test void freshQuickChangeAndRevertSynchronizesUiAndPdfCurrency() throws Exception {
        var source = saved();
        var completed = CoupleSocialSecurityPersistenceTest.completion(source);
        source.getAnalysisResults().accept(completed);
        settle(source.getAnalysisResults(), completed.planId(), completed.kind());
        try (var view = CoupleSocialSecurityPersistenceTest.session(source)) {
            await(view.persistence().notice, "Current Results");
            fx(() -> {
                view.dialog().quickCalculation = input -> (p, c) -> comparison(input.selected());
                field(view.dialog(), "integratedRunButton", Button.class).fire(); return null;
            });
            await(field(view.dialog(), "integratedStatus", Label.class), "strategies evaluated");
            var persistence = field(view.dialog(), "quickPersistence", WeightedResultPersistence.class);
            await(persistence.notice, "Current Results");
            var original = capture(view.dialog(), false);
            var count = field(view.dialog(), "integratedCandidateCount", Spinner.class);
            var prior = fx(() -> (Integer) count.getValue());
            fx(() -> { count.getValueFactory().setValue(prior == 1 ? 2 : 1); return null; });
            await(persistence.notice, "Outdated");
            assertTrue(capture(view.dialog(), false).context().getFirst().startsWith("Historical Analysis"));
            fx(() -> { count.getValueFactory().setValue(prior); return null; });
            await(persistence.notice, "Current Results");
            assertEquals("", fx(() -> field(view.dialog(), "integratedStale", Label.class).getText()));
            var reverted = capture(view.dialog(), false);
            assertTrue(reverted.context().getFirst().startsWith("Current Results"));
            assertEquals(original.rankedTables(), reverted.rankedTables());
            pdf(reverted, "phase12g-quick-reverted");
            fx(() -> {
                field(view.dialog(), "navigation", AnalyzerNavigationView.class).select(SocialSecurityAnalyzerJobController.Mode.QUICK);
                var stage = field(view.dialog(), "stage", javafx.stage.Stage.class);
                stage.setMinWidth(0); stage.setMinHeight(0);
                var previousScene = stage.getScene(); var root = previousScene.getRoot();
                previousScene.setRoot(new javafx.scene.layout.Pane());
                var light = new javafx.scene.Scene(root, 1180, 820); light.getStylesheets().setAll(previousScene.getStylesheets());
                stage.setScene(light); stage.sizeToScene(); stage.show(); root.applyCss(); root.layout();
                Files.createDirectories(Path.of("target/phase12g-previews"));
                javax.imageio.ImageIO.write(SharedClaimingAgeGridTest.toBuffered(root.snapshot(null, null)), "png",
                        Path.of("target/phase12g-previews/quick-reverted-light.png").toFile());
                light.setRoot(new javafx.scene.layout.Pane());
                var dark = new javafx.scene.Scene(root, 900, 650); dark.getStylesheets().setAll(light.getStylesheets());
                stage.setScene(dark); stage.sizeToScene(); stage.show();
                root.setStyle("-fx-base: #252a33; -fx-background: #252a33; -fx-text-fill: #eeeeee;");
                root.applyCss(); root.layout();
                javax.imageio.ImageIO.write(SharedClaimingAgeGridTest.toBuffered(root.snapshot(null, null)), "png",
                        Path.of("target/phase12g-previews/quick-reverted-dark.png").toFile());
                stage.hide(); return null;
            });
        } finally { SocialSecurityParameterPersistenceTest.stop(source); }
    }

    @Test void quickAcceptedReportSurvivesCanceledAndAllFailedReplacementAndRestoresAfterClose() throws Exception {
        var source = saved();
        var completed = CoupleSocialSecurityPersistenceTest.completion(source);
        source.getAnalysisResults().accept(completed); settle(source.getAnalysisResults(), completed.planId(), completed.kind());
        try (var view = CoupleSocialSecurityPersistenceTest.session(source)) {
            await(view.persistence().notice, "Current Results");
            fx(() -> {
                view.dialog().quickCalculation = input -> (p, c) -> comparison(input.selected());
                field(view.dialog(), "integratedRunButton", Button.class).fire(); return null;
            });
            await(field(view.dialog(), "integratedStatus", Label.class), "strategies evaluated");
            assertTrue(ready(view.dialog(), false));
            await(field(view.dialog(), "quickPersistence", WeightedResultPersistence.class).notice, "Current Results");
            var frozen = fx(() -> field(view.dialog(), "quickReport", QuickReportSession.class));
            var original = capture(view.dialog(), false);
            assertEquals(completed.runId(), frozen.prepared().prerequisiteRunId());
            // Stale prerequisite blocks a new run while preserving the accepted, exportable comparison.
            fx(() -> { field(view.dialog(), "primaryMortalityAdjustment", TextField.class).setText("0.8"); return null; });
            assertTrue(fx(() -> field(view.dialog(), "integratedRunButton", Button.class).isDisabled()));
            var historical = capture(view.dialog(), false);
            assertTrue(historical.context().getFirst().startsWith("Inputs Need Attention"),
                    "The new-run SS prerequisite is unavailable; the original comparison remains exportable.");
            assertEquals(original.rankedTables(), historical.rankedTables());
            assertEquals(original.assumptions(), historical.assumptions());
            fx(() -> { field(view.dialog(), "primaryMortalityAdjustment", TextField.class).setText("1"); return null; });
            var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
            fx(() -> {
                view.dialog().quickCalculation = input -> (p, c) -> {
                    entered.countDown();
                    try { release.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.util.concurrent.CancellationException(); }
                    return comparison(input.selected());
                };
                field(view.dialog(), "integratedRunButton", Button.class).fire(); return null;
            });
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            fx(() -> { view.jobs().cancel(); return null; }); release.countDown();
            for (int i = 0; i < 100 && fx(view.jobs()::state) != SocialSecurityAnalyzerJobController.State.IDLE; i++) Thread.sleep(20);
            assertSame(frozen, fx(() -> field(view.dialog(), "quickReport", QuickReportSession.class)));
            assertTrue(ready(view.dialog(), false));
            fx(() -> {
                view.dialog().quickCalculation = input -> (p, c) -> {
                    var comparison = comparison(input.selected()).comparison();
                    return IntegratedSocialSecurityComparisonPresentation.from(new IntegratedSocialSecurityStrategyComparisonResult(comparison.currentPlanBaseline(), comparison.suppliedCandidateCount(),
                            comparison.entries().stream().map(e -> IntegratedSocialSecurityStrategyComparisonEntry.failure(e.callerOrder(), e.strategy(), e.socialSecurityOnlyExpectedValue(),
                                    new IntegratedSocialSecurityStrategyEvaluationFailure(e.strategy(), "All failed", IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION))).toList()), input.selected());
                };
                field(view.dialog(), "integratedRunButton", Button.class).fire(); return null;
            });
            await(field(view.dialog(), "integratedStatus", Label.class), "No successful candidates");
            assertSame(frozen, fx(() -> field(view.dialog(), "quickReport", QuickReportSession.class)));
            assertEquals(original.rankedTables(), capture(view.dialog(), false).rankedTables());
            fx(() -> {
                view.dialog().quickCalculation = input -> (p, c) -> {
                    throw new IllegalStateException("Injected fatal comparison failure");
                };
                field(view.dialog(), "integratedRunButton", Button.class).fire(); return null;
            });
            for (int i = 0; i < 200 && !ready(view.dialog(), false); i++) Thread.sleep(20);
            assertTrue(ready(view.dialog(), false));
            fx(() -> { SocialSecurityParameterPersistenceTest.dismissError(); return null; });
            assertSame(frozen, fx(() -> field(view.dialog(), "quickReport", QuickReportSession.class)));
            // A newly attached SS result must not rewrite the older comparison's prerequisite context.
            var newer = CoupleSocialSecurityPersistenceTest.completion(source);
            fx(() -> { view.persistence().accept(newer); return null; });
            settle(source.getAnalysisResults(), newer.planId(), newer.kind());
            assertEquals(completed.runId(), fx(() -> field(view.dialog(), "quickReport", QuickReportSession.class)).prepared().prerequisiteRunId());
            var quick = source.getAnalysisResults().inMemory(new AnalysisResultStore.Slot(source.getAnalysisSessionId(), AnalysisKind.COUPLE_QUICK)).orElseThrow();
            assertEquals(frozen.prepared().runId(), quick.runId());
            settle(source.getAnalysisResults(), quick.planId(), quick.kind());
            fx(() -> { source.newPlan(LongevityWeightedAnalysisRequestFactoryTest.plan()); return null; });
            assertFalse(ready(view.dialog(), false)); assertFalse(ready(view.dialog(), true));
        }
        assertTrue(Files.exists(new JsonAnalysisSnapshotRepository(dir.resolve("results")).path(completed.planId(), AnalysisKind.COUPLE_QUICK)));
        fx(() -> { source.open(dir.resolve("plan.json")); return null; });
        try (var next = CoupleSocialSecurityPersistenceTest.session(source)) {
            await(next.persistence().notice, "Current Results");
            fx(() -> { field(next.dialog(), "navigation", AnalyzerNavigationView.class).select(SocialSecurityAnalyzerJobController.Mode.QUICK); return null; });
            var quick = field(next.dialog(), "quickPersistence", WeightedResultPersistence.class);
            for (int i = 0; i < 500 && !ready(next.dialog(), false); i++) Thread.sleep(20);
            assertTrue(ready(next.dialog(), false));
            var restored = fx(() -> field(next.dialog(), "quickReport", QuickReportSession.class));
            assertEquals(completed.runId(), restored.prepared().prerequisiteRunId());
            assertEquals(0, fx(next.jobs()::generation));
            var count = field(next.dialog(), "integratedCandidateCount", javafx.scene.control.Spinner.class);
            var priorCount = fx(() -> (Integer) count.getValue());
            fx(() -> { count.getValueFactory().setValue(priorCount == 1 ? 2 : 1); return null; });
            await(quick.notice, "Outdated");
            fx(() -> { count.getValueFactory().setValue(priorCount); return null; });
            await(quick.notice, "Current Results");
            assertSame(restored, fx(() -> field(next.dialog(), "quickReport", QuickReportSession.class)));
        } finally { SocialSecurityParameterPersistenceTest.stop(source); }
    }

    @Test void missingAndIncompatibleResultsAreNotExportable() throws Exception {
        var source = saved();
        try (var view = CoupleSocialSecurityPersistenceTest.session(source)) {
            assertFalse(ready(view.dialog(), true)); assertFalse(ready(view.dialog(), false));
        }
        var accepted = CoupleSocialSecurityPersistenceTest.completion(source);
        var codec = new AnalysisSnapshotCodec(); var original = accepted.snapshot(codec);
        var incompatible = new AnalysisSnapshot(original.schemaVersion(), original.fingerprintVersion(), original.planId(), original.kind(), original.shape(), original.runId(), original.completedAt(), original.applicationBuild(),
                Map.of("unsupported-financial-algorithm", "future"), original.references(), original.inputFingerprint(), original.contextFingerprint(), original.payloadDigest(), original.payload(), original.context(), null);
        var repository = new JsonAnalysisSnapshotRepository(dir.resolve("results")); repository.save(incompatible);
        byte[] bytes = Files.readAllBytes(repository.path(original.planId(), original.kind()));
        try (var view = CoupleSocialSecurityPersistenceTest.session(source)) {
            await(view.persistence().notice, "Incompatible");
            assertFalse(ready(view.dialog(), true));
            assertArrayEquals(bytes, Files.readAllBytes(repository.path(original.planId(), original.kind())));
        } finally { SocialSecurityParameterPersistenceTest.stop(source); }
    }
    static IntegratedSocialSecurityComparisonPresentation comparison(List<SocialSecurityStrategyAnalyzerPresentation.RankedStrategy> selected) {
        var baseline = new IntegratedSocialSecurityStrategyResult(selected.getFirst().cell().strategy(), new Projection(), metrics("100000.0012300"), true, List.of("Original baseline warning"));
        var entries = new ArrayList<IntegratedSocialSecurityStrategyComparisonEntry>();
        for (int i = 0; i < selected.size(); i++) {
            var row = selected.get(i); var strategy = row.cell().strategy();
            if (i == selected.size() - 1 && selected.size() > 1)
                entries.add(IntegratedSocialSecurityStrategyComparisonEntry.failure(i + 1, strategy, Optional.of(row.cell().expectedValue()),
                        new IntegratedSocialSecurityStrategyEvaluationFailure(strategy, "Original retained candidate failure", IntegratedSocialSecurityStrategyEvaluationFailure.Category.EVALUATION)));
            else entries.add(IntegratedSocialSecurityStrategyComparisonEntry.success(i + 1, strategy, Optional.of(row.cell().expectedValue()),
                    new IntegratedSocialSecurityStrategyResult(strategy, new Projection(), metrics("200000.0012300"), true, List.of("Original candidate warning")), metrics("100000.0000000")));
        }
        return IntegratedSocialSecurityComparisonPresentation.from(new IntegratedSocialSecurityStrategyComparisonResult(baseline, selected.size(), entries), selected);
    }
    @Test void quickPureReportRetainsOriginalOrderElectionsMetricsFailuresAndNoProjectionGraph() throws Exception {
        var selected = CoupleSocialSecurityPersistenceTest.result.evaluatedStrategies().stream().limit(3)
                .map(c -> new SocialSecurityStrategyAnalyzerPresentation.RankedStrategy(1, c)).toList();
        var result = comparison(selected);
        var prepared = new QuickReportSession.Prepared(UUID.randomUUID(), UUID.randomUUID(), new IntegratedReportContext("Frozen Original Household", "Original Primary", "Original Spouse", COMPLETED.minusSeconds(2),
                List.of(new IntegratedAnalyzerReport.Section("Frozen financial assumptions", List.of("Original economic return 3%"))), "Unused weighted context"), "a".repeat(64), UUID.randomUUID(), 3,
                selected.stream().map(r -> new QuickReportSession.Candidate(r.rank(), r.cell().strategy(), r.cell().expectedValue())).toList(), List.of(new IntegratedAnalyzerReport.Section("Original SS prerequisite", List.of("Original conditioning and discount"))));
        var frozen = QuickReportSession.accepted(prepared, result, COMPLETED);
        assertEquals(result.comparison().currentPlanBaseline().metrics(), frozen.baseline().metrics());
        for (int i = 0; i < result.rows().size(); i++) {
            assertEquals(result.rows().get(i).entry().differencesFromCurrentPlan(), frozen.rows().get(i).differences());
            assertEquals(result.rows().get(i).entry().socialSecurityOnlyExpectedValue(), frozen.rows().get(i).expected());
            assertEquals(result.rows().get(i).entry().failure(), frozen.rows().get(i).failure());
        }
        var current = QuickComparisonReportAdapter.capture(frozen, true, COMPLETED.plusSeconds(4));
        var outdated = QuickComparisonReportAdapter.capture(frozen, false, COMPLETED.plusSeconds(5));
        assertInstanceOf(IntegratedAnalyzerReport.TablesOnly.class, current.visualization());
        assertEquals(current.rankedTables(), outdated.rankedTables()); assertEquals(current.assumptions(), outdated.assumptions());
        assertEquals(List.of("1", "2", "3"), current.rankedTables().get(1).rows().stream().map(r -> r.getFirst()).toList());
        assertEquals(42, current.rankedTables().get(3).rows().size());
        assertEquals(new BigDecimal("100000.0012300"), frozen.baseline().metrics().totalTaxes());
        assertTrue(current.selectedSummary().getFirst().contains("successful: 2; failed: 1"));
        String text = pdf(outdated, "quick-outdated");
        for (String expected : List.of("Memory-only", "Original retained candidate failure", "Original baseline warning", "Original candidate warning", "Original economic return 3%", "Historical Analysis", "Roth conversions", "RMDs", "Medicare premiums")) assertTrue(text.contains(expected), expected);
        assertFalse(text.contains("CLAIMING-AGE HEAT MAP"));
        pdf(current, "quick-current");
    }
}

