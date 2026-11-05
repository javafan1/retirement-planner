package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.controls.RequiredFieldSupport;
import com.daviddunn.retirementplanner.ui.help.RothInputHelp;
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
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.Predicate;

public class RothConversionView extends VBox {

    private final Label availabilityLabel = new Label();
    private final Label strategyLabel = new Label("Strategy:");
    private final Label conversionYearLabel = new Label("Conversion Year:");
    private final Label stopRuleLabel = new Label("Stop Rule:");
    private final List<RequiredFieldSupport.Attachment> requiredFieldAttachments = new ArrayList<>();

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
    private Runnable onWorkspaceEditCompleted;
    private Predicate<RothConversionRequest> transactionalApplyHandler;


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
                strategyLabel,
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
                conversionYearLabel,
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
                stopRuleLabel,
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

        InputHelp.install(enabledCheckBox, RothInputHelp.ENABLE);
        InputHelp.install(strategyComboBox, RothInputHelp.STRATEGY);
        InputHelp.install(conversionYearField, RothInputHelp.YEAR);
        InputHelp.install(conversionAmountField, RothInputHelp.AMOUNT);
        InputHelp.install(targetTaxableIncomeField, RothInputHelp.TARGET);
        InputHelp.install(frequencyComboBox, RothInputHelp.FREQUENCY);
        InputHelp.install(stopRuleComboBox, RothInputHelp.STOP);
        InputHelp.linkGridLabels(grid);
        requiredFieldAttachments.add(RequiredFieldSupport.attach(strategyLabel, strategyComboBox,
                enabledCheckBox.selectedProperty()));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(conversionYearLabel, conversionYearField,
                enabledCheckBox.selectedProperty()));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(frequencyLabel, frequencyComboBox,
                enabledCheckBox.selectedProperty()));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(stopRuleLabel, stopRuleComboBox,
                enabledCheckBox.selectedProperty()));
        var fixedAmountRequired = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> enabledCheckBox.isSelected() && strategyComboBox.getValue() == RothConversionStrategy.FIXED_AMOUNT,
                enabledCheckBox.selectedProperty(), strategyComboBox.valueProperty());
        var targetRequired = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> enabledCheckBox.isSelected()
                        && strategyComboBox.getValue() == RothConversionStrategy.CUSTOM_TAXABLE_INCOME_TARGET,
                enabledCheckBox.selectedProperty(), strategyComboBox.valueProperty());
        requiredFieldAttachments.add(RequiredFieldSupport.attach(conversionAmountLabel, conversionAmountField,
                fixedAmountRequired));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(targetTaxableIncomeLabel, targetTaxableIncomeField,
                targetRequired));
        applyButton.disableProperty().bind(dirty.not());
        cancelButton.disableProperty().bind(dirty.not());
        applyButton.setOnAction(event -> {
            if (applyChanges() && onWorkspaceEditCompleted != null) onWorkspaceEditCompleted.run();
        });
        cancelButton.setOnAction(event -> {
            cancelChanges();
            if (onWorkspaceEditCompleted != null) onWorkspaceEditCompleted.run();
        });
        HBox buttonBar = new HBox(10, applyButton, cancelButton);
        buttonBar.setPadding(new Insets(10));
        statusLabel =
                new Label();


        availabilityLabel.setWrapText(true);
        availabilityLabel.setAccessibleText("Roth conversion availability");
        Label requiredLegend = RequiredFieldSupport.createLegend();
        requiredLegend.visibleProperty().bind(enabledCheckBox.selectedProperty());
        requiredLegend.managedProperty().bind(enabledCheckBox.selectedProperty());
        getChildren().addAll(
                availabilityLabel,
                grid,
                requiredLegend,
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

    /** Releases required-indicator listeners when this application view is discarded. */
    public void dispose() {
        requiredFieldAttachments.forEach(RequiredFieldSupport.Attachment::close);
        requiredFieldAttachments.clear();
    }


    private void updateStrategyFields() {

        var availability = com.daviddunn.retirementplanner.domain.roth.RothConversionConfiguration.assess(currentPlan);
        availabilityLabel.setText(availability.guidance());
        boolean savedEnabled = currentPlan != null && currentPlan.getRothConversionRequest() != null
                && currentPlan.getRothConversionRequest().isEnabled();
        enabledCheckBox.setDisable(!availability.canEnable() && !savedEnabled);
        boolean disabled = !enabledCheckBox.isSelected() || !availability.canEnable();
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
        if (plan != currentPlan || currentPlan == null) {
            return false;
        }
        RothConversionRequest updated;
        try {
            updated = readValidated();
        } catch (InputException exception) {
            showValidation(exception);
            return false;
        }
        if (com.daviddunn.retirementplanner.domain.roth.RothConversionConfiguration.same(
                updated, currentPlan.getRothConversionRequest())) {
            return true;
        }
        if (updated != null && updated.isEnabled()
                && !new com.daviddunn.retirementplanner.domain.roth.RothConversionDestinationService()
                .ownersRequiringDestination(currentPlan, updated).isEmpty()) {
            statusLabel.setText("Apply the Roth conversion settings first to confirm and create the modeled Roth IRA destination; Save did not create an account.");
            return false;
        }
        if (transactionalApplyHandler != null) {
            try {
                boolean applied = transactionalApplyHandler.test(updated);
                if (applied) load(currentPlan);
                return applied;
            } catch (RuntimeException exception) {
                statusLabel.setText(exception.getMessage() == null
                        ? "Roth conversion changes could not be saved."
                        : exception.getMessage());
                return false;
            }
        }
        currentPlan.setRothConversionRequest(updated);
        if (onPlanChanged != null) onPlanChanged.run();
        return true;
    }

    public void refresh(RetirementPlan plan) {
        if (plan != currentPlan || !isDirty()) {
            load(plan);
        }
        else {
            updateStrategyFields();
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
        if (transactionalApplyHandler != null) {
            boolean applied;
            try {
                applied = transactionalApplyHandler.test(updated);
            } catch (RuntimeException exception) {
                statusLabel.setText(exception.getMessage() == null
                        ? "Roth conversion changes could not be applied."
                        : exception.getMessage());
                return false;
            }
            if (!applied) {
                return false;
            }
            load(currentPlan);
            statusLabel.setText("Roth conversion applied.");
            return true;
        }
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
            // Explicit disable retains every saved configuration field. Legacy null
            // remains null, and unrelated Save never invents a schedule.
            return com.daviddunn.retirementplanner.domain.roth.RothConversionConfiguration.disabled(applied);
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
        var proposed = new RothConversionRequest(true, year, amount, stop, strategy, frequency, target);
        return read(enabledCheckBox, "Conversion availability", () -> {
            com.daviddunn.retirementplanner.domain.roth.RothConversionConfiguration.validateTransition(currentPlan, proposed);
            return proposed;
        });
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

    /** Installs the shared confirm-and-commit boundary used by the application window. */
    public void setTransactionalApplyHandler(Predicate<RothConversionRequest> handler) {
        this.transactionalApplyHandler = handler;
    }

    public void setOnWorkspaceEditCompleted(Runnable callback) {
        this.onWorkspaceEditCompleted = callback;
    }
}
