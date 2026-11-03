package com.daviddunn.retirementplanner.ui.pdf;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

/** Standard JavaFX adapters; per-export Save policies can replace the chooser adapter. */
public final class PdfExportDialogs {
    private PdfExportDialogs() { }

    public static PdfExportWorkflowCoordinator.ChoicePrompt choicePrompt() {
        return PdfExportChoiceDialog::show;
    }

    public static PdfExportWorkflowCoordinator.DestinationChooser saveChooser(String title) {
        return (owner, suggested) -> {
            var chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.setInitialFileName(suggested);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF report", "*.pdf"));
            File selected = chooser.showSaveDialog(owner);
            return selected == null ? Optional.empty() : Optional.of(Path.of(selected.toURI()));
        };
    }

    public static void showFailure(Window owner, PdfExportWorkflowCoordinator.PdfExportFailure failure) {
        String message = failure.retainedPreview() == null
                ? "The PDF could not be completed: " + message(failure.cause())
                : "The PDF was created, but the system viewer could not be started. The file is available at:\n"
                + failure.retainedPreview();
        var alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("PDF export");
        alert.setHeaderText("PDF export could not be completed");
        if (owner != null) alert.initOwner(owner);
        alert.show();
    }

    private static String message(Throwable failure) {
        if (failure == null) return "Unknown error.";
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
