package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.Control;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class RothConversionView extends VBox {

    private final CheckBox enabledCheckBox;

    private final TextField conversionYearField;

    private final Label conversionAmountLabel;

    private final TextField conversionAmountField;

    private final Label targetTaxableIncomeLabel;

    private final TextField targetTaxableIncomeField;

    private final Label frequencyLabel;

    private final ComboBox<RothConversionFrequency>
            frequencyComboBox;

    private final ComboBox<RothConversionStrategy>
            strategyComboBox;

    private final ComboBox<RothConversionStopRule>
            stopRuleComboBox;

    private final Label statusLabel;

    private final Button applyButton = new Button("Apply");
    private final Button cancelButton = new Button("Cancel");
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
    private boolean loading;

    private RetirementPlan currentPlan;

    private Runnable onPlanChanged;


    public RothConversionView() {

        setPadding(
                new Insets(20));

        setSpacing(15);


        GridPane grid =
                new GridPane();

        grid.setHgap(10);
        grid.setVgap(10);


        int row = 0;


        /*
         * =================================================
         * Roth Conversion
         * =================================================
         */

        Label heading =
                new Label(
                        "Roth Conversion");

        heading.setStyle(
                "-fx-font-weight: bold;");


        grid.add(
                heading,
                0,
                row++,
                2,
                1);


        grid.add(
                new Separator(),
                0,
                row++,
                2,
                1);


        /*
         * Enable conversion.
         */

        enabledCheckBox =
                new CheckBox(
                        "Enable Roth conversion");

        grid.add(
                enabledCheckBox,
                0,
                row++,
                2,
                1);


        /*
         * Strategy.
         */

        grid.add(
                new Label(
                        "Strategy:"),
                0,
                row);


        strategyComboBox =
                new ComboBox<>();

        strategyComboBox
                .getItems()
                .addAll(
                        RothConversionStrategy.values());

        strategyComboBox.setConverter(
                new StringConverter<>() {

                    @Override
                    public String toString(
                            RothConversionStrategy strategy) {

                        if (strategy == null) {
                            return "";
                        }

                        return strategy ==
                                RothConversionStrategy
                                        .CUSTOM_TAXABLE_INCOME_TARGET
                                ? "Custom Taxable Income Target"
                                : strategy.name();
                    }

                    @Override
                    public RothConversionStrategy fromString(
                            String value) {

                        if ("Custom Taxable Income Target"
                                .equals(value)) {

                            return RothConversionStrategy
                                    .CUSTOM_TAXABLE_INCOME_TARGET;
                        }

                        return RothConversionStrategy.valueOf(value);
                    }
                });

        strategyComboBox
                .getSelectionModel()
                .select(
                        RothConversionStrategy.FIXED_AMOUNT);

        strategyComboBox
                .valueProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                updateStrategyFields());

        grid.add(
                strategyComboBox,
                1,
                row++);


        /*
         * Conversion year.
         */

        grid.add(
                new Label(
                        "Conversion Year:"),
                0,
                row);


        conversionYearField =
                new TextField();

        grid.add(
                conversionYearField,
                1,
                row++);


        /*
         * Conversion amount.
         */

        conversionAmountLabel =
                new Label(
                        "Conversion Amount:");

        grid.add(
                conversionAmountLabel,
                0,
                row);


        conversionAmountField =
                new TextField();

        grid.add(
                conversionAmountField,
                1,
                row++);

        targetTaxableIncomeLabel =
                new Label(
                        "Target Taxable Income:");

        grid.add(
                targetTaxableIncomeLabel,
                0,
                row);

        targetTaxableIncomeField =
                new TextField();

        targetTaxableIncomeField.setPromptText(
                "Target Taxable Income");

        grid.add(
                targetTaxableIncomeField,
                1,
                row++);


        /*
         * Conversion frequency.
         */

        frequencyLabel =
                new Label(
                        "Frequency:");

        grid.add(
                frequencyLabel,
                0,
                row);


        frequencyComboBox =
                new ComboBox<>();

        frequencyComboBox
                .getItems()
                .addAll(
                        RothConversionFrequency.values());

        frequencyComboBox
                .getSelectionModel()
                .select(
                        RothConversionFrequency.ONE_TIME);

        grid.add(
                frequencyComboBox,
                1,
                row++);


        /*
         * Stop rule.
         */

        grid.add(
                new Label(
                        "Stop Rule:"),
                0,
                row);


        stopRuleComboBox =
                new ComboBox<>();

        stopRuleComboBox
                .getItems()
                .addAll(
                        RothConversionStopRule.values());

        stopRuleComboBox
                .getSelectionModel()
                .select(
                        RothConversionStopRule
                                .FIRST_HOUSEHOLD_RMD);

        grid.add(
                stopRuleComboBox,
                1,
                row++);


        /*
         * Apply button.
         */

        applyButton.disableProperty().bind(dirty.not());
        cancelButton.disableProperty().bind(dirty.not());
        applyButton.setOnAction(event -> applyChanges());
        cancelButton.setOnAction(event -> cancelChanges());
        HBox buttonBar = new HBox(10, applyButton, cancelButton);
        buttonBar.setPadding(new Insets(10));
        statusLabel =
                new Label();


        getChildren().addAll(
                grid,
                buttonBar,
                statusLabel);

        for (TextField field : List.of(conversionYearField, conversionAmountField, targetTaxableIncomeField)) {
            field.textProperty().addListener((observable, oldValue, newValue) -> updateDirty());
        }
        enabledCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateStrategyFields();
            updateDirty();
        });
        strategyComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateDirty());
        frequencyComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateDirty());
        stopRuleComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateDirty());


        /*
         * Configure the initial field visibility.
         */

        updateStrategyFields();
    }


    private void updateStrategyFields() {

        boolean disabled = !enabledCheckBox.isSelected();
        for (Control control : List.of(strategyComboBox, conversionYearField, conversionAmountField,
                targetTaxableIncomeField, frequencyComboBox, stopRuleComboBox)) {
            control.setDisable(disabled);
        }

        boolean fixedAmount =
                strategyComboBox.getValue()
                        == RothConversionStrategy.FIXED_AMOUNT;

        boolean customTarget =
                strategyComboBox.getValue()
                        == RothConversionStrategy
                        .CUSTOM_TAXABLE_INCOME_TARGET;


        /*
         * Fixed-dollar conversions require
         * both an amount and a frequency.
         */

        conversionAmountLabel.setVisible(
                fixedAmount);

        conversionAmountLabel.setManaged(
                fixedAmount);

        conversionAmountField.setVisible(
                fixedAmount);

        conversionAmountField.setManaged(
                fixedAmount);

        targetTaxableIncomeLabel.setVisible(
                customTarget);

        targetTaxableIncomeLabel.setManaged(
                customTarget);

        targetTaxableIncomeField.setVisible(
                customTarget);

        targetTaxableIncomeField.setManaged(
                customTarget);
    }


    public void load(
            RetirementPlan plan) {

        currentPlan = Objects.requireNonNull(plan);
        loading = true;

        RothConversionRequest request =
                plan.getRothConversionRequest();


        if (request == null) {

            enabledCheckBox.setSelected(
                    false);

            conversionYearField.setText("");

            conversionAmountField.setText("");

            targetTaxableIncomeField.setText("");

            strategyComboBox
                    .getSelectionModel()
                    .select(
                            RothConversionStrategy
                                    .FIXED_AMOUNT);

            frequencyComboBox
                    .getSelectionModel()
                    .select(
                            RothConversionFrequency
                                    .ONE_TIME);

            stopRuleComboBox
                    .getSelectionModel()
                    .select(
                            RothConversionStopRule
                                    .FIRST_HOUSEHOLD_RMD);

            statusLabel.setText("");

            updateStrategyFields();
            loading = false;
            updateDirty();

            return;
        }


        enabledCheckBox.setSelected(
                request.isEnabled());


        conversionYearField.setText(
                Integer.toString(
                        request.getStartYear()));


        strategyComboBox
                .getSelectionModel()
                .select(
                        request.getStrategy());


        if (request.getStrategy()
                == RothConversionStrategy.FIXED_AMOUNT) {

            conversionAmountField.setText(
                    request
                            .getAnnualAmount()
                            .stripTrailingZeros()
                            .toPlainString());

        } else {

            conversionAmountField.setText("");
        }

        if (request.getStrategy()
                == RothConversionStrategy
                .CUSTOM_TAXABLE_INCOME_TARGET) {

            targetTaxableIncomeField.setText(
                    request
                            .getCustomTargetTaxableIncome()
                            .stripTrailingZeros()
                            .toPlainString());

        } else {

            targetTaxableIncomeField.setText("");
        }


        frequencyComboBox
                .getSelectionModel()
                .select(
                        request.getFrequency());


        stopRuleComboBox
                .getSelectionModel()
                .select(
                        request.getStopRule());


        updateStrategyFields();

        statusLabel.setText("");
        loading = false;
        updateDirty();
    }


    public boolean save(RetirementPlan plan) {
        return plan == currentPlan && applyChanges();
    }

    public void refresh(RetirementPlan plan) {
        if (plan != currentPlan || !isDirty()) {
            load(plan);
        }
        else {
            updateDirty();
        }
    }

    public ReadOnlyBooleanProperty dirtyProperty() {
        return dirty.getReadOnlyProperty();
    }

    public boolean isDirty() {
        return dirty.get();
    }

    public void cancelChanges() {
        if (currentPlan != null) {
            load(currentPlan);
        }
    }

    public boolean validateChanges() {
        if (currentPlan == null) {
            return false;
        }
        try {
            readValidated();
            return true;
        }
        catch (InputException exception) {
            showValidation(exception);
            return false;
        }
    }

    public boolean applyChanges() {
        if (currentPlan == null) {
            return false;
        }
        RothConversionRequest updated;
        try {
            updated = readValidated();
        }
        catch (InputException exception) {
            showValidation(exception);
            updateDirty();
            return false;
        }
        boolean changed = !values(updated).equals(values(currentPlan.getRothConversionRequest()));
        if (changed) {
            // The immutable request is fully validated before the only model write.
            currentPlan.setRothConversionRequest(updated);
        }
        load(currentPlan);
        statusLabel.setText("Roth conversion applied.");
        if (changed && onPlanChanged != null) {
            onPlanChanged.run();
        }
        return true;
    }

    private void updateDirty() {
        if (loading) {
            return;
        }
        try {
            dirty.set(currentPlan != null
                    && !values(readValidated()).equals(values(currentPlan.getRothConversionRequest())));
        }
        catch (InputException exception) {
            dirty.set(currentPlan != null);
        }
    }

    private RothConversionRequest readValidated() {
        RothConversionRequest applied = currentPlan.getRothConversionRequest();
        if (!enabledCheckBox.isSelected()) {
            // Explicit disable still removes the request. Preserve an already disabled
            // persisted configuration during an unrelated Save.
            return applied != null && !applied.isEnabled() ? applied : null;
        }
        RothConversionStrategy strategy = read(strategyComboBox, "Conversion strategy", () ->
                Objects.requireNonNull(strategyComboBox.getValue(), "A selection is required."));
        int year = read(conversionYearField, "Conversion year", () -> {
            int value = Integer.parseInt(conversionYearField.getText().trim());
            if (value < 1900) {
                throw new IllegalArgumentException("Must be at least 1900.");
            }
            return value;
        });
        // Non-fixed amounts are dormant persisted data; keep them on unrelated edits.
        BigDecimal amount = strategy == RothConversionStrategy.FIXED_AMOUNT
                ? nonNegative(conversionAmountField, "Conversion amount")
                : applied == null ? BigDecimal.ZERO : applied.getAnnualAmount();
        BigDecimal target = strategy == RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET
                ? nonNegative(targetTaxableIncomeField, "Target taxable income") : null;
        RothConversionFrequency frequency = read(frequencyComboBox, "Conversion frequency", () ->
                Objects.requireNonNull(frequencyComboBox.getValue(), "A selection is required."));
        RothConversionStopRule stop = read(stopRuleComboBox, "Stop rule", () ->
                Objects.requireNonNull(stopRuleComboBox.getValue(), "A selection is required."));
        return new RothConversionRequest(true, year, amount, stop, strategy, frequency, target);
    }

    private static List<Object> values(RothConversionRequest request) {
        if (request == null) {
            return List.of();
        }
        return Arrays.<Object>asList(request.isEnabled(), request.getStartYear(), request.getAnnualAmount(),
                request.getStrategy(), request.getCustomTargetTaxableIncome(), request.getFrequency(),
                request.getStopRule()).stream()
                .map(value -> value instanceof BigDecimal decimal ? decimal.stripTrailingZeros() : value)
                .toList();
    }

    private BigDecimal nonNegative(TextField field, String name) {
        return read(field, name, () -> {
            BigDecimal value = new BigDecimal(field.getText().trim());
            if (value.signum() < 0) {
                throw new IllegalArgumentException("Cannot be negative.");
            }
            return value;
        });
    }

    private static <T> T read(Control control, String name, Supplier<T> parser) {
        try {
            return parser.get();
        }
        catch (RuntimeException exception) {
            throw new InputException(control, name + ": "
                    + (exception instanceof NumberFormatException ? "Enter a valid number."
                    : exception.getMessage()));
        }
    }

    private void showValidation(InputException exception) {
        statusLabel.setText(exception.getMessage());
        exception.control.requestFocus();
    }

    private static final class InputException extends IllegalArgumentException {
        private final Control control;

        private InputException(Control control, String message) {
            super(message);
            this.control = control;
        }
    }

    public void setOnPlanChanged(Runnable onPlanChanged) {
        this.onPlanChanged = onPlanChanged;
    }
}
