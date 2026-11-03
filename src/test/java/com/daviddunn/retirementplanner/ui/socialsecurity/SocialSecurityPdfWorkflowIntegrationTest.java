package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerPdfExporter;
import com.daviddunn.retirementplanner.app.export.IntegratedAnalyzerReport;
import com.daviddunn.retirementplanner.ui.pdf.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SocialSecurityPdfWorkflowIntegrationTest {
    @TempDir Path directory;

    @BeforeAll static void startFx() throws Exception { ClaimingStrategyHeatMapViewTest.startFx(); }

    @Test void historicalPdfAvailabilityRequiresAnImmutableSupportedReport() {
        var legacyOnly = new ProjectionAnalyzerHistoryDialog.Content(() -> new StackPane(), path -> () -> { });
        assertFalse(ProjectionAnalyzerHistoryDialog.hasFrozenPdfReport(legacyOnly));

        var socialSecurity = new ProjectionAnalyzerHistoryDialog.Content(() -> new StackPane(), path -> () -> { },
                () -> IntegratedAnalyzerPdfReportTest.deterministicReport());
        assertTrue(ProjectionAnalyzerHistoryDialog.hasFrozenPdfReport(socialSecurity));

    }

    @Test void singlePersonActionViewCapturesOnceRendersOffFxAndOpensOnlyCompletedPreview() throws Exception {
        var report = IntegratedAnalyzerPdfReportTest.deterministicReport();
        var captureCount = new AtomicInteger();
        var captureOnFx = new java.util.concurrent.atomic.AtomicBoolean();
        var opened = new AtomicReference<Path>();
        var store = new PdfPreviewStore(directory.resolve("single-view"));
        var workflow = new PdfExportWorkflowCoordinator<IntegratedAnalyzerReport>(
                owner -> PdfExportChoice.VIEW,
                (owner, name) -> fail("View must not show a Save chooser"), store,
                path -> { assertTrue(Files.isRegularFile(path)); opened.set(path); },
                command -> Thread.ofVirtual().start(command), Platform::runLater);
        var action = new IndividualPdfExportAction("test-individual-pdf", "social-security-only.pdf",
                () -> true, () -> { captureCount.incrementAndGet(); captureOnFx.set(Platform.isFxApplicationThread()); return report; }, workflow);
        var stage = onFx(() -> {
            var window = new Stage(); window.setScene(new Scene(new StackPane(action.button()), 480, 220)); window.show();
            action.button().fire();
            return window;
        });
        assertTrue(waitFor(() -> opened.get() != null, 30));
        assertTrue(waitFor(() -> !workflow.isBusy(), 30));
        assertTrue(Files.isRegularFile(opened.get()));
        assertEquals(1, captureCount.get());
        assertTrue(captureOnFx.get());
        try (var previews = Files.list(store.directory())) {
            assertEquals(1, previews.filter(path -> path.toString().endsWith(".pdf")).count());
        }
        onFx(() -> { stage.close(); return null; });
    }

    @Test void saveUsesSameFrozenReportAndCancelDoesNotCaptureOrCreatePreview() throws Exception {
        var report = IntegratedAnalyzerPdfReportTest.deterministicReport();
        var captureCount = new AtomicInteger();
        var saved = directory.resolve("social-security.pdf");
        var store = new PdfPreviewStore(directory.resolve("single-save"));
        var workflow = new PdfExportWorkflowCoordinator<IntegratedAnalyzerReport>(
                owner -> PdfExportChoice.SAVE, (owner, name) -> Optional.of(saved), store,
                path -> fail("Save must not invoke a viewer"), command -> Thread.ofVirtual().start(command), Platform::runLater);
        var action = new IndividualPdfExportAction("test-individual-save", "social-security-only.pdf",
                () -> true, () -> { captureCount.incrementAndGet(); return report; }, workflow);
        var stage = onFx(() -> {
            var window = new Stage(); window.setScene(new Scene(new StackPane(action.button()), 480, 220)); window.show();
            action.button().fire(); return window;
        });
        assertTrue(waitFor(() -> Files.exists(saved), 30));
        assertEquals(1, captureCount.get());
        assertFalse(Files.exists(store.directory()));
        assertTrue(Files.size(saved) > 0);
        onFx(() -> { stage.close(); return null; });
        assertTrue(waitFor(() -> !workflow.isBusy(), 30));

        var cancelledStore = new PdfPreviewStore(directory.resolve("single-cancel"));
        var cancelledCapture = new AtomicInteger();
        var cancelWorkflow = new PdfExportWorkflowCoordinator<IntegratedAnalyzerReport>(owner -> PdfExportChoice.CANCEL,
                (owner, name) -> fail("Cancel must not show a Save chooser"), cancelledStore,
                path -> fail("Cancel must not open a viewer"), Runnable::run, Runnable::run);
        var cancelAction = new IndividualPdfExportAction("test-individual-cancel", "social-security-only.pdf",
                () -> true, () -> { cancelledCapture.incrementAndGet(); return report; }, cancelWorkflow);
        onFx(() -> {
            var window = new Stage(); window.setScene(new Scene(new StackPane(cancelAction.button()), 480, 220)); window.show();
            cancelAction.button().fire(); window.close(); return null;
        });
        assertEquals(0, cancelledCapture.get());
        assertFalse(Files.exists(cancelledStore.directory()));
    }

    private static boolean waitFor(java.util.function.BooleanSupplier condition, int seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        return condition.getAsBoolean();
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        var task = new java.util.concurrent.FutureTask<>(action);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }
}
