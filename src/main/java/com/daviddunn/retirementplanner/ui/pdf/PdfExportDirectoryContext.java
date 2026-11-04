package com.daviddunn.retirementplanner.ui.pdf;

import com.daviddunn.retirementplanner.application.settings.ApplicationSettingsService;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Associates owned PDF dialogs with the application's one persisted settings authority. */
public final class PdfExportDirectoryContext {
    private static final Object SETTINGS_KEY = PdfExportDirectoryContext.class.getName();

    private PdfExportDirectoryContext() { }

    public static void attach(Window applicationWindow, ApplicationSettingsService settings) {
        applicationWindow.getProperties().put(SETTINGS_KEY, java.util.Objects.requireNonNull(settings));
    }

    /** Initializes a chooser only for a Save request; failure is explicit and never falls back silently. */
    public static boolean prepare(FileChooser chooser, Window owner) {
        var settings = settingsFor(owner);
        if (settings == null) return true; // Standalone/test windows retain the native chooser default.
        Path directory;
        try {
            String configured = settings.snapshot().getDefaultExportDirectory();
            directory = resolveAndCreate(configured);
            chooser.setInitialDirectory(directory.toFile());
            return true;
        } catch (IOException | SecurityException | IllegalArgumentException failure) {
            var alert = new Alert(Alert.AlertType.ERROR,
                    "The Default Export Directory could not be used. Check the path and permissions in Application Settings.\n"
                            + (failure.getMessage() == null ? "Invalid or unavailable directory." : failure.getMessage()),
                    ButtonType.OK);
            alert.setTitle("PDF export directory unavailable");
            alert.setHeaderText("Cannot open the PDF Save dialog");
            if (owner != null && owner.isShowing()) alert.initOwner(owner);
            alert.showAndWait();
            return false;
        }
    }

    public static Path resolveAndCreate(String configured) throws IOException {
        if (configured == null || configured.isBlank()) throw new IOException("No Default Export Directory is configured.");
        Path directory = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory) || !Files.isReadable(directory) || !Files.isWritable(directory)) {
            throw new IOException("The configured export directory is not readable and writable.");
        }
        return directory;
    }

    private static ApplicationSettingsService settingsFor(Window owner) {
        Window current = owner;
        while (current != null) {
            Object value = current.getProperties().get(SETTINGS_KEY);
            if (value instanceof ApplicationSettingsService service) return service;
            if (current instanceof Stage stage) current = stage.getOwner();
            else if (current instanceof PopupWindow popup) current = popup.getOwnerWindow();
            else current = null;
        }
        return null;
    }
}
