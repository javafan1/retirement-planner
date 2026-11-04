package com.daviddunn.retirementplanner.ui.pdf;

import com.daviddunn.retirementplanner.application.settings.*;
import com.daviddunn.retirementplanner.persistence.JsonApplicationSettingsRepository;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PdfExportDirectoryContextTest {
    @TempDir Path directory;

    @BeforeAll static void startFx() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }

    @Test void saveChooserDirectoryIsCreatedOnlyWhenSaveAdapterPreparesIt() throws Exception {
        var export = directory.resolve("new-export");
        var repository = new JsonApplicationSettingsRepository(directory.resolve("settings.json"));
        try (var service = new ApplicationSettingsService(repository)) {
            service.savePreferences(ApplicationTheme.LIGHT, null, export.toString(), false)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertFalse(Files.exists(export));
            fx(() -> {
                var stage = new Stage(); stage.setScene(new Scene(new Pane(), 300, 100)); stage.show();
                PdfExportDirectoryContext.attach(stage, service);
                var chooser = new FileChooser();
                assertTrue(PdfExportDirectoryContext.prepare(chooser, stage));
                assertEquals(export.toAbsolutePath().normalize().toFile(), chooser.getInitialDirectory());
                assertTrue(Files.isDirectory(export));
                stage.close(); return null;
            });
        }
    }

    @Test void standaloneChooserKeepsNativeDefaultAndInvalidPathIsReportedToCaller() throws Exception {
        fx(() -> {
            var chooser = new FileChooser();
            assertTrue(PdfExportDirectoryContext.prepare(chooser, null));
            assertNull(chooser.getInitialDirectory());
            return null;
        });
        assertThrows(RuntimeException.class, () -> PdfExportDirectoryContext.resolveAndCreate("\u0000"));
    }
}
