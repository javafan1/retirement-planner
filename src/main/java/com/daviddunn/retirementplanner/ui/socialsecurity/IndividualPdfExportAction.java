package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.*;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import java.nio.file.*;
import java.util.Locale;
import java.util.function.*;

/** Native save workflow and background report writing; never schedules analysis work. */
final class IndividualPdfExportAction {
    private final Button button = new Button("Export PDF");
    private final BooleanSupplier ready;
    private final Supplier<IntegratedAnalyzerReport> capture;
    private boolean busy;
    private final String filename;
    IndividualPdfExportAction(String id, String filename, BooleanSupplier ready, Supplier<IntegratedAnalyzerReport> capture) {
        this.filename = filename; this.ready = ready; this.capture = capture;
        button.setId(id); button.setAccessibleText("Export completed individual claiming analysis to PDF");
        com.daviddunn.retirementplanner.ui.controls.InputHelp.install(button,
                "Export the completed frozen result. Run again if the result is stale.");
        button.setOnAction(event -> choose()); refresh();
    }
    Button button() { return button; }
    void refresh() { button.setDisable(busy || !ready.getAsBoolean()); }
    private void choose() {
        if (busy || !ready.getAsBoolean()) return;
        var owner = button.getScene().getWindow();
        try {
            var chooser = new FileChooser(); chooser.setTitle("Export individual claiming PDF");
            chooser.setInitialFileName(filename);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF report", "*.pdf"));
            var file = chooser.showSaveDialog(owner);
            if (file == null || !ready.getAsBoolean()) return;
            Path path = file.toPath();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                path = Path.of(path + ".pdf");
                // The native chooser confirmed the typed path, not the appended-extension path.
                if (Files.exists(path)) {
                    var confirm = new Alert(Alert.AlertType.CONFIRMATION, "Replace existing file " + path.getFileName() + "?", ButtonType.YES, ButtonType.NO);
                    confirm.initOwner(owner);
                    if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;
                }
            }
            if (!ready.getAsBoolean()) return;
            var report = capture.get();
            var destination = path;
            busy = true; refresh();
            var task = new Task<Void>() {
                @Override protected Void call() throws Exception { new IntegratedAnalyzerPdfExporter().export(report, destination); return null; }
            };
            task.setOnSucceeded(event -> {
                busy = false; refresh();
                if (owner.isShowing()) {
                    var alert = new Alert(Alert.AlertType.INFORMATION, "Report saved to " + destination, ButtonType.OK);
                    alert.initOwner(owner); alert.setHeaderText("PDF export complete"); alert.show();
                }
            });
            task.setOnFailed(event -> { busy = false; refresh(); if (owner.isShowing()) error(task.getException()); });
            Thread.ofVirtual().name("individual-analysis-pdf").start(task);
        } catch (RuntimeException exception) { busy = false; refresh(); error(exception); }
    }
    private void error(Throwable failure) {
        var alert = new Alert(Alert.AlertType.ERROR, "Could not export PDF: " + failure.getMessage(), ButtonType.OK);
        alert.initOwner(button.getScene().getWindow()); alert.setHeaderText("PDF export failed"); alert.show();
    }
}
