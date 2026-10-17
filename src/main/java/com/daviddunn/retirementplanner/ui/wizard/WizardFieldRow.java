package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Presentation around an existing control; parsing and validation remain with its editor. */
public final class WizardFieldRow extends VBox {

    public enum Requiredness { REQUIRED, CONDITIONALLY_REQUIRED, RECOMMENDED, OPTIONAL, ADVANCED }

    private final Control input;
    private final Label issue = new Label();

    public WizardFieldRow(String name, Requiredness requiredness, Control input, String units, String help) {
        super(4);
        this.input = java.util.Objects.requireNonNull(input);
        getStyleClass().add("wizard-field-row");
        String description = name + " — " + requiredness.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        Label label = new Label(description);
        label.setLabelFor(input);
        label.setWrapText(true);
        label.setMinHeight(USE_PREF_SIZE);
        Label unit = new Label(units);
        unit.getStyleClass().add("wizard-field-units");
        unit.setWrapText(true);
        unit.setMinHeight(USE_PREF_SIZE);
        input.setAccessibleText(description + "; " + units);
        InputHelp.install(input, help);
        Button helpButton = new Button("Help: " + name);
        helpButton.getStyleClass().add("wizard-help-button");
        helpButton.setAccessibleText("Show or hide help for " + name);
        Label explanation = new Label(help);
        input.setAccessibleHelp(help);
        input.tooltipProperty().addListener((observable, previous, current) -> {
            String text = current == null ? help : current.getText();
            explanation.setText(text);
            input.setAccessibleHelp(text);
        });
        explanation.setWrapText(true);
        explanation.setMinHeight(USE_PREF_SIZE);
        explanation.setVisible(false);
        explanation.setManaged(false);
        helpButton.setOnAction(event -> {
            explanation.setVisible(!explanation.isVisible());
            explanation.setManaged(explanation.isVisible());
        });
        issue.getStyleClass().add("wizard-field-error");
        issue.setWrapText(true);
        issue.setMinHeight(USE_PREF_SIZE);
        issue.visibleProperty().bind(issue.textProperty().isNotEmpty());
        issue.managedProperty().bind(issue.visibleProperty());
        getChildren().setAll(label, input, unit, helpButton, explanation, issue);
        // Only the owning editor knows whether a correction is valid. Keep issues until revalidation.
    }

    public void setIssue(String message) {
        issue.setText(java.util.Objects.requireNonNull(message));
        input.pseudoClassStateChanged(PseudoClass.getPseudoClass("invalid"), !message.isEmpty());
        issue.setAccessibleText(message);
    }

    public void clearIssue() { setIssue(""); }

    public void focusInput() { input.requestFocus(); }
}
