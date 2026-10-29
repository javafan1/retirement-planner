package com.daviddunn.retirementplanner.ui.startup;

import com.daviddunn.retirementplanner.application.branding.*;
import com.daviddunn.retirementplanner.application.settings.ApplicationTheme;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.stage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.Optional;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AboutApplicationDialogTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException initialized) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }

    static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    @ParameterizedTest @EnumSource(ApplicationTheme.class)
    void actualSplashAndAboutShareMetadataAndRenderAtSupportedSizes(ApplicationTheme theme) throws Exception {
        fx(() -> {
            try (var manager = ApplicationThemeManager.start(theme)) {
                var branding = ApplicationBranding.defaults();
                var version = ApplicationVersion.current();
                for (int width : new int[] { 1180, 900 }) {
                    var owner = new Stage();
                    owner.setScene(new Scene(new javafx.scene.layout.StackPane(), width, width == 900 ? 650 : 820));
                    owner.show();
                    var dialog = new AboutApplicationDialog(owner, branding);
                    var splashStage = new Stage();
                    try {
                        dialog.show();
                        var pane = dialog.getDialogPane();
                        pane.applyCss(); pane.layout();
                        assertSame(owner, dialog.getOwner());
                        assertEquals(Modality.WINDOW_MODAL, dialog.getModality());
                        assertEquals("About Aledrix Retirement Planning", dialog.getTitle());
                        assertEquals(version.displaySummary(), ((Label) pane.lookup("#about-version")).getText());
                        assertEquals("Git commit: " + version.commitHash(), ((Label) pane.lookup("#about-commit")).getText());
                        assertEquals(ApplicationBranding.COMPANY_ATTRIBUTION, ((Label) pane.lookup("#about-attribution")).getText());
                        assertNotNull(pane.lookup("#about-logo"));
                        assertTrue(((Button) pane.lookupButton(ButtonType.CLOSE)).isCancelButton());
                        var splash = new StartupSplashView(branding, BrandingImages.load(branding.logoResource()));
                        var scene = new Scene(splash);
                        ApplicationThemeManager.prepare(scene);
                        splashStage.setScene(scene); splashStage.show(); splash.applyCss(); splash.layout();
                        assertEquals(((Label) pane.lookup("#about-version")).getText(),
                                ((Label) splash.lookup("#branding-version")).getText());
                        assertEquals(ApplicationBranding.COMPANY_ATTRIBUTION, ((Label) splash.lookup("#branding-attribution")).getText());
                        for (var node : splash.getChildren()) {
                            assertTrue(node.getBoundsInParent().getMaxY() <= scene.getHeight(), node.getId());
                        }
                        var attribution = (Label) pane.lookup("#about-attribution");
                        var beforeColor = attribution.getTextFill();
                        save(pane.snapshot(null, null), "about-" + theme + "-" + width + ".png");
                        save(scene.snapshot(null), "splash-" + theme + "-" + width + ".png");
                        manager.apply(theme == ApplicationTheme.LIGHT ? ApplicationTheme.DARK : ApplicationTheme.LIGHT);
                        pane.applyCss();
                        assertNotEquals(beforeColor, attribution.getTextFill());
                        assertEquals(version.displaySummary(), ((Label) pane.lookup("#about-version")).getText());
                        manager.apply(theme);
                        ((Button) pane.lookupButton(ButtonType.CLOSE)).fire();
                        assertFalse(dialog.isShowing());
                    } finally {
                        dialog.close(); splashStage.hide(); owner.hide();
                    }
                }
            }
            return null;
        });
    }

    @Test void escapeWindowCloseAndMissingLogoWorkWithUnknownBuild() throws Exception {
        fx(() -> {
            var owner = new Stage(); owner.setScene(new Scene(new javafx.scene.layout.StackPane(), 900, 650)); owner.show();
            try (var manager = ApplicationThemeManager.start(ApplicationTheme.LIGHT)) {
                var branding = new ApplicationBranding("Title", "Test Edition", "Tagline", "images/branding/missing.png");
                var dialog = new AboutApplicationDialog(owner, branding, ApplicationVersion.read(null));
                dialog.show();
                assertNull(dialog.getDialogPane().lookup("#about-logo"));
                assertNull(dialog.getDialogPane().lookup("#about-timestamp"));
                dialog.getDialogPane().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
                assertFalse(dialog.isShowing());
                dialog.show();
                ((Stage) dialog.getDialogPane().getScene().getWindow()).fireEvent(
                        new WindowEvent(dialog.getDialogPane().getScene().getWindow(), WindowEvent.WINDOW_CLOSE_REQUEST));
                assertFalse(dialog.isShowing());
            } finally { owner.hide(); }
            return null;
        });
    }

    @Test void actualHelpMenuOpensAboutWithoutChangingFinancialOrPreferenceState() throws Exception {
        var repository = new com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository(directory.resolve("settings.json"));
        var store = new com.daviddunn.retirementplanner.app.socialsecurity.results.AnalysisResultStore(
                new com.daviddunn.retirementplanner.persistence.JsonAnalysisSnapshotRepository(directory.resolve("results")));
        var controller = new com.daviddunn.retirementplanner.ui.controller.ApplicationController(
                new com.daviddunn.retirementplanner.persistence.JsonRetirementPlanRepository(), repository, store);
        controller.newPlan(com.daviddunn.retirementplanner.app.baseline.BaselineDetailsTest.plan(true));
        controller.getCurrentProjection();
        var input = com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot.freeze(controller.getCurrentPlan());
        var snapshot = controller.getAcceptedProjectionSnapshot();
        var preferences = controller.getSettingsService().snapshot();
        long revision = controller.getSourcePlanRevision();
        boolean dirty = controller.isModified();
        var field = controller.getClass().getDeclaredField("projectionEngine");
        field.setAccessible(true);
        field.set(controller, new com.daviddunn.retirementplanner.domain.projection.ProjectionEngine() {
            @Override public com.daviddunn.retirementplanner.domain.projection.Projection project(
                    com.daviddunn.retirementplanner.domain.model.RetirementPlan plan) {
                throw new AssertionError("About must not calculate projections");
            }
            @Override public com.daviddunn.retirementplanner.domain.projection.ProjectionExecutionResult projectWithOutcome(
                    com.daviddunn.retirementplanner.domain.model.RetirementPlan plan) {
                throw new AssertionError("About must not calculate projections");
            }
        });
        try {
            fx(() -> {
                try (var manager = ApplicationThemeManager.start(ApplicationTheme.LIGHT)) {
                    var window = new com.daviddunn.retirementplanner.ui.MainWindow(controller);
                    var owner = new Stage(); window.setStage(owner); owner.setScene(window.createScene()); owner.show();
                    try {
                        var menus = (MenuBar) owner.getScene().lookup(".menu-bar");
                        var help = menus.getMenus().stream().filter(menu -> menu.getText().equals("Help")).findFirst().orElseThrow();
                        assertEquals(1, menus.getMenus().stream().filter(menu -> menu.getText().equals("Help")).count());
                        var about = help.getItems().stream().filter(item -> "about-application-menu".equals(item.getId())).findFirst().orElseThrow();
                        assertEquals("About Aledrix Retirement Planning", about.getText());
                        about.fire();
                        var dialogWindow = Window.getWindows().stream().filter(item -> item instanceof Stage stage && stage.getOwner() == owner).findFirst().orElseThrow();
                        var pane = (DialogPane) dialogWindow.getScene().getRoot();
                        assertEquals(ApplicationVersion.current().displaySummary(), ((Label) pane.lookup("#about-version")).getText());
                        ((Button) pane.lookupButton(ButtonType.CLOSE)).fire();
                    } finally { owner.hide(); }
                }
                return null;
            });
            assertEquals(input, com.daviddunn.retirementplanner.app.projection.CompleteProjectionSnapshot.freeze(controller.getCurrentPlan()));
            assertSame(snapshot, controller.getAcceptedProjectionSnapshot());
            assertEquals(revision, controller.getSourcePlanRevision());
            assertEquals(dirty, controller.isModified());
            var after = controller.getSettingsService().snapshot();
            assertEquals(preferences.getTheme(), after.getTheme());
            assertEquals(preferences.getLastOpenedPlan(), after.getLastOpenedPlan());
            assertEquals(preferences.getSuppressedAnalyzerIntroductions(), after.getSuppressedAnalyzerIntroductions());
            assertFalse(Files.exists(directory.resolve("settings.json")));
        } finally {
            controller.getAnalysisParameters().shutdown();
            store.shutdownIfIdle();
            controller.getSettingsService().close();
        }
    }

    private static void save(WritableImage image, String name) throws Exception {
        var directory = Path.of("target/phase13h/previews");
        Files.createDirectories(directory);
        var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) {
            for (int x = 0; x < pixels.getWidth(); x++) pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        }
        javax.imageio.ImageIO.write(pixels, "png", directory.resolve(name).toFile());
    }
}
