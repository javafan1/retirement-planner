package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.app.export.BreakEvenPdfReport;
import com.daviddunn.retirementplanner.domain.breakeven.*;
import com.daviddunn.retirementplanner.ui.pdf.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BreakEvenPdfWorkflowIntegrationTest {
    @BeforeAll static void startFx() throws Exception { BreakEvenCompactLayoutTest.startFx(); }

    @Test void cancelDoesNotChooseDestinationOrRenderAndViewOpensOnlyCompletedManagedPreview() throws Exception {
        var result = result();
        Path root = Files.createTempDirectory("aledrix-break-even-workflow");
        var chooserCalls = new AtomicInteger();
        var opened = new AtomicReference<Path>();
        var failures = new AtomicReference<PdfExportWorkflowCoordinator.PdfExportFailure>();
        FutureTask<Void> task = new FutureTask<>(() -> {
            var canceled = coordinator(PdfExportChoice.CANCEL, chooserCalls, root.resolve("cancel"), opened, failures);
            var cancelDialog = new BreakEvenAnalysisDialog(result, BreakEvenContext.unavailable(),
                    new BreakEvenInsightService().prepare(result), canceled);
            cancelDialog.show();
            try {
                ((Button) cancelDialog.getDialogPane().lookup("#break-even-export-pdf")).fire();
                assertEquals(0, chooserCalls.get());
                assertFalse(Files.exists(root.resolve("cancel")));
                assertNull(reportField(cancelDialog));
                assertNull(failures.get());
            } finally { cancelDialog.close(); }

            var view = coordinator(PdfExportChoice.VIEW, chooserCalls, root.resolve("view"), opened, failures);
            var viewDialog = new BreakEvenAnalysisDialog(result, BreakEvenContext.unavailable(),
                    new BreakEvenInsightService().prepare(result), view);
            viewDialog.show();
            try {
                ((Button) viewDialog.getDialogPane().lookup("#break-even-export-pdf")).fire();
                assertEquals(0, chooserCalls.get(), "View must bypass the Save chooser");
                assertNotNull(opened.get());
                assertTrue(Files.isRegularFile(opened.get()));
                assertEquals("%PDF-", new String(Files.readAllBytes(opened.get()), 0, 5,
                        java.nio.charset.StandardCharsets.US_ASCII));
                try (var pdf = Loader.loadPDF(opened.get().toFile())) {
                    assertTrue(pdf.getNumberOfPages() > 0);
                }
                assertNull(failures.get());
            } finally { viewDialog.close(); }
            return null;
        });
        Platform.runLater(task);
        task.get(90, TimeUnit.SECONDS);
    }

    @Test void saveUsesTheSelectedDestinationAndExistingReportGenerator() throws Exception {
        var result = result();
        Path root = Files.createTempDirectory("aledrix-break-even-save");
        Path destination = root.resolve("Break-Even-Analysis.pdf");
        var chooserCalls = new AtomicInteger();
        var opened = new AtomicReference<Path>();
        var failures = new AtomicReference<PdfExportWorkflowCoordinator.PdfExportFailure>();
        FutureTask<Void> task = new FutureTask<>(() -> {
            var dialog = new BreakEvenAnalysisDialog(result, BreakEvenContext.unavailable(),
                    new BreakEvenInsightService().prepare(result),
                    coordinator(PdfExportChoice.SAVE, chooserCalls, destination, opened, failures));
            dialog.show();
            try {
                var originalReport = dialog.preparePdfReport();
                ((Button) dialog.getDialogPane().lookup("#break-even-export-pdf")).fire();
                assertEquals(1, chooserCalls.get());
                assertTrue(Files.isRegularFile(destination));
                assertNull(opened.get());
                assertNull(failures.get());
                assertSame(result, originalReport.analysis());
                try (var pdf = Loader.loadPDF(destination.toFile())) { assertTrue(pdf.getNumberOfPages() > 0); }
            } finally { dialog.close(); }
            return null;
        });
        Platform.runLater(task);
        task.get(90, TimeUnit.SECONDS);
    }

    private static PdfExportWorkflowCoordinator<BreakEvenPdfReport> coordinator(PdfExportChoice choice,
            AtomicInteger chooserCalls, Path destination, AtomicReference<Path> opened,
            AtomicReference<PdfExportWorkflowCoordinator.PdfExportFailure> failures) {
        return new PdfExportWorkflowCoordinator<>(owner -> choice,
                (owner, suggested) -> {
                    chooserCalls.incrementAndGet();
                    return Optional.of(destination);
                }, new PdfPreviewStore(destination.resolveSibling(destination.getFileName() + "-previews")),
                opened::set, Runnable::run, Runnable::run);
    }

    private static BreakEvenAnalysisResult result() {
        var baseline = BreakEvenCompactLayoutTest.snapshot(62);
        var current = BreakEvenCompactLayoutTest.snapshot(65);
        return new BreakEvenAnalyzer().analyze(baseline, current);
    }

    private static Object reportField(BreakEvenAnalysisDialog dialog) throws Exception {
        var field = BreakEvenAnalysisDialog.class.getDeclaredField("pdfReport");
        field.setAccessible(true);
        return field.get(dialog);
    }
}
