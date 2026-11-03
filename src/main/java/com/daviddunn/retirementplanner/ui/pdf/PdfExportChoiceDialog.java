package com.daviddunn.retirementplanner.ui.pdf;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Compact owner-aware choice dialog. Closing the window and Escape both mean Cancel. */
public final class PdfExportChoiceDialog {
    private PdfExportChoiceDialog() { }

    public static PdfExportChoice show(Window owner) {
        var dialog = create(owner);
        return dialog.showAndWait().orElse(PdfExportChoice.CANCEL);
    }

    static Dialog<PdfExportChoice> create(Window owner) {
        var dialog = new Dialog<PdfExportChoice>();
        dialog.setTitle("Export PDF");
        dialog.setHeaderText("What would you like to do with this PDF?");
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        } else {
            dialog.initModality(Modality.APPLICATION_MODAL);
        }

        var view = new ButtonType("View PDF", ButtonBar.ButtonData.YES);
        var save = new ButtonType("Save PDF", ButtonBar.ButtonData.NO);
        var cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(view, save, cancel);
        var explanation = new Label("View opens the report in your default PDF viewer. Save lets you choose where to store a copy.");
        explanation.setWrapText(true);
        explanation.setAccessibleText(explanation.getText());
        var content = new VBox(10, explanation);
        content.getStyleClass().add("pdf-export-choice");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStyleClass().add("pdf-export-choice-pane");
        dialog.getDialogPane().setAccessibleText("Choose whether to view, save, or cancel this PDF export");
        dialog.setResultConverter(button -> button == view ? PdfExportChoice.VIEW
                : button == save ? PdfExportChoice.SAVE : PdfExportChoice.CANCEL);
        dialog.setOnShown(event -> {
            var defaultButton = (Button) dialog.getDialogPane().lookupButton(view);
            defaultButton.setDefaultButton(true);
            defaultButton.setAccessibleText("View PDF in the default PDF viewer");
            ((Button) dialog.getDialogPane().lookupButton(save)).setAccessibleText("Save PDF to a selected location");
            ((Button) dialog.getDialogPane().lookupButton(cancel)).setAccessibleText("Cancel PDF export");
            defaultButton.requestFocus();
        });
        return dialog;
    }
}
