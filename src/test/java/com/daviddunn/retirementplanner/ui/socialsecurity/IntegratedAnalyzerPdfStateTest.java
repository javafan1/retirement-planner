package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class IntegratedAnalyzerPdfStateTest {
    @TempDir Path directory;
    @BeforeAll static void startFx() throws Exception { ClaimingStrategyHeatMapViewTest.startFx(); }

    @Test void buttonsRequireCompletedCurrentResultsAndRespectBusyAndExportState() throws Exception {
        fx(f -> {
            assertTrue(f.deterministicButton().isDisabled());
            assertTrue(f.weightedView().exportPdf.isDisabled());
            assertThrows(java.lang.reflect.InvocationTargetException.class, () -> invoke(f.dialog, "capturePdfReport", false));
            f.install();
            assertFalse(f.deterministicButton().isDisabled());
            assertFalse(f.weightedView().exportPdf.isDisabled());
            set(f.dialog, "exportBusy", true);
            invoke(f.dialog, "refreshExportActions");
            assertTrue(f.deterministicButton().isDisabled());
            assertTrue(f.weightedView().exportPdf.isDisabled());
            set(f.dialog, "exportBusy", false);
            invoke(f.dialog, "refreshExportActions");
            assertTrue(f.jobs.start(SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE, (p, c) -> "result", value -> {}, failure -> fail(failure)));
            assertTrue(f.deterministicButton().isDisabled());
            assertTrue(f.weightedView().exportPdf.isDisabled());
            f.jobs.cancel();
            assertTrue(f.deterministicButton().isDisabled());
            f.executor.run();
            assertFalse(f.deterministicButton().isDisabled());
            assertFalse(f.weightedView().exportPdf.isDisabled());
        });
    }

    @Test void staleOrIncompatibleResultsCannotBeExportedButDeterministicIgnoresLongevityEdits() throws Exception {
        fx(f -> {
            f.install();
            field(f.dialog, "primaryMortalityAdjustment", TextField.class).setText("1.10");
            assertTrue(f.weightedView().exportPdf.isDisabled());
            assertFalse(f.deterministicButton().isDisabled());
            assertThrows(java.lang.reflect.InvocationTargetException.class, () -> invoke(f.dialog, "capturePdfReport", true));
            field(f.dialog, "exhaustiveStale", Label.class).setText("Plan changed");
            assertTrue(f.deterministicButton().isDisabled());
            field(f.dialog, "exhaustiveStale", Label.class).setText("");
            set(f.dialog, "planRevision", 8L);
            invoke(f.dialog, "refreshExportActions");
            assertTrue(f.deterministicButton().isDisabled());
            assertTrue(f.weightedView().exportPdf.isDisabled());
        });
    }

    @Test void capturesActiveHeatMapSelectionMetricAndRankedOrderWithoutMutatingOrRerunning() throws Exception {
        fx(f -> {
            f.install();
            long generation = f.jobs.generation();
            var map = field(f.dialog, "deterministicHeatMap", ClaimingStrategyHeatMapView.class);
            field(f.dialog, "deterministicResultTabs", TabPane.class).getSelectionModel()
                    .select(field(f.dialog, "deterministicHeatMapTab", Tab.class));
            ClaimingStrategyHeatMapViewTest.button(map, 70, 70).fire();
            map.metric.setValue(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT);
            var cell = map.reportSelection();
            var navigation = field(f.dialog, "navigation", AnalyzerNavigationView.class);
            for (var choice : navigation.choices()) navigation.select(choice.mode());
            navigation.select(SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE);
            assertSame(cell, map.reportSelection());
            var source = field(f.dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class);
            var report = (IntegratedAnalyzerReport) invoke(f.dialog, "capturePdfReport", false);
            assertEquals(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT.toString(), report.heatMap().metric());
            assertTrue(report.selectedSummary().toString().contains("Primary 70 / Spouse 70"));
            assertEquals(81, report.rankedTables().getFirst().rows().size());
            new IntegratedAnalyzerPdfExporter().export(report, directory.resolve("selected.pdf"));
            assertSame(cell, map.reportSelection());
            assertSame(source, field(f.dialog, "exhaustivePresentation", ExhaustiveIntegratedSearchPresentation.class));
            assertEquals(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT, map.metric.getValue());
            assertEquals(generation, f.jobs.generation());
            assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, f.jobs.state());
            assertFalse(f.deterministicButton().isDisabled());

            var weighted = f.weightedView();
            weighted.table.getSelectionModel().selectLast();
            var entry = weighted.table.getSelectionModel().getSelectedItem();
            weighted.heatMap.metric.setValue(ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE);
            for (var choice : navigation.choices()) navigation.select(choice.mode());
            navigation.select(SocialSecurityAnalyzerJobController.Mode.WEIGHTED);
            assertSame(entry, weighted.table.getSelectionModel().getSelectedItem());
            report = (IntegratedAnalyzerReport) invoke(f.dialog, "capturePdfReport", true);
            assertTrue(report.selectedSummary().toString().contains("Rank: 2"));
            assertEquals(ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE.toString(), report.heatMap().metric());
            assertSame(entry, weighted.table.getSelectionModel().getSelectedItem());
            assertEquals(generation, f.jobs.generation());
            assertEquals("", weighted.stale.getText());
            assertThrows(java.io.IOException.class, () -> new IntegratedAnalyzerPdfExporter().export(
                    (IntegratedAnalyzerReport) invoke(f.dialog, "capturePdfReport", true), directory.resolve("absent/report.pdf")));
            assertFalse(weighted.exportPdf.isDisabled());
            assertSame(entry, weighted.table.getSelectionModel().getSelectedItem());
        });
    }

    @Test void viewPdfUsesFrozenHeatMapSelectionAndDoesNotRunAnalysisAgain() throws Exception {
        fx(f -> {
            f.install();
            var map = field(f.dialog, "deterministicHeatMap", ClaimingStrategyHeatMapView.class);
            field(f.dialog, "deterministicResultTabs", TabPane.class).getSelectionModel()
                    .select(field(f.dialog, "deterministicHeatMapTab", Tab.class));
            ClaimingStrategyHeatMapViewTest.button(map, 70, 70).fire();
            map.metric.setValue(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT);
            var expectedGeneration = f.jobs.generation();
            var opened = new java.util.concurrent.atomic.AtomicReference<Path>();
            var store = new com.daviddunn.retirementplanner.ui.pdf.PdfPreviewStore(directory.resolve("social-security-preview"));
            var workflow = new com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator<IntegratedAnalyzerReport>(
                    owner -> com.daviddunn.retirementplanner.ui.pdf.PdfExportChoice.VIEW,
                    (owner, name) -> fail("View must not open a Save chooser"), store,
                    path -> { assertTrue(Files.isRegularFile(path)); opened.set(path); }, Runnable::run, Runnable::run);
            set(f.dialog, "pdfWorkflow", workflow);
            invoke(f.dialog, "exportPdf", false);
            assertNotNull(opened.get());
            assertEquals(expectedGeneration, f.jobs.generation());
            assertEquals(SocialSecurityAnalyzerJobController.State.IDLE, f.jobs.state());
            assertEquals(ClaimingStrategyHeatMapMetric.DIFFERENCE_FROM_CURRENT, map.metric.getValue());
            assertTrue(Files.size(opened.get()) > 0);
            try (var document = org.apache.pdfbox.Loader.loadPDF(opened.get().toFile())) {
                String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
                assertTrue(text.contains("Primary 70 / Spouse 70"));
                assertTrue(text.contains("Difference from Current"));
            }
            var weighted = field(f.dialog, "weightedView", LongevityWeightedIntegratedView.class);
            field(weighted, "resultTabs", TabPane.class).getSelectionModel().select(field(weighted, "heatMapTab", Tab.class));
            ClaimingStrategyHeatMapViewTest.button(weighted.heatMap, 70, 70).fire();
            weighted.heatMap.metric.setValue(ClaimingStrategyHeatMapMetric.FUTURE_DOLLAR_ESTATE);
            invoke(f.dialog, "exportPdf", true);
            try (var document = org.apache.pdfbox.Loader.loadPDF(opened.get().toFile())) {
                String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
                assertTrue(text.contains("Longevity-Weighted"));
                assertTrue(text.contains("Valuation date"));
                assertTrue(text.contains("Primary Survivor Benefit Claiming Age"));
            }
            assertEquals(expectedGeneration, f.jobs.generation());
        });
    }

    @Test void cancelingIntegratedPdfDoesNotCreateAPreviewOrRunAnalysis() throws Exception {
        fx(f -> {
            f.install();
            var store = new com.daviddunn.retirementplanner.ui.pdf.PdfPreviewStore(directory.resolve("cancelled-social-security-preview"));
            var workflow = new com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator<IntegratedAnalyzerReport>(
                    owner -> com.daviddunn.retirementplanner.ui.pdf.PdfExportChoice.CANCEL,
                    (owner, name) -> fail("Cancel must not open a Save chooser"), store,
                    path -> fail("Cancel must not open a viewer"), Runnable::run, Runnable::run);
            set(f.dialog, "pdfWorkflow", workflow);
            long generation = f.jobs.generation();
            invoke(f.dialog, "exportPdf", true);
            assertFalse(Files.exists(store.directory()));
            assertFalse(workflow.isBusy());
            assertEquals(generation, f.jobs.generation());
        });
    }

    @Test void socialSecurityOnlyAndQuickActionsExportTheirFrozenReportWithoutAnalysisRuns() throws Exception {
        SharedClaimingAgeGridTest.prepare();
        fx(f -> {
            f.install();
            var now = java.time.Instant.parse("2026-11-03T12:00:00Z");
            var ssSession = new SocialSecurityReportSession(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                    "Historical household", true, CoupleSocialSecurityPersistenceTest.fixture(true, false),
                    SharedClaimingAgeGridTest.presentation, now.minusSeconds(10), now);
            set(f.dialog, "socialSecurityReport", ssSession);
            set(f.dialog, "socialSecurityReportBinding", invoke(f.dialog, "reportBinding"));
            set(f.dialog, "socialSecurityResultCurrent", true);
            var selected = SharedClaimingAgeGridTest.presentation.rankedStrategies().stream().limit(2).toList();
            var comparison = SocialSecurityAndQuickPdfTest.comparison(selected);
            var prepared = new QuickReportSession.Prepared(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                    IntegratedAnalyzerPdfReportTest.context(false), "a".repeat(64), java.util.UUID.randomUUID(), selected.size(),
                    selected.stream().map(row -> new QuickReportSession.Candidate(row.rank(), row.cell().strategy(), row.cell().expectedValue())).toList(),
                    List.of());
            set(f.dialog, "quickReport", QuickReportSession.accepted(prepared, comparison, now));
            set(f.dialog, "quickReportBinding", invoke(f.dialog, "reportBinding"));
            invoke(f.dialog, "refreshExportActions");
            assertTrue((boolean) invoke(f.dialog, "canExportAdditionalPdf", true));
            assertTrue((boolean) invoke(f.dialog, "canExportAdditionalPdf", false));

            Path ssPath = directory.resolve("ss-only-workflow.pdf");
            set(f.dialog, "pdfWorkflow", saveWorkflow(ssPath));
            long generation = f.jobs.generation();
            invoke(f.dialog, "exportAdditionalPdf", true);
            assertTrue(Files.isRegularFile(ssPath));
            try (var pdf = org.apache.pdfbox.Loader.loadPDF(ssPath.toFile())) {
                assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains("Social Security Only"));
            }

            Path quickPath = directory.resolve("quick-workflow.pdf");
            set(f.dialog, "pdfWorkflow", saveWorkflow(quickPath));
            invoke(f.dialog, "exportAdditionalPdf", false);
            assertTrue(Files.isRegularFile(quickPath));
            try (var pdf = org.apache.pdfbox.Loader.loadPDF(quickPath.toFile())) {
                String text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
                assertTrue(text.contains("Quick Comparison"));
            }
            assertEquals(generation, f.jobs.generation());
        });
    }

    private com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator<IntegratedAnalyzerReport>
            saveWorkflow(Path destination) {
        return new com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator<>(
                owner -> com.daviddunn.retirementplanner.ui.pdf.PdfExportChoice.SAVE,
                (owner, name) -> java.util.Optional.of(destination),
                new com.daviddunn.retirementplanner.ui.pdf.PdfPreviewStore(directory.resolve("ss-save-preview")),
                path -> fail("Save must not invoke a viewer"), Runnable::run, Runnable::run);
    }

    @Test void sharedPaletteMatchesActualFxCellBackground() throws Exception {
        fx(f -> {
            f.install();
            var map = field(f.dialog, "deterministicHeatMap", ClaimingStrategyHeatMapView.class);
            var button = ClaimingStrategyHeatMapViewTest.button(map, 62, 62);
            var stage = new Stage();
            // The existing dialog owns the map, so apply CSS through its scene.
            var root = field(f.dialog, "stage", Stage.class).getScene().getRoot();
            ((AnalyzerNavigationView) root).select(SocialSecurityAnalyzerJobController.Mode.EXHAUSTIVE);
            field(f.dialog, "deterministicResultTabs", TabPane.class).getSelectionModel().selectLast();
            root.applyCss();
            var color = (javafx.scene.paint.Color) button.getBackground().getFills().getFirst().getFill();
            assertEquals(javafx.scene.paint.Color.web(com.daviddunn.retirementplanner.util.ClaimingHeatMapPalette.ESSENTIALLY_OPTIMAL.color), color);
            stage.close();
        });
    }

    private static final class Fixture implements AutoCloseable {
        final SocialSecurityAnalyzerJobControllerTest.ManualExecutor executor = new SocialSecurityAnalyzerJobControllerTest.ManualExecutor();
        final SocialSecurityAnalysisJobCoordinator coordinator = new SocialSecurityAnalysisJobCoordinator(executor);
        final SocialSecurityAnalyzerJobController jobs = new SocialSecurityAnalyzerJobController(coordinator, Runnable::run);
        final SocialSecurityStrategyAnalyzerDialog dialog = new SocialSecurityStrategyAnalyzerDialog(null,
                LongevityWeightedAnalysisRequestFactoryTest.plan(), jobs);

        void install() throws Exception {
            set(dialog, "planRevision", 7L);
            set(dialog, "assumptionsRevision", 9L);
            set(dialog, "exhaustiveRevision", 7L);
            var deterministic = IntegratedAnalyzerPdfReportTest.deterministic();
            set(dialog, "exhaustivePresentation", deterministic);
            set(dialog, "deterministicReportContext", IntegratedAnalyzerPdfReportTest.context(false));
            invoke(dialog, "renderExhaustive", deterministic);
            var weighted = IntegratedAnalyzerPdfReportTest.weighted();
            set(dialog, "weightedPresentation", weighted);
            set(dialog, "weightedReportContext", IntegratedAnalyzerPdfReportTest.context(true));
            set(dialog, "weightedCurrent", true);
            weightedView().render(weighted, "Current elections");
            invoke(dialog, "refreshExportActions");
        }
        Button deterministicButton() throws Exception { return field(dialog, "deterministicExportPdf", Button.class); }
        LongevityWeightedIntegratedView weightedView() throws Exception { return field(dialog, "weightedView", LongevityWeightedIntegratedView.class); }
        @Override public void close() throws Exception {
            invoke(dialog, "close");
            field(dialog, "stage", Stage.class).close();
            coordinator.close();
        }
    }
    private static Object invoke(Object target, String name, Object... arguments) throws Exception {
        var types = Arrays.stream(arguments).map(value -> value instanceof Boolean ? boolean.class : value.getClass()).toArray(Class<?>[]::new);
        var method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }
    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(target));
    }
    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void fx(Check check) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { try (var fixture = new Fixture()) { check.run(fixture); } return null; });
        Platform.runLater(task); task.get(30, TimeUnit.SECONDS);
    }
    interface Check { void run(Fixture fixture) throws Exception; }
}
