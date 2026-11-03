package com.daviddunn.retirementplanner.ui.pdf;

import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PdfExportChoiceDialogPreviewTest {
    private static volatile boolean fxStarted;

    @Test void rendersTheChoiceDialogInBothThemesAndSupportsDefaultAndEscapeBehavior() throws Exception {
        startFx();
        onFx(() -> {
            Stage owner = new Stage(); owner.setScene(new Scene(new StackPane(), 560, 320)); owner.show();
            var themes = new ApplicationThemeManager(ApplicationTheme.LIGHT);
            Path output = Path.of("target", "phase17b1-pdf-choice"); Files.createDirectories(output);
            for (var theme : ApplicationTheme.values()) {
                themes.apply(theme);
                var dialog = PdfExportChoiceDialog.create(owner);
                dialog.show();
                dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                var view = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(0));
                assertTrue(view.isDefaultButton());
                assertEquals("View PDF in the default PDF viewer", view.getAccessibleText());
                var image = dialog.getDialogPane().snapshot(null, null);
                ImageIO.write(toBuffered(image), "png",
                        output.resolve("choice-" + theme.name().toLowerCase() + ".png").toFile());
                dialog.close();
            }
            var escape = PdfExportChoiceDialog.create(owner);
            escape.show();
            escape.getDialogPane().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
            assertTrue(escape.getResult() == null || escape.getResult() == PdfExportChoice.CANCEL);
            escape.close();
            themes.close(); owner.close();
            return null;
        });
        assertTrue(Files.size(Path.of("target", "phase17b1-pdf-choice", "choice-light.png")) > 0);
        assertTrue(Files.size(Path.of("target", "phase17b1-pdf-choice", "choice-dark.png")) > 0);
    }

    @Test void rendersProjectionAndBreakEvenExportChoiceOwnersInBothThemes() throws Exception {
        startFx();
        onFx(() -> {
            Path output = Path.of("target", "phase17b2-pdf-export-choice"); Files.createDirectories(output);
            var themes = new ApplicationThemeManager(ApplicationTheme.LIGHT);
            for (String ownerTitle : java.util.List.of("Projection Export", "Break-Even Export")) {
                Stage owner = new Stage(); owner.setTitle(ownerTitle); owner.setScene(new Scene(new StackPane(), 600, 360)); owner.show();
                String prefix = ownerTitle.startsWith("Projection") ? "projection" : "break-even";
                for (var theme : ApplicationTheme.values()) {
                    themes.apply(theme);
                    var dialog = PdfExportChoiceDialog.create(owner);
                    dialog.show(); dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                    var view = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(
                            dialog.getDialogPane().getButtonTypes().get(0));
                    var save = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(
                            dialog.getDialogPane().getButtonTypes().get(1));
                    var cancel = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(
                            dialog.getDialogPane().getButtonTypes().get(2));
                    assertTrue(view.isDefaultButton());
                    var image = dialog.getDialogPane().snapshot(null, null);
                    ImageIO.write(toBuffered(image), "png", output.resolve(prefix + "-"
                            + theme.name().toLowerCase() + ".png").toFile());
                    save.requestFocus(); dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                    ImageIO.write(toBuffered(dialog.getDialogPane().snapshot(null, null)), "png",
                            output.resolve(prefix + "-save-focused-" + theme.name().toLowerCase() + ".png").toFile());
                    cancel.requestFocus(); dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                    ImageIO.write(toBuffered(dialog.getDialogPane().snapshot(null, null)), "png",
                            output.resolve(prefix + "-cancel-focused-" + theme.name().toLowerCase() + ".png").toFile());
                    dialog.close(); owner.close();
                    if (theme != ApplicationTheme.values()[ApplicationTheme.values().length - 1]) {
                        owner = new Stage(); owner.setTitle(ownerTitle); owner.setScene(new Scene(new StackPane(), 600, 360)); owner.show();
                    }
                }
            }
            themes.close();
            return null;
        });
        Path output = Path.of("target", "phase17b2-pdf-export-choice");
        for (String name : java.util.List.of("projection-light.png", "projection-dark.png",
                "break-even-light.png", "break-even-dark.png", "projection-save-focused-light.png",
                "projection-save-focused-dark.png", "break-even-save-focused-light.png",
                "break-even-save-focused-dark.png", "projection-cancel-focused-light.png",
                "projection-cancel-focused-dark.png", "break-even-cancel-focused-light.png",
                "break-even-cancel-focused-dark.png")) assertTrue(Files.size(output.resolve(name)) > 0);
    }

    @Test void rendersRetainedViewerFailureInBothThemes() throws Exception {
        startFx();
        onFx(() -> {
            Path output = Path.of("target", "phase17b2-pdf-export-choice"); Files.createDirectories(output);
            var themes = new ApplicationThemeManager(ApplicationTheme.LIGHT);
            for (var theme : ApplicationTheme.values()) {
                themes.apply(theme);
                PdfExportDialogs.showFailure(null, new PdfExportWorkflowCoordinator.PdfExportFailure(
                        new java.io.IOException("No default PDF viewer is available."), Path.of("C:/managed/preview.pdf")));
                var window = javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                        .reduce((first, second) -> second).orElseThrow();
                var pane = (javafx.scene.Parent) window.getScene().getRoot();
                pane.applyCss(); pane.layout();
                var image = pane.snapshot(null, null);
                ImageIO.write(toBuffered(image), "png", output.resolve("viewer-error-"
                        + theme.name().toLowerCase() + ".png").toFile());
                window.hide();
            }
            themes.close();
            return null;
        });
        Path output = Path.of("target", "phase17b2-pdf-export-choice");
        assertTrue(Files.size(output.resolve("viewer-error-light.png")) > 0);
        assertTrue(Files.size(output.resolve("viewer-error-dark.png")) > 0);
    }

    @Test void rendersSocialSecurityAnalyzerExportChoicesInBothThemes() throws Exception {
        startFx();
        onFx(() -> {
            Path output = Path.of("target", "phase17b3-social-security-pdf-choice"); Files.createDirectories(output);
            var themes = new ApplicationThemeManager(ApplicationTheme.LIGHT);
            for (String context : java.util.List.of("Deterministic Integrated", "Longevity-Weighted Integrated",
                    "Social Security Only", "Quick Comparison", "Single-Person Deterministic",
                    "Single-Person Social Security Only", "Single-Person Longevity-Weighted Integrated")) {
                for (var theme : ApplicationTheme.values()) {
                    themes.apply(theme);
                    Stage owner = new Stage();
                    owner.setTitle(context + " Analyzer");
                    owner.setScene(new Scene(new StackPane(), 620, 360));
                    owner.show();
                    var dialog = PdfExportChoiceDialog.create(owner);
                    dialog.show(); dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                    var view = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(
                            dialog.getDialogPane().getButtonTypes().get(0));
                    assertTrue(view.isDefaultButton());
                    assertEquals("View PDF in the default PDF viewer", view.getAccessibleText());
                    ImageIO.write(toBuffered(dialog.getDialogPane().snapshot(null, null)), "png",
                            output.resolve(fileName(context, theme)).toFile());
                    dialog.close(); owner.close();
                }
            }
            themes.close();
            return null;
        });
        Path output = Path.of("target", "phase17b3-social-security-pdf-choice");
        for (String context : java.util.List.of("Deterministic Integrated", "Longevity-Weighted Integrated",
                "Social Security Only", "Quick Comparison", "Single-Person Deterministic",
                "Single-Person Social Security Only", "Single-Person Longevity-Weighted Integrated"))
            for (var theme : ApplicationTheme.values())
            assertTrue(Files.size(output.resolve(fileName(context, theme))) > 0);
    }

    @Test void rendersMonteCarloExportChoicesAndBusyFailureSurfacesInBothThemes() throws Exception {
        startFx();
        onFx(() -> {
            Path output = Path.of("target", "phase17b4-monte-carlo-pdf-choice"); Files.createDirectories(output);
            var themes = new ApplicationThemeManager(ApplicationTheme.LIGHT);
            for (String ownerTitle : java.util.List.of("Monte Carlo Retirement Analysis", "Monte Carlo Strategy Comparison",
                    "Historical Monte Carlo Analysis")) {
                Stage owner = new Stage(); owner.setTitle(ownerTitle); owner.setScene(new Scene(new StackPane(), 620, 380)); owner.show();
                String prefix = ownerTitle.startsWith("Monte Carlo Retirement") ? "standalone"
                        : ownerTitle.startsWith("Monte Carlo Strategy") ? "comparison" : "historical";
                for (var theme : ApplicationTheme.values()) {
                    themes.apply(theme);
                    var dialog = PdfExportChoiceDialog.create(owner);
                    dialog.show(); dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                    var view = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(
                            dialog.getDialogPane().getButtonTypes().get(0));
                    var save = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(
                            dialog.getDialogPane().getButtonTypes().get(1));
                    var cancel = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(
                            dialog.getDialogPane().getButtonTypes().get(2));
                    assertTrue(view.isDefaultButton());
                    ImageIO.write(toBuffered(dialog.getDialogPane().snapshot(null, null)), "png",
                            output.resolve(prefix + "-" + theme.name().toLowerCase() + ".png").toFile());
                    save.requestFocus(); dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                    ImageIO.write(toBuffered(dialog.getDialogPane().snapshot(null, null)), "png",
                            output.resolve(prefix + "-save-focused-" + theme.name().toLowerCase() + ".png").toFile());
                    cancel.requestFocus(); dialog.getDialogPane().applyCss(); dialog.getDialogPane().layout();
                    ImageIO.write(toBuffered(dialog.getDialogPane().snapshot(null, null)), "png",
                            output.resolve(prefix + "-cancel-focused-" + theme.name().toLowerCase() + ".png").toFile());
                    dialog.close(); owner.close();
                    if (theme != ApplicationTheme.values()[ApplicationTheme.values().length - 1]) {
                        owner = new Stage(); owner.setTitle(ownerTitle); owner.setScene(new Scene(new StackPane(), 620, 380)); owner.show();
                    }
                }
            }
            for (var theme : ApplicationTheme.values()) {
                themes.apply(theme);
                PdfExportDialogs.showFailure(null, new PdfExportWorkflowCoordinator.PdfExportFailure(
                        new java.io.IOException("Simulated PDF renderer failure."), null));
                var window = javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                        .reduce((first, second) -> second).orElseThrow();
                var image = ((javafx.scene.Parent) window.getScene().getRoot()).snapshot(null, null);
                ImageIO.write(toBuffered(image), "png", output.resolve("render-failure-"
                        + theme.name().toLowerCase() + ".png").toFile());
                window.hide();
                PdfExportDialogs.showFailure(null, new PdfExportWorkflowCoordinator.PdfExportFailure(
                        new java.io.IOException("No default PDF viewer is available."), Path.of("C:/managed/preview.pdf")));
                window = javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                        .reduce((first, second) -> second).orElseThrow();
                image = ((javafx.scene.Parent) window.getScene().getRoot()).snapshot(null, null);
                ImageIO.write(toBuffered(image), "png", output.resolve("viewer-failure-"
                        + theme.name().toLowerCase() + ".png").toFile());
                window.hide();
            }
            themes.close();
            return null;
        });
        Path output = Path.of("target", "phase17b4-monte-carlo-pdf-choice");
        for (String name : java.util.List.of("standalone-light.png", "standalone-dark.png", "comparison-light.png",
                "comparison-dark.png", "historical-light.png", "historical-dark.png", "render-failure-light.png",
                "render-failure-dark.png", "viewer-failure-light.png", "viewer-failure-dark.png"))
            assertTrue(Files.size(output.resolve(name)) > 0);
    }

    private static String fileName(String context, ApplicationTheme theme) {
        String normalized = context.toLowerCase().replace(' ', '-');
        return normalized + "-" + theme.name().toLowerCase() + ".png";
    }

    private static synchronized void startFx() throws Exception {
        if (fxStarted) return;
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(ready::countDown); }
        assertTrue(ready.await(20, TimeUnit.SECONDS)); Platform.setImplicitExit(false); fxStarted = true;
    }

    private static <T> T onFx(java.util.concurrent.Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        var result = new AtomicReference<T>(); var failure = new AtomicReference<Throwable>(); var done = new CountDownLatch(1);
        Platform.runLater(() -> { try { result.set(action.call()); } catch (Throwable error) { failure.set(error); } finally { done.countDown(); } });
        assertTrue(done.await(30, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    private static java.awt.image.BufferedImage toBuffered(javafx.scene.image.WritableImage image) {
        int width = (int) image.getWidth(); int height = (int) image.getHeight();
        var output = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var reader = image.getPixelReader();
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            output.setRGB(x, y, reader.getArgb(x, y));
        return output;
    }
}
