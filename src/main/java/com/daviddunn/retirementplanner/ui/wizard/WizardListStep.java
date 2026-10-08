package com.daviddunn.retirementplanner.ui.wizard;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Function;

/** Compact item list shared by creation steps; editors return new domain objects. */
abstract class WizardListStep<T> implements NewPlanWizardStep {

    protected final NewPlanDraft draft;
    protected final TableView<T> table = new TableView<>();
    protected final VBox content = new VBox(12);
    protected final Label message = new Label();

    WizardListStep(NewPlanDraft draft, String description, String emptyText) {
        this.draft = java.util.Objects.requireNonNull(draft);
        Label explanation = new Label(description);
        explanation.setWrapText(true);
        explanation.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        table.setPrefHeight(240);
        table.setMinHeight(150);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label(emptyText));
        message.setWrapText(true);
        message.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        message.getStyleClass().add("wizard-field-error");
        message.visibleProperty().bind(message.textProperty().isNotEmpty());
        message.managedProperty().bind(message.visibleProperty());
        content.getChildren().addAll(explanation, table, message);
    }

    protected void column(String title, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        table.getColumns().add(column);
    }

    protected Button action(String text, String id, Runnable run) {
        Button button = new Button(text);
        button.setId(id);
        button.setOnAction(event -> run.run());
        return button;
    }

    protected void actions(Button... buttons) {
        content.getChildren().add(content.getChildren().size() - 1, new HBox(10, buttons));
    }

    protected <R> void editDialog(Dialog<R> dialog, java.util.function.Consumer<R> apply) {
        if (content.getScene() != null) dialog.initOwner(content.getScene().getWindow());
        dialog.showAndWait().ifPresent(apply);
    }

    @Override
    public Node content() { return content; }

    @Override
    public boolean validateAndApply() {
        try {
            draft.getPlan().validateHouseholdReferences();
            message.setText("");
            return true;
        }
        catch (IllegalArgumentException exception) {
            message.setText(exception.getMessage());
            return false;
        }
    }
}
