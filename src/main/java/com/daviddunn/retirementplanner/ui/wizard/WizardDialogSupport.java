package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.util.Money;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Opt-in presentation around existing dialog builders; contains no financial rules. */
public final class WizardDialogSupport {

    public enum Kind { TEXT, NUMBER, DATE, CHOICE }

    public record Field(String name, Control input, Kind kind, boolean required) { }

    private WizardDialogSupport() { }

    public static <T> void install(Dialog<T> dialog, List<Field> fields, Supplier<T> build) {

        GridPane grid = (GridPane) dialog.getDialogPane().getContent();
        Label error = new Label();
        error.setId("wizard-editor-error");
        error.setWrapText(true);
        error.setMinHeight(Region.USE_PREF_SIZE);
        error.getStyleClass().add("wizard-field-error");
        error.managedProperty().bind(error.textProperty().isNotEmpty());
        error.visibleProperty().bind(error.managedProperty());
        for (Field field : fields) {
            Control input = field.input();
            input.setId("wizard-input-" + field.name().toLowerCase(java.util.Locale.ROOT).replace(' ', '-'));
            input.setMaxWidth(Double.MAX_VALUE);
            if (input.getTooltip() == null) {
                InputHelp.install(input, field.name() + (field.required() ? " is required for this item." : " is optional."));
            }
            grid.getChildren().stream().filter(Label.class::isInstance).map(Label.class::cast)
                    .filter(label -> Objects.equals(GridPane.getRowIndex(label), GridPane.getRowIndex(input)))
                    .filter(label -> GridPane.getColumnIndex(label) == null || GridPane.getColumnIndex(label) == 0)
                    .findFirst().ifPresent(label -> {
                        label.setText(field.name() + (field.required() ? " *" : ""));
                        label.setLabelFor(input);
                    });
            Runnable clear = () -> {
                input.pseudoClassStateChanged(PseudoClass.getPseudoClass("invalid"), false);
                error.setText("");
            };
            if (input instanceof TextField text) text.textProperty().addListener((o, a, b) -> clear.run());
            if (input instanceof DatePicker date) date.getEditor().textProperty().addListener((o, a, b) -> clear.run());
            if (input instanceof ComboBox<?> combo) combo.valueProperty().addListener((o, a, b) -> clear.run());
        }
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints inputs = new ColumnConstraints();
        inputs.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labels, inputs);
        VBox body = new VBox(10, new Label("* Required"), grid, error);
        body.setPadding(new javafx.geometry.Insets(10));
        dialog.getDialogPane().setContent(body);
        dialog.getDialogPane().getStyleClass().add("new-plan-wizard");
        dialog.getDialogPane().getStylesheets().add(Objects.requireNonNull(
                WizardDialogSupport.class.getResource("/css/new-plan-wizard.css")).toExternalForm());
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(560);
        final Object[] result = new Object[1];
        dialog.getDialogPane().addEventFilter(ActionEvent.ACTION, event -> {
            if (event.getTarget() != dialog.getDialogPane().lookupButton(ButtonType.OK)) return;
            Field affected = null;
            try {
                for (Field field : fields) {
                    if (!field.input().isManaged() || !field.input().isVisible()) continue;
                    affected = field;
                    Control input = field.input();
                    if (input instanceof TextField text) {
                        String value = text.getText().trim();
                        if (field.required() && value.isEmpty()) throw new IllegalArgumentException("A value is required.");
                        if (!value.isEmpty() && field.kind() == Kind.NUMBER) Money.of(value);
                    }
                    if (input instanceof ComboBox<?> combo && field.required() && combo.getValue() == null) {
                        throw new IllegalArgumentException("A selection is required.");
                    }
                    if (input instanceof DatePicker date) {
                        java.time.LocalDate value;
                        try {
                            value = date.getConverter().fromString(date.getEditor().getText());
                        }
                        catch (RuntimeException invalid) {
                            throw new IllegalArgumentException("Enter a valid date.");
                        }
                        if (field.required() && value == null) throw new IllegalArgumentException("A date is required.");
                        date.setValue(value);
                    }
                }
                result[0] = build.get();
                error.setText("");
            }
            catch (RuntimeException exception) {
                event.consume();
                String message = exception instanceof NumberFormatException ? "Enter a valid number." : exception.getMessage();
                if (message == null) message = "Please complete the required fields.";
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                for (Field field : fields) {
                    if (lower.contains(field.name().toLowerCase(java.util.Locale.ROOT))) affected = field;
                }
                if (lower.contains("end date")) affected = fields.stream()
                        .filter(field -> field.name().equals("End Date")).findFirst().orElse(affected);
                if (affected != null) {
                    affected.input().pseudoClassStateChanged(PseudoClass.getPseudoClass("invalid"), true);
                    affected.input().requestFocus();
                    error.setText(affected.name() + ": " + message);
                }
                else error.setText(message);
            }
        });
        dialog.setResultConverter(type -> {
            if (type != ButtonType.OK) return null;
            @SuppressWarnings("unchecked") T value = (T) result[0];
            return value;
        });
    }
}
