package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.pdf.*;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloPdfWorkflowIntegrationTest {
    @TempDir Path directory;

    @BeforeAll static void startFx() throws Exception { MonteCarloViewTest.startFx(); }

    @Test void viewAndSaveRenderTheOneAcceptedReportWithoutReanalysis() throws Exception {
        var run = MonteCarloUiFixtures.run("1200000");
        MonteCarloPdfReport report = MonteCarloViewTest.fx(() -> MonteCarloPdfReportAdapter.from(run));
        verifyChoice(report, PdfExportChoice.VIEW);
        verifyChoice(report, PdfExportChoice.SAVE);
        var comparison = MonteCarloComparisonFixtures.run();
        var pairedReport = MonteCarloViewTest.fx(() -> MonteCarloPdfReportAdapter.from(comparison));
        verifyChoice(pairedReport, PdfExportChoice.VIEW);
    }

    @Test void cancelDoesNotCaptureChooseSaveOrCreatePreview() throws Exception {
        var report = MonteCarloViewTest.fx(() -> MonteCarloPdfReportAdapter.from(MonteCarloUiFixtures.run("1200000")));
        var store = new PdfPreviewStore(directory.resolve("cancel-preview"));
        var capture = new AtomicInteger();
        var chooser = new AtomicInteger();
        var workflow = new PdfExportWorkflowCoordinator<MonteCarloPdfReport>(owner -> PdfExportChoice.CANCEL,
                (owner, filename) -> { chooser.incrementAndGet(); return Optional.empty(); }, store,
                path -> fail("Cancel must not invoke a viewer"), Runnable::run, Runnable::run);
        var identity = new Object();
        var action = action("mc-cancel-test", report, identity, capture, workflow);
        Stage stage = showAndFire(action);
        assertEquals(0, capture.get());
        assertEquals(0, chooser.get());
        assertFalse(Files.exists(store.directory()));
        MonteCarloViewTest.fx(() -> { stage.close(); return null; });
    }

    @Test void enabledStandaloneViewAndSaveKeepOriginalExpenseDisclosures() throws Exception {
        var plan = MonteCarloUiFixtures.plan("1200000", 3);
        StandaloneExpenseHistoryTest.volatility(plan, ".25");
        var run = new MonteCarloRunService().run(plan,
                com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings.forPlan(plan, 2, 417, new java.math.BigDecimal(".12")),
                null, com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener.none(),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none());
        var report = MonteCarloViewTest.fx(() -> MonteCarloPdfReportAdapter.from(run));
        assertTrue(String.join("\n", report.context()).contains("expense 1 = 25%"));
        verifyChoice(report, PdfExportChoice.VIEW);
        verifyChoice(report, PdfExportChoice.SAVE);
    }

    @Test void enabledComparisonViewAndSaveKeepBothCandidateDisclosures() throws Exception {
        var plan = MonteCarloComparisonFixtures.plan();
        StandaloneExpenseHistoryTest.volatility(plan, ".25");
        var b = new com.daviddunn.retirementplanner.app.socialsecurity.RetirementPlanScenarioCopyService().copy(plan);
        b.setBaseline(null); StandaloneExpenseHistoryTest.volatility(b, ".17");
        plan.setBaseline(com.daviddunn.retirementplanner.domain.baseline.ProjectionBaselineFactory.create(b, "Independent original"));
        var prepared = MonteCarloStrategyComparisonRunService.capture(plan,
                com.daviddunn.retirementplanner.app.montecarlo.MonteCarloSettings.forPlan(plan, 2, 417, new java.math.BigDecimal(".12")),
                MonteCarloMode.FIXED_LIFESPAN, "1", "1", "67", "67");
        var run = new MonteCarloStrategyComparisonRunService().run(prepared,
                com.daviddunn.retirementplanner.domain.analysis.AnalysisProgressListener.none(),
                com.daviddunn.retirementplanner.domain.analysis.AnalysisCancellationToken.none());
        var report = MonteCarloViewTest.fx(() -> MonteCarloPdfReportAdapter.from(run));
        assertTrue(PairedExpenseHistoryTest.assumptionText(report).contains("expense 1 = 25%"));
        assertTrue(PairedExpenseHistoryTest.assumptionText(report).contains("expense 1 = 17%"));
        verifyChoice(report, PdfExportChoice.VIEW); verifyChoice(report, PdfExportChoice.SAVE);
    }

    @Test void duplicateActivationIsRejectedAndStaleSelectionCannotChangeCapturedPdf() throws Exception {
        var run = MonteCarloUiFixtures.run("1300000");
        MonteCarloPdfReport report = MonteCarloViewTest.fx(() -> MonteCarloPdfReportAdapter.from(run));
        var identity = new AtomicReference<Object>(new Object());
        var accepted = identity.get();
        var capture = new AtomicInteger();
        var prompts = new AtomicInteger();
        var queued = new AtomicReference<Runnable>();
        var path = directory.resolve("accepted-report.pdf");
        var workflow = new PdfExportWorkflowCoordinator<MonteCarloPdfReport>(owner -> {
            prompts.incrementAndGet(); return PdfExportChoice.SAVE;
        }, (owner, name) -> { assertEquals("monte-carlo-fixed.pdf", name); return Optional.of(path); },
                new PdfPreviewStore(directory.resolve("duplicate-preview")),
                ignored -> fail("Save must not open a viewer"), queued::set, Platform::runLater);
        var action = new MonteCarloPdfExportAction("mc-duplicate-test", () -> true,
                () -> { capture.incrementAndGet(); return report; }, identity::get,
                value -> identity.get() == value, ignored -> "monte-carlo-fixed.pdf", workflow);
        Stage stage = showAndFire(action);
        assertTrue(workflow.isBusy());
        assertTrue(action.button().isDisabled());
        Path previews = Path.of("target", "phase17b4-monte-carlo-pdf-choice");
        Files.createDirectories(previews);
        MonteCarloViewTest.fx(() -> {
            var themes = new ApplicationThemeManager(ApplicationTheme.LIGHT);
            for (var theme : ApplicationTheme.values()) {
                themes.apply(theme);
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                var image = stage.getScene().getRoot().snapshot(null, null);
                ImageIO.write(toBuffered(image), "png", previews.resolve("standalone-busy-"
                        + theme.name().toLowerCase() + ".png").toFile());
            }
            themes.close();
            return null;
        });
        MonteCarloViewTest.fx(() -> { action.button().fire(); return null; });
        assertEquals(1, prompts.get());
        assertEquals(1, capture.get());
        identity.set(new Object()); // A later selection cannot replace the captured report.
        queued.get().run();
        assertTrue(waitFor(() -> !workflow.isBusy(), 30));
        assertTrue(Files.isRegularFile(path));
        assertTrue(MonteCarloPdfExportTest.text(path).contains("Seed: 417"));
        assertFalse(Files.exists(directory.resolve("duplicate-preview")));
        MonteCarloViewTest.fx(() -> { stage.close(); return null; });
    }

    private void verifyChoice(MonteCarloPdfReport report, PdfExportChoice choice) throws Exception {
        var captureCount = new AtomicInteger();
        var current = new Object();
        String suffix = report.title().toLowerCase().replace(' ', '-');
        var store = new PdfPreviewStore(directory.resolve("preview-" + choice + "-" + suffix));
        var opened = new AtomicReference<Path>();
        var saved = directory.resolve("saved-" + choice + "-" + suffix + ".pdf");
        var chooserCount = new AtomicInteger();
        var workflow = new PdfExportWorkflowCoordinator<MonteCarloPdfReport>(owner -> choice,
                (owner, filename) -> {
                    chooserCount.incrementAndGet();
                    assertEquals(report.filename(), filename);
                    return Optional.of(saved);
                }, store,
                path -> { assertTrue(Files.isRegularFile(path)); assertTrue(Files.size(path) > 0); opened.set(path); },
                command -> Thread.ofVirtual().start(command), Platform::runLater);
        var action = new MonteCarloPdfExportAction("mc-" + choice.name().toLowerCase(), () -> true,
                () -> { captureCount.incrementAndGet(); return report; }, () -> current, identity -> false,
                ignored -> report.filename(), workflow);
        Stage stage = showAndFire(action);
        assertTrue(waitFor(() -> !workflow.isBusy(), 30));
        assertEquals(1, captureCount.get());
        if (choice == PdfExportChoice.VIEW) {
            assertEquals(0, chooserCount.get());
            assertNotNull(opened.get());
            assertTrue(Files.isRegularFile(opened.get()));
            try (var previews = Files.list(store.directory())) {
                assertEquals(1, previews.filter(file -> file.toString().endsWith(".pdf")).count());
            }
            assertFalse(Files.exists(saved));
            var generatedText = MonteCarloPdfExportTest.text(opened.get());
            assertTrue(generatedText.contains("Seed: 417"));
            if (report.title().contains("Strategy Comparison")) {
                assertTrue(generatedText.contains("Strategy A"));
                assertTrue(generatedText.contains("Strategy B"));
            }
        } else {
            assertEquals(1, chooserCount.get());
            assertTrue(Files.isRegularFile(saved));
            var generatedText = MonteCarloPdfExportTest.text(saved);
            assertTrue(generatedText.contains("Seed: 417"));
            if (report.title().contains("Strategy Comparison")) {
                assertTrue(generatedText.contains("Strategy A"));
                assertTrue(generatedText.contains("Strategy B"));
            }
            assertFalse(Files.exists(store.directory()));
        }
        if (PairedExpenseHistoryTest.assumptionText(report).contains("paired execution version")) {
            String text = MonteCarloPdfExportTest.text(choice == PdfExportChoice.VIEW ? opened.get() : saved).replaceAll("\\s+", " ");
            assertTrue(text.contains("expense 1 = 25%")); assertTrue(text.contains("expense 1 = 17%"));
            assertTrue(text.contains("intentionally unmatched"));
        }
        MonteCarloViewTest.fx(() -> { stage.close(); return null; });
    }

    private static MonteCarloPdfExportAction action(String id, MonteCarloPdfReport report, Object identity,
            AtomicInteger capture, PdfExportWorkflowCoordinator<MonteCarloPdfReport> workflow) {
        return new MonteCarloPdfExportAction(id, () -> true, () -> { capture.incrementAndGet(); return report; },
                () -> identity, ignored -> false, ignored -> report.filename(), workflow);
    }

    private static Stage showAndFire(MonteCarloPdfExportAction action) throws Exception {
        return MonteCarloViewTest.fx(() -> {
            var stage = new Stage(); stage.setScene(new Scene(new StackPane(action.button()), 500, 250)); stage.show();
            action.button().fire(); return stage;
        });
    }

    private static boolean waitFor(java.util.function.BooleanSupplier condition, int seconds) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        return condition.getAsBoolean();
    }

    private static java.awt.image.BufferedImage toBuffered(javafx.scene.image.WritableImage image) {
        var result = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < result.getHeight(); y++) for (int x = 0; x < result.getWidth(); x++)
            result.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        return result;
    }
}
