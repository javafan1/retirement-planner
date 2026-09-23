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

public final class BreakEvenAnalysisDialog extends Dialog<Void> {
    private final BreakEvenPdfReport pdfReport;
    public BreakEvenAnalysisDialog(BreakEvenAnalysisResult result) {
        this(result, BreakEvenContext.unavailable());
    }

    public BreakEvenAnalysisDialog(BreakEvenAnalysisResult result, BreakEvenContext context) {
        this(result, context, new com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsightService().prepare(result));
    }

    public BreakEvenAnalysisDialog(BreakEvenAnalysisResult result, BreakEvenContext context,
            com.daviddunn.retirementplanner.domain.breakeven.BreakEvenInsight insight) {
        pdfReport = new BreakEvenPdfReport(result, context, insight);
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
        var exportButton = getDialogPane().lookupButton(export);
        exportButton.setId("break-even-export-pdf");
        exportButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            choosePdf();
        });
        getDialogPane().getStylesheets().add(getClass().getResource("/css/results-summary.css").toExternalForm());
        getDialogPane().getStylesheets().add(getClass().getResource("/css/break-even.css").toExternalForm());
    }
    /** Captures completed inputs independently of chart selection and disclosure state. */
    public BreakEvenPdfReport preparePdfReport() { return pdfReport; }

    public void exportPdf(Path file) throws IOException {
        new BreakEvenPdfExporter().export(pdfReport, file);
    }

    private void choosePdf() {
        var chooser = new FileChooser();
        chooser.setTitle("Export Break-Even Analysis PDF");
        chooser.setInitialFileName("Break-Even-Analysis.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF report", "*.pdf"));
        var owner = getDialogPane().getScene().getWindow();
        var file = chooser.showSaveDialog(owner);
        if (file == null) return;
        Path path = file.toPath();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) path = Path.of(path + ".pdf");
        try {
            exportPdf(path);
        } catch (IOException exception) {
            var alert = new Alert(Alert.AlertType.ERROR, "Could not export PDF: " + exception.getMessage(), ButtonType.OK);
            alert.initOwner(owner);
            alert.showAndWait();
        }
    }
}