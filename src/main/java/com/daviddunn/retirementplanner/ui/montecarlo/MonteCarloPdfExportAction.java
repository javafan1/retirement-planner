package com.daviddunn.retirementplanner.ui.montecarlo;

import com.daviddunn.retirementplanner.app.export.MonteCarloPdfExporter;
import com.daviddunn.retirementplanner.app.export.MonteCarloPdfReport;
import com.daviddunn.retirementplanner.ui.pdf.PdfExportDialogs;
import com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator;
import com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowFactory;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Shared View/Save workflow for completed Monte Carlo reports; never schedules analysis work. */
final class MonteCarloPdfExportAction {
    private final Button button = new Button("Export PDF");
    private final BooleanSupplier ready;
    private final Supplier<MonteCarloPdfReport> capture;
    private final Supplier<Object> selectedIdentity;
    private final Predicate<Object> identityCurrent;
    private final Function<Object, String> suggestedFilename;
    private final PdfExportWorkflowCoordinator<MonteCarloPdfReport> workflow;

    MonteCarloPdfExportAction(String id, BooleanSupplier ready, Supplier<MonteCarloPdfReport> capture,
                              Supplier<Object> selectedIdentity, Predicate<Object> identityCurrent,
                              Function<Object, String> suggestedFilename) {
        this(id, ready, capture, selectedIdentity, identityCurrent, suggestedFilename, createWorkflow());
    }

    MonteCarloPdfExportAction(String id, BooleanSupplier ready, Supplier<MonteCarloPdfReport> capture,
                              Supplier<Object> selectedIdentity, Predicate<Object> identityCurrent,
                              Function<Object, String> suggestedFilename,
                              PdfExportWorkflowCoordinator<MonteCarloPdfReport> workflow) {
        this.ready = ready;
        this.capture = capture;
        this.selectedIdentity = selectedIdentity;
        this.identityCurrent = identityCurrent;
        this.suggestedFilename = suggestedFilename;
        this.workflow = workflow;
        button.setId(id);
        button.setAccessibleText("Export completed Monte Carlo analysis to PDF");
        button.setTooltip(new Tooltip("Export frozen completed results, including clearly labeled Outdated historical results."));
        button.setOnAction(event -> choose());
        refresh();
    }

    Button button() { return button; }

    void refresh() {
        button.setDisable(workflow.isBusy() || !ready.getAsBoolean());
    }

    private void choose() {
        if (!ready.getAsBoolean() || workflow.isBusy() || button.getScene() == null) return;
        Object acceptedIdentity = selectedIdentity.get();
        if (acceptedIdentity == null) return;
        Window owner = button.getScene().getWindow();
        String filename = suggestedFilename.apply(acceptedIdentity);
        workflow.start(new PdfExportWorkflowCoordinator.Request<>(
                owner,
                filename,
                () -> ready.getAsBoolean() && selectedIdentity.get() == acceptedIdentity,
                capture,
                (report, destination) -> new MonteCarloPdfExporter().export(report, destination),
                // A captured report remains valid even after its view closes. The callback below
                // suppresses only stale success UI; failures still reach the user.
                () -> true,
                (choice, path) -> {
                    if (choice == com.daviddunn.retirementplanner.ui.pdf.PdfExportChoice.SAVE
                            && identityCurrent.test(acceptedIdentity) && owner.isShowing()) {
                        var alert = new Alert(Alert.AlertType.INFORMATION, "Report saved to " + path, ButtonType.OK);
                        alert.initOwner(owner);
                        alert.setHeaderText("PDF export complete");
                        alert.show();
                    }
                },
                failure -> PdfExportDialogs.showFailure(owner.isShowing() ? owner : null, failure),
                ignored -> refresh()));
    }

    private static PdfExportWorkflowCoordinator<MonteCarloPdfReport> createWorkflow() {
        return PdfExportWorkflowFactory.create("Export Monte Carlo PDF", MonteCarloPdfExportAction::chooseDestination);
    }

    private static Optional<Path> chooseDestination(Window owner, String suggestedFilename) {
        var chooser = new FileChooser();
        chooser.setTitle("Export Monte Carlo PDF");
        chooser.setInitialFileName(suggestedFilename);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF report", "*.pdf"));
        File file = chooser.showSaveDialog(owner);
        if (file == null) return Optional.empty();

        Path path = file.toPath();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            path = Path.of(path + ".pdf");
            // Preserve the existing confirmation for a collision created by extension normalization.
            if (Files.exists(path)) {
                var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Replace existing file " + path.getFileName() + "?", ButtonType.YES, ButtonType.NO);
                confirm.initOwner(owner);
                if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return Optional.empty();
            }
        }
        return Optional.of(path);
    }
}
