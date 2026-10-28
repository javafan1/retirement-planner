package com.daviddunn.retirementplanner.ui.startup;

import com.daviddunn.retirementplanner.application.branding.ApplicationBranding;
import com.daviddunn.retirementplanner.application.settings.*;
import com.daviddunn.retirementplanner.persistence.*;
import com.daviddunn.retirementplanner.ui.*;
import com.daviddunn.retirementplanner.ui.controller.ApplicationController;
import com.daviddunn.retirementplanner.ui.settings.ApplicationThemeManager;
import com.daviddunn.retirementplanner.app.socialsecurity.results.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.Label;
import javafx.scene.image.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class StartupSplashViewTest {
    @TempDir Path directory;
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); }); }
        catch (IllegalStateException initialized) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(30, TimeUnit.SECONDS);
    }
    @Test void productionImagesDecodeAndReferenceSheetIsNeverEligible() throws Exception {
        fx(() -> {
            var image = BrandingImages.load(ApplicationBranding.defaults().logoResource()).orElseThrow();
            assertTrue(image.getWidth() >= 1024); assertEquals(0, image.getPixelReader().getColor(0, 0).getOpacity());
            var icon = BrandingImages.load(BrandingImages.ICON).orElseThrow();
            assertEquals(icon.getWidth(), icon.getHeight());
            assertTrue(BrandingImages.load("images/branding/aledrix-logo-reference.png").isEmpty());
            return null;
        });
    }
    @Test void missingCorruptOversizedAndExcessiveDimensionsYieldTextOnlyView() throws Exception {
        fx(() -> {
            var name = ApplicationBranding.defaults().logoResource();
            assertTrue(BrandingImages.load(name, ignored -> null).isEmpty());
            assertTrue(BrandingImages.load(name, ignored -> new ByteArrayInputStream(new byte[33])).isEmpty());
            var oversized = new byte[8 * 1024 * 1024 + 1];
            assertTrue(BrandingImages.load(name, ignored -> new ByteArrayInputStream(oversized)).isEmpty());
            var header = ByteBuffer.allocate(33).putLong(0x89504e470d0a1a0aL).putInt(13).putInt(0x49484452)
                    .putInt(Integer.MAX_VALUE).putInt(1).array();
            assertTrue(BrandingImages.load(name, ignored -> new ByteArrayInputStream(header)).isEmpty());
            var view = new StartupSplashView(ApplicationBranding.defaults(), Optional.empty());
            assertNull(view.lookup("#branding-logo"));
            assertEquals("Aledrix Retirement Planning", ((Label)view.lookup("#branding-product")).getText());
            assertEquals("The Science of Retirement Planning", ((Label)view.lookup("#branding-tagline")).getText());
            return null;
        });
    }
    @ParameterizedTest @EnumSource(ApplicationTheme.class)
    void splashAndNewMainShareThemeSettingsTitleAndPreviewRollback(ApplicationTheme theme) throws Exception {
        var repository = new JsonApplicationSettingsRepository(directory.resolve("settings.json"));
        var initial = new ApplicationSettings(); initial.setTheme(theme); repository.save(initial);
        var store = new AnalysisResultStore(new JsonAnalysisSnapshotRepository(directory.resolve("analysis")));
        try (var settings = new ApplicationSettingsService(repository)) {
            var controller = fx(() -> new ApplicationController(new JsonRetirementPlanRepository(), repository, store, settings));
            assertSame(settings, controller.getSettingsService());
            assertNull(CompletableFuture.supplyAsync(controller::prepareStartup).get(10, TimeUnit.SECONDS));
            long revision = controller.getSourcePlanRevision();
            fx(() -> {
                var manager = ApplicationThemeManager.start(settings.snapshot().getTheme());
                var splash = new Stage(); var view = new StartupSplashView(ApplicationBranding.defaults(),
                        BrandingImages.load(ApplicationBranding.defaults().logoResource()));
                var scene = new Scene(view); ApplicationThemeManager.prepare(scene); splash.setScene(scene); splash.show();
                assertEquals(theme, manager.theme());
                var custom = new ApplicationBranding("Aledrix Test Edition", "Aledrix Retirement Planning", "The Science of Retirement Planning",
                        ApplicationBranding.defaults().logoResource());
                var window = new MainWindow(controller, custom, manager, null);
                var main = new Stage(); window.setStage(main); main.setScene(window.createScene()); main.show();
                assertEquals("Aledrix Test Edition - Untitled", main.getTitle());
                assertEquals(revision, controller.getSourcePlanRevision()); assertFalse(controller.isModified());
                assertTrue(scene.getRoot().getStylesheets().stream().anyMatch(s -> s.endsWith("theme-" + theme.name().toLowerCase() + ".css")));
                manager.apply(theme == ApplicationTheme.LIGHT ? ApplicationTheme.DARK : ApplicationTheme.LIGHT);
                manager.apply(settings.snapshot().getTheme()); assertEquals(theme, manager.theme());
                assertSame(settings, controller.getSettingsService());
                controller.markModified(); window.setStage(main);
                assertEquals("Aledrix Test Edition - Untitled *", main.getTitle());
                main.hide(); splash.hide(); splash.setScene(null); manager.close();
                assertTrue(view.getStylesheets().stream().noneMatch(s -> s.endsWith("theme-dark.css") || s.endsWith("theme-light.css")));
                return null;
            });
            controller.getAnalysisParameters().shutdown();
        } finally {
            store.shutdownIfIdle();
        }
    }

    @ParameterizedTest @EnumSource(ApplicationTheme.class)
    void renderSplashPreviews(ApplicationTheme theme) throws Exception {
        fx(() -> {
            try (var manager = ApplicationThemeManager.start(theme)) {
                var view = new StartupSplashView(ApplicationBranding.defaults(), BrandingImages.load(ApplicationBranding.defaults().logoResource()));
                var scene = new Scene(view, 560, 380); ApplicationThemeManager.prepare(scene);
                view.applyCss(); view.layout();
                assertEquals("Aledrix Retirement Planning", ((Label)view.lookup("#branding-product")).getText());
                var image = scene.snapshot(null); save(image, "splash-" + theme.name().toLowerCase() + ".png");
                var twice = new SnapshotParameters(); twice.setTransform(javafx.scene.transform.Transform.scale(2, 2));
                save(view.snapshot(twice, null), "splash-" + theme.name().toLowerCase() + "-2x.png");
                return null;
            }
        });
    }
    @Test void boundedLongDeploymentTextUsesNaturalHeightWithoutHorizontalOverflow() throws Exception {
        fx(() -> {
            try (var manager = ApplicationThemeManager.start(ApplicationTheme.LIGHT)) {
                var branding = new ApplicationBranding("Title", "Aledrix retirement planning ".repeat(5),
                        "The science of retirement planning ".repeat(6), ApplicationBranding.defaults().logoResource());
                var view = new StartupSplashView(branding, BrandingImages.load(branding.logoResource()));
                var scene = new Scene(view); ApplicationThemeManager.prepare(scene);
                var stage = new Stage(); stage.setScene(scene);
                try {
                    stage.show(); view.applyCss(); stage.sizeToScene(); view.layout();
                    assertTrue(scene.getHeight() >= 380);
                    for (var node : view.getChildren()) {
                        assertTrue(node.getBoundsInParent().getMaxX() <= scene.getWidth());
                        assertTrue(node.getBoundsInParent().getMaxY() <= scene.getHeight());
                    }
                } finally { stage.hide(); }
                return null;
            }
        });
    }
    private static void save(WritableImage image, String name) throws Exception {
        var output = Path.of("target/phase13c-previews"); Files.createDirectories(output);
        var buffer = new java.awt.image.BufferedImage((int)image.getWidth(), (int)image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<buffer.getHeight(); y++) for (int x=0; x<buffer.getWidth(); x++) buffer.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(buffer,"png",output.resolve(name).toFile());
    }
}
