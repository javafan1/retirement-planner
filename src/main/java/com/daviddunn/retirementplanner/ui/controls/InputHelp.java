package com.daviddunn.retirementplanner.ui.controls;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

/** Descriptive help only: never changes an input value, validation or event handler. */
public final class InputHelp {
    private InputHelp() { }

    public static void install(Control input, String text) {
        input.setTooltip(HelpIcon.createTooltip(text));
        if (input.getAccessibleHelp() == null || input.getAccessibleHelp().isBlank()) {
            input.setAccessibleHelp(text);
        }
    }

    public static void link(Label label, Control input) {
        label.setLabelFor(input);
        label.setTooltip(input.getTooltip());
    }

    /** Explicitly called by simple form grids, before skins exist. Ignores spanning headings. */
    public static void linkGridLabels(GridPane grid) {
        for (Node node : grid.getChildren()) {
            if (!(node instanceof Control input) || input instanceof Label || input.getTooltip() == null) {
                continue;
            }
            Label nearest = null;
            int nearestColumn = -1;
            for (Node candidate : grid.getChildren()) {
                if (candidate instanceof Label label && !(label instanceof HelpIcon)
                        && (GridPane.getColumnSpan(label) == null || GridPane.getColumnSpan(label) == 1)
                        && row(label) == row(input) && column(label) < column(input)
                        && column(label) > nearestColumn) {
                    nearest = label;
                    nearestColumn = column(label);
                }
            }
            if (nearest != null) link(nearest, input);
        }
    }

    private static int row(Node node) {
        return GridPane.getRowIndex(node) == null ? 0 : GridPane.getRowIndex(node);
    }

    private static int column(Node node) {
        return GridPane.getColumnIndex(node) == null ? 0 : GridPane.getColumnIndex(node);
    }
}
