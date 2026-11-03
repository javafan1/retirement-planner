package com.daviddunn.retirementplanner.ui.breakeven;

import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenAnalysisResult;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenContext;
import javafx.scene.control.*;
import javafx.stage.Screen;
import javafx.stage.FileChooser;
import javafx.event.ActionEvent;
import com.daviddunn.retirementplanner.app.export.*;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowCoordinator;
import com.daviddunn.retirementplanner.ui.pdf.PdfExportWorkflowFactory;

public final class BreakEvenAnalysisDialog extends Dialog<Void> {
    private final BreakEvenAnalysisResult result;
    private final BreakEvenContext context;
    private final com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsight insight;
    private BreakEvenPdfReport pdfReport;
    private final PdfExportWorkflowCoordinator<BreakEvenPdfReport> pdfWorkflow;
    private Button exportButton;
    private final ProgressIndicator pdfProgress = new ProgressIndicator();
    public BreakEvenAnalysisDialog(BreakEvenAnalysisResult result) {
        this(result, BreakEvenContext.unavailable());
    }

    public BreakEvenAnalysisDialog(BreakEvenAnalysisResult result, BreakEvenContext context) {
        this(result, context, new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsightService().prepare(result));
    }

    public BreakEvenAnalysisDialog(BreakEvenAnalysisResult result, BreakEvenContext context,
            com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsight insight) {
        this(result, context, insight, createPdfWorkflow());
    }

    public BreakEvenAnalysisDialog(BreakEvenAnalysisResult result, BreakEvenContext context,
            com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsight insight,
            PdfExportWorkflowCoordinator<BreakEvenPdfReport> pdfWorkflow) {
        this.result = java.util.Objects.requireNonNull(result);
        this.context = java.util.Objects.requireNonNull(context);
        this.insight = java.util.Objects.requireNonNull(insight);
        this.pdfWorkflow = java.util.Objects.requireNonNull(pdfWorkflow);
        setTitle("Break-Even Analysis");
        setHeaderText("Break-Even Analysis");
        setResizable(true);
        ScrollPane scroll = new ScrollPane(new BreakEvenAnalysisView(result, context, insight));
        scroll.setFitToWidth(true);
        getDialogPane().setContent(scroll);
        var screen = Screen.getPrimary().getVisualBounds();
        getDialogPane().setPrefSize(Math.min(1200, screen.getWidth() - 64),
                Math.min(920, screen.getHeight() - 80));
        var export = new ButtonType("Export PDF", ButtonBar.ButtonData.OTHER);
        getDialogPane().getButtonTypes().addAll(export, ButtonType.CLOSE);
        exportButton = (Button) getDialogPane().lookupButton(export);
        pdfProgress.setPrefSize(15, 15);
        pdfProgress.setMaxSize(15, 15);
        exportButton.setId("break-even-export-pdf");
        exportButton.setDisable(false);
        exportButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            choosePdf();
        });
        getDialogPane().getStylesheets().add(getClass().getResource("/css/results-summary.css").toExternalForm());
        getDialogPane().getStylesheets().add(getClass().getResource("/css/break-even.css").toExternalForm());
    }
    /** Captures completed inputs independently of chart selection and disclosure state. */
    public BreakEvenPdfReport preparePdfReport() {
        if (pdfReport == null) pdfReport = new BreakEvenPdfReport(result, context, insight);
        return pdfReport;
    }

    public void exportPdf(Path file) throws IOException {
        new BreakEvenPdfExporter().export(preparePdfReport(), file);
    }

    private void choosePdf() {
        var owner = getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow();
        pdfWorkflow.start(new PdfExportWorkflowCoordinator.Request<>(owner, "Break-Even-Analysis.pdf",
                () -> owner != null && owner.isShowing(), this::preparePdfReport,
                (report, destination) -> new BreakEvenPdfExporter().export(report, destination),
                () -> true,
                (choice, path) -> { },
                failure -> {
                    if (failure.retainedPreview() != null) {
                        com.daviddunn.retirementplanner.ui.pdf.PdfExportDialogs.showFailure(owner, failure);
                    } else {
                        var alert = new Alert(Alert.AlertType.ERROR,
                                "Could not export PDF: " + (failure.cause() == null ? "Unknown error." : failure.cause().getMessage()),
                                ButtonType.OK);
                        alert.initOwner(owner);
                        alert.showAndWait();
                    }
                },
                busy -> {
                    exportButton.setDisable(busy);
                    exportButton.setGraphic(busy ? pdfProgress : null);
                    exportButton.setAccessibleHelp(busy ? "PDF export is in progress." : null);
                }));
    }

    private static PdfExportWorkflowCoordinator<BreakEvenPdfReport> createPdfWorkflow() {
        PdfExportWorkflowCoordinator.DestinationChooser saveChooser = (owner, suggested) -> {
            var chooser = new FileChooser();
            chooser.setTitle("Export Break-Even Analysis PDF");
            chooser.setInitialFileName(suggested);
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF report", "*.pdf"));
            var file = chooser.showSaveDialog(owner);
            if (file == null) return Optional.empty();
            Path path = file.toPath();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) path = Path.of(path + ".pdf");
            return Optional.of(path);
        };
        return PdfExportWorkflowFactory.create("Export Break-Even Analysis PDF", saveChooser);
    }
}
