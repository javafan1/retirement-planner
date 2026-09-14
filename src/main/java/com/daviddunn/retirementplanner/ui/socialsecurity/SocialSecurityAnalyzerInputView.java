package com.daviddunn.retirementplanner.ui.socialsecurity;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Natural, width-aware content for the analyzer's shared inputs section. */
final class SocialSecurityAnalyzerInputView extends VBox {
    private final GridPane current = grid(30, 50, 20);

    SocialSecurityAnalyzerInputView() {
        super(10);
        setPadding(new Insets(10));
        setMinWidth(0);
        setMinHeight(Region.USE_PREF_SIZE);
        GridPane matrix = grid(34, 22, 22, 22);
        matrix.addRow(0, label("Input / Assumption"), label("Social Security Only"),
                label("Deterministic Integrated"), label("Longevity-Weighted Integrated"));
        int index = 1;
        for (var row : SocialSecurityAnalyzerInputMatrix.rows()) {
            matrix.addRow(index++, label(row.input()), label(row.socialSecurity().text()),
                    label(row.deterministic().text()), label(row.weighted().text()));
        }
        getChildren().addAll(label(SocialSecurityAnalyzerInputMatrix.SS_OBJECTIVE),
                label(SocialSecurityAnalyzerInputMatrix.DETERMINISTIC_OBJECTIVE),
                label(SocialSecurityAnalyzerInputMatrix.WEIGHTED_OBJECTIVE),
                label(SocialSecurityAnalyzerInputMatrix.QUICK_NOTE), label(SocialSecurityAnalyzerInputMatrix.BASELINE_NOTE), matrix,
                label("Current inputs below update as you edit. Completed results retain their result-time assumptions and are marked stale when affected."),
                label(SocialSecurityAnalyzerInputSummary.DATE_EXPLANATION), current);
    }

    void show(SocialSecurityAnalyzerInputSummary summary) {
        current.getChildren().clear();
        current.addRow(0, label("Plan / Analyzer Input"), label("Current Value"), label("Source"));
        int index = 1;
        for (var row : summary.rows()) {
            current.addRow(index++, label(row.input()), label(row.value()), label(row.source()));
        }
    }

    private static GridPane grid(double... widths) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setMinWidth(0);
        grid.setMinHeight(Region.USE_PREF_SIZE);
        for (double width : widths) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(width);
            grid.getColumnConstraints().add(column);
        }
        return grid;
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
