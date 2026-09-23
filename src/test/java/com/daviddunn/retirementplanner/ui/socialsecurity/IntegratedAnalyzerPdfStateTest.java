package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.socialsecurity.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    @Test void sharedPaletteMatchesActualFxCellBackground() throws Exception {
        fx(f -> {
            f.install();
            var map = field(f.dialog, "deterministicHeatMap", ClaimingStrategyHeatMapView.class);
            var button = ClaimingStrategyHeatMapViewTest.button(map, 62, 62);
            var stage = new Stage();
            // The existing dialog owns the map, so apply CSS through its scene.
            var root = field(f.dialog, "stage", Stage.class).getScene().getRoot();
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
