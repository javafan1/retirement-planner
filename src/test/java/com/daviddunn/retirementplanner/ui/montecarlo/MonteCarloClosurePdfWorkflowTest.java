package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.export.*;
import com.daviddunn.retirementplanner.app.montecarlo.MonteCarloClosureReportingTest;
import com.daviddunn.retirementplanner.application.settings.*;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import com.daviddunn.retirementplanner.ui.pdf.*;
import javafx.stage.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

import static com.daviddunn.retirementplanner.ui.montecarlo.MonteCarloViewTest.*;
import static org.junit.jupiter.api.Assertions.*;

class MonteCarloClosurePdfWorkflowTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception { startFx(); }

    @ParameterizedTest @CsvSource({"true,false,false", "true,true,false", "false,false,false", "false,true,false",
            "true,false,true", "true,true,true", "false,false,true", "false,true,true"})
    void allEightEnabledReportsUseViewSaveCancelAndConfiguredDirectory(boolean couple, boolean longevity, boolean paired) throws Exception {
        var snapshot = MonteCarloClosureReportingTest.capture(couple, longevity, paired, true);
        var report = fx(() -> MonteCarloClosureReportingTest.report(snapshot));
        var stage = fx(() -> { var owner = new Stage(); owner.setScene(new javafx.scene.Scene(new javafx.scene.layout.Pane(), 320, 200)); owner.show(); return owner; });
        try {
            String viewed = null;
            for (int action = 0; action < 4; action++) {
                // Cancel choice, View, canceled Save chooser, accepted Save.
                var choice = action == 0 ? PdfExportChoice.CANCEL : action == 1 ? PdfExportChoice.VIEW : PdfExportChoice.SAVE;
                boolean chooserCancelled = action == 2;
                var root = directory.resolve("action-" + action);
                var export = root.resolve("configured-export");
                var repository = new JsonApplicationSettingsRepository(root.resolve("settings.json"));
                try (var settings = new ApplicationSettingsService(repository)) {
                    settings.savePreferences(ApplicationTheme.LIGHT, null, export.toString(), false).toCompletableFuture().get(5, TimeUnit.SECONDS);
                    fx(() -> { PdfExportDirectoryContext.attach(stage, settings); return null; });
                    var captures = new AtomicInteger(); var renders = new AtomicInteger(); var choosers = new AtomicInteger();
                    var opened = new AtomicReference<Path>(); var failure = new AtomicReference<Throwable>();
                    var destination = export.resolve(report.filename());
                    var preview = new PdfPreviewStore(root.resolve("previews"));
                    var workflow = new PdfExportWorkflowCoordinator<MonteCarloPdfReport>(owner -> choice, (owner, name) -> {
                        choosers.incrementAndGet(); assertEquals(report.filename(), name);
                        var chooser = new FileChooser();
                        assertTrue(PdfExportDirectoryContext.prepare(chooser, owner));
                        assertEquals(export.toAbsolutePath().normalize().toFile(), chooser.getInitialDirectory());
                        return chooserCancelled ? Optional.empty() : Optional.of(destination);
                    }, preview, path -> {
                        try (var pdf = Loader.loadPDF(path.toFile())) { assertTrue(pdf.getNumberOfPages() > 0); }
                        opened.set(path);
                    }, work -> Thread.ofVirtual().start(work), javafx.application.Platform::runLater);
                    fx(() -> {
                        workflow.start(new PdfExportWorkflowCoordinator.Request<>(stage, report.filename(), () -> true,
                                () -> { captures.incrementAndGet(); return report; }, (frozen, path) -> {
                                    renders.incrementAndGet(); new MonteCarloPdfExporter().export(frozen, path);
                                }, () -> true, (selected, path) -> { }, error -> failure.set(new AssertionError(error)), busy -> { }));
                        return null;
                    });
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                    while (fx(workflow::isBusy) && System.nanoTime() < deadline) Thread.sleep(10);
                    assertFalse(fx(workflow::isBusy)); assertNull(failure.get());
                    boolean accepted = choice == PdfExportChoice.VIEW || choice == PdfExportChoice.SAVE && !chooserCancelled;
                    assertEquals(accepted ? 1 : 0, captures.get()); assertEquals(accepted ? 1 : 0, renders.get());
                    assertEquals(choice == PdfExportChoice.SAVE ? 1 : 0, choosers.get());
                    if (choice == PdfExportChoice.VIEW) {
                        assertFalse(Files.exists(export)); assertNotNull(opened.get());
                        try (var pdf = Loader.loadPDF(opened.get().toFile())) { viewed = new PDFTextStripper().getText(pdf); }
                        assertTrue(Files.isRegularFile(opened.get()));
                    } else {
                        assertNull(opened.get()); assertFalse(Files.exists(preview.directory()));
                        if (accepted) {
                            try (var pdf = Loader.loadPDF(destination.toFile())) { assertEquals(viewed, new PDFTextStripper().getText(pdf)); }
                        } else assertFalse(Files.exists(destination));
                        if (choice == PdfExportChoice.CANCEL) assertFalse(Files.exists(export));
                    }
                }
            }
        } finally { fx(() -> { stage.close(); return null; }); }
    }
}
