package com.daviddunn.retirementplanner.ui.socialsecurity;

import com.daviddunn.retirementplanner.app.export.*;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
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
    private final com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator<IntegratedAnalyzerReport> workflow;
    IndividualPdfExportAction(String id, String filename, BooleanSupplier ready, Supplier<IntegratedAnalyzerReport> capture) {
        this(id, filename, ready, capture, createWorkflow());
    }
    IndividualPdfExportAction(String id, String filename, BooleanSupplier ready, Supplier<IntegratedAnalyzerReport> capture,
            com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator<IntegratedAnalyzerReport> workflow) {
        this.filename = filename; this.ready = ready; this.capture = capture;
        this.workflow = java.util.Objects.requireNonNull(workflow);
        button.setId(id); button.setAccessibleText("Export completed individual claiming analysis to PDF");
        com.daviddunn.retirementplanner.ui.controls.InputHelp.install(button,
                "Export the completed frozen result. Run again if the result is stale.");
        button.setOnAction(event -> choose()); refresh();
    }
    Button button() { return button; }
    void refresh() { button.setDisable(busy || !ready.getAsBoolean()); }
    private void choose() {
        if (busy || !ready.getAsBoolean()) return;
        if (button.getScene() == null) return;
        Window owner = button.getScene().getWindow();
        workflow.start(new com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator.Request<>(
                owner, filename, ready,
                capture,
                (report, destination) -> new IntegratedAnalyzerPdfExporter().export(report, destination),
                () -> true,
                (choice, path) -> {
                    if (choice == com.daviddunn.retirementplanner.ui.pdf.PdfExportChoice.SAVE
                            && owner.isShowing() && ready.getAsBoolean()) {
                        var alert = new Alert(Alert.AlertType.INFORMATION, "Report saved to " + path, ButtonType.OK);
                        alert.initOwner(owner); alert.setHeaderText("PDF export complete"); alert.show();
                    }
                },
                failure -> showFailure(owner, failure),
                value -> { busy = value; refresh(); }));
    }

    private static void showFailure(Window owner,
            com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator.PdfExportFailure failure) {
        Window target = owner != null && owner.isShowing() ? owner
                : owner instanceof javafx.stage.Stage stage ? stage.getOwner() : null;
        if (failure.retainedPreview() != null) {
            com.daviddunn.retirementplanner.ui.pdf.PdfExportDialogs.showFailure(target, failure);
            return;
        }
        var alert = new Alert(Alert.AlertType.ERROR, "Could not export PDF: "
                + (failure.cause() == null ? "Unknown error." : failure.cause().getMessage()), ButtonType.OK);
        if (target != null) alert.initOwner(target);
        alert.setHeaderText("PDF export failed"); alert.show();
    }

    private static com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator<IntegratedAnalyzerReport>
            createWorkflow() {
        var chooser = (com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator.DestinationChooser)
                (owner, suggested) -> {
                    var fileChooser = new FileChooser(); fileChooser.setTitle("Export individual claiming PDF");
                    fileChooser.setInitialFileName(suggested);
                    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF report", "*.pdf"));
                    var file = fileChooser.showSaveDialog(owner);
                    if (file == null) return java.util.Optional.empty();
                    Path destination = file.toPath();
                    if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                        destination = Path.of(destination + ".pdf");
                        if (Files.exists(destination)) {
                            var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                                    "Replace existing file " + destination.getFileName() + "?", ButtonType.YES, ButtonType.NO);
                            if (owner != null) confirm.initOwner(owner);
                            if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return java.util.Optional.empty();
                        }
                    }
                    return java.util.Optional.of(destination);
                };
        return com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowFactory.create("Export individual claiming PDF", chooser);
    }
}
