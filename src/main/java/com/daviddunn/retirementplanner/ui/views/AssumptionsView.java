package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import com.daviddunn.retirementplanner.ui.help.HelpText;
import com.daviddunn.retirementplanner.ui.rmd.OpeningRmdWorkflowService;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class AssumptionsView extends VBox {

    /*
     * Projection assumptions.
     */
    private final DatePicker projectionStartDatePicker;
    private final TextField projectionLengthField;

    /*
     * Death scenario assumptions.
     */
    private final ComboBox<DeathScenario>
            deathScenarioComboBox;

    private final TextField deathYearField;

    private final ComboBox<Integer>
            survivorClaimingAgeComboBox;

    private final TextField postDeathExpenseFactorField;

    /*
     * Economic assumptions.
     */
    private final TextField investmentReturnField;
    private final TextField inflationRateField;
    private final TextField healthcareInflationField;
    private final TextField socialSecurityColaField;

    /*
     * Tax assumptions.
     */
    private final TextField federalBracketGrowthField;
    private final TextField standardDeductionGrowthField;
    private final TextField futureFederalMarginalRateAdjustmentField;
    private final TextField futureFederalMarginalRateEffectiveYearField;
    private final TextField stateIncomeTaxRateField;
    private final TextField localIncomeTaxRateField;
    private final TextField estimatedHeirTaxRateField;

    private final Label statusLabel;
    private final Button openingRmdButton;

    private final Button applyButton = new Button("Apply");
    private final Button cancelButton = new Button("Cancel");
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
    private boolean loading;
    private RetirementPlan currentPlan;

    /*
     * MainWindow registers a callback here so
     * projections can be refreshed whenever
     * assumptions change.
     */
    private Runnable onPlanChanged;
    private Runnable onOpeningRmdRequested;

    public AssumptionsView() {

        setPadding(new Insets(20));
        setSpacing(15);

        /*
         * Projection fields.
         */
        projectionStartDatePicker =
                new DatePicker();

        projectionLengthField =
                new TextField();

        /*
         * Death scenario fields.
         */
        deathScenarioComboBox =
                new ComboBox<>();

        deathScenarioComboBox
                .getItems()
                .addAll(
                        DeathScenario.values());

        deathYearField =
                new TextField();

        survivorClaimingAgeComboBox =
                new ComboBox<>();

        for (int age = 62; age <= 70; age++) {
            survivorClaimingAgeComboBox
                    .getItems()
                    .add(age);
        }

        deathScenarioComboBox
                .valueProperty()
                .addListener(
                        (observable,
                         oldValue,
                         newValue) ->
                                updateDeathScenarioFields());

        postDeathExpenseFactorField =
                new TextField("100");

        /*
         * Economic fields.
         */
        investmentReturnField =
                new TextField();

        inflationRateField =
                new TextField();

        healthcareInflationField =
                new TextField();

        socialSecurityColaField =
                new TextField();

        /*
         * Tax fields.
         */
        federalBracketGrowthField =
                new TextField();

        standardDeductionGrowthField =
                new TextField();

        futureFederalMarginalRateAdjustmentField =
                new TextField();

        futureFederalMarginalRateEffectiveYearField =
                new TextField();

        stateIncomeTaxRateField =
                new TextField();

        localIncomeTaxRateField =
                new TextField();

        estimatedHeirTaxRateField =
                new TextField();

        statusLabel =
                new Label();

        applyButton.setOnAction(e -> applyChanges());
        cancelButton.setOnAction(e -> cancelChanges());
        applyButton.disableProperty().bind(dirty.not());
        cancelButton.disableProperty().bind(dirty.not());

        openingRmdButton =
                new Button("Opening RMD Information...");

        openingRmdButton.setOnAction(
                e -> requestOpeningRmdInformation());

        GridPane grid =
                new GridPane();

        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        /*
         * =================================================
         * Projection
         * =================================================
         */

        Label projectionHeading =
                new Label("Projection");

        projectionHeading.setStyle(
                "-fx-font-weight: bold;");

        grid.add(
                projectionHeading,
                0,
                row++,
                3,
                1);

        grid.add(
                new Separator(),
                0,
                row++,
                3,
                1);

        grid.add(
                new Label("Projection Start Date:"),
                0,
                row);

        grid.add(
                projectionStartDatePicker,
                1,
                row++);

        grid.add(
                new Label("Projection Length (Years):"),
                0,
                row);

        grid.add(
                projectionLengthField,
                1,
                row++);

        grid.add(
                openingRmdButton,
                1,
                row++);


        /*
         * =================================================
         * Economic Assumptions
         * =================================================
         */

        Label economicHeading =
                new Label("Economic Assumptions");

        economicHeading.setStyle(
                "-fx-font-weight: bold;");

        grid.add(
                economicHeading,
                0,
                row++,
                3,
                1);

        grid.add(
                new Separator(),
                0,
                row++,
                3,
                1);

        grid.add(
                new Label(
                        "Annual Investment Return (%):"),
                0,
                row);

        grid.add(
                new HelpIcon(
                        HelpText.INVESTMENT_RETURN),
                1,
                row);

        grid.add(
                investmentReturnField,
                2,
                row++);

        grid.add(
                new Label(
                        "General Inflation Rate (%):"),
                0,
                row);

        grid.add(
                new HelpIcon(
                        HelpText.GENERAL_INFLATION),
                1,
                row);

        grid.add(
                inflationRateField,
                2,
                row++);

        grid.add(
                new Label(
                        "Healthcare Inflation Rate (%):"),
                0,
                row);

        grid.add(
                new HelpIcon(
                        HelpText.HEALTHCARE_INFLATION),
                1,
                row);

        grid.add(
                healthcareInflationField,
                2,
                row++);

        grid.add(
                new Label(
                        "Social Security COLA (%):"),
                0,
                row);

        grid.add(
                new HelpIcon(
                        HelpText.SOCIAL_SECURITY_COLA),
                1,
                row);

        grid.add(
                socialSecurityColaField,
                2,
                row++);

        /*
         * =================================================
         * Tax Assumptions
         * =================================================
         */

        Label taxHeading =
                new Label("Tax Assumptions");

        taxHeading.setStyle(
                "-fx-font-weight: bold;");

        grid.add(
                taxHeading,
                0,
                row++,
                3,
                1);

        grid.add(
                new Separator(),
                0,
                row++,
                3,
                1);

        grid.add(
                new Label(
                        "Federal Tax Bracket Growth (%):"),
                0,
                row);

        grid.add(
                federalBracketGrowthField,
                1,
                row++);

        grid.add(
                new Label(
                        "Standard Deduction Growth (%):"),
                0,
                row);

        grid.add(
                standardDeductionGrowthField,
                1,
                row++);

        grid.add(
                new Label(
                        "Future Federal Tax Rate Change (% points):"),
                0,
                row);

        grid.add(
                futureFederalMarginalRateAdjustmentField,
                1,
                row++);

        grid.add(
                new Label("Effective Year:"),
                0,
                row);

        grid.add(
                futureFederalMarginalRateEffectiveYearField,
                1,
                row++);

        grid.add(
                new Label(
                        "State Income Tax Rate (%):"),
                0,
                row);

        grid.add(
                stateIncomeTaxRateField,
                1,
                row++);

        grid.add(
                new Label(
                        "Local Income Tax Rate (%):"),
                0,
                row);

        grid.add(
                localIncomeTaxRateField,
                1,
                row++);

        grid.add(
                new Label(
                        "Estimated Heir Tax Rate (%):"),
                0,
                row);

        grid.add(
                estimatedHeirTaxRateField,
                1,
                row++);

        /*
         * =================================================
         * Death Scenario
         * =================================================
         */

        Label deathScenarioHeading =
                new Label("Death Scenario");

        deathScenarioHeading.setStyle(
                "-fx-font-weight: bold;");

        grid.add(
                deathScenarioHeading,
                0,
                row++,
                3,
                1);

        grid.add(
                new Separator(),
                0,
                row++,
                3,
                1);

        grid.add(
                new Label("Death Scenario:"),
                0,
                row);

        grid.add(
                deathScenarioComboBox,
                1,
                row++);

        grid.add(
                new Label("Death Year:"),
                0,
                row);

        grid.add(
                deathYearField,
                1,
                row++);

        grid.add(
                new Label("Survivor SSC Claiming Age:"),
                0,
                row);

        grid.add(
                survivorClaimingAgeComboBox,
                1,
                row++);

        grid.add(
                new Label("Post-Death Expense Factor (%):"),
                0,
                row);

        grid.add(
                postDeathExpenseFactorField,
                1,
                row++);


        HBox buttonBar = new HBox(10, applyButton, cancelButton);
        buttonBar.setPadding(new Insets(10));
        getChildren().addAll(grid, buttonBar, statusLabel);

        for (TextField field : textFields()) {
            field.textProperty().addListener((observable, oldValue, newValue) -> updateDirty());
        }
        projectionStartDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            projectionStartDatePicker.getEditor().setText(
                    projectionStartDatePicker.getConverter().toString(newValue));
            updateDirty();
        });
        projectionStartDatePicker.getEditor().textProperty().addListener((observable, oldValue, newValue) -> updateDirty());
        deathScenarioComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateDirty());
        survivorClaimingAgeComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateDirty());

        /*
         * Establish the initial disabled state.
         */
        updateDeathScenarioFields();
    }

    public void load(
            RetirementPlan plan) {

        currentPlan = Objects.requireNonNull(plan);
        loading = true;

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        EconomicAssumptions economicAssumptions =
                assumptions
                        .getEconomicAssumptions();

        TaxAssumptions taxAssumptions =
                assumptions
                        .getTaxAssumptions();

        DeathScenarioAssumptions
                deathScenarioAssumptions =
                assumptions
                        .getDeathScenarioAssumptions();

        /*
         * Projection.
         */
        projectionStartDatePicker.setValue(
                assumptions
                        .getProjectionStartDate());

        projectionLengthField.setText(
                Integer.toString(
                        assumptions
                                .getProjectionLengthYears()));

        /*
         * Death scenario.
         */
        deathScenarioComboBox.setValue(
                deathScenarioAssumptions
                        .getDeathScenario());

        Integer deathYear =
                deathScenarioAssumptions
                        .getDeathYear();

        deathYearField.setText(
                deathYear != null
                        ? Integer.toString(deathYear)
                        : "");

        survivorClaimingAgeComboBox.setValue(
                deathScenarioAssumptions
                        .getSurvivorClaimingAge());

        BigDecimal postDeathExpenseFactor =
                deathScenarioAssumptions
                        .getPostDeathExpenseFactor();

        postDeathExpenseFactorField.setText(
                postDeathExpenseFactor
                        .multiply(BigDecimal.valueOf(100))
                        .stripTrailingZeros()
                        .toPlainString());

        projectionStartDatePicker.getEditor().setText(
                projectionStartDatePicker.getConverter().toString(assumptions.getProjectionStartDate()));
        updateDeathScenarioFields();

        /*
         * Economic assumptions.
         */
        investmentReturnField.setText(
                toPercent(
                        economicAssumptions
                                .getExpectedAnnualInvestmentReturn()));

        inflationRateField.setText(
                toPercent(
                        economicAssumptions
                                .getExpectedAnnualInflationRate()));

        healthcareInflationField.setText(
                toPercent(
                        economicAssumptions
                                .getHealthcareInflationRate()));

        socialSecurityColaField.setText(
                toPercent(
                        economicAssumptions
                                .getSocialSecurityColaRate()));

        /*
         * Tax assumptions.
         */
        federalBracketGrowthField.setText(
                toPercent(
                        taxAssumptions
                                .getFederalTaxBracketGrowthRate()));

        standardDeductionGrowthField.setText(
                toPercent(
                        taxAssumptions
                                .getStandardDeductionGrowthRate()));

        BigDecimal futureFederalMarginalRateAdjustment =
                taxAssumptions
                        .getFutureFederalMarginalRateAdjustment();

        futureFederalMarginalRateAdjustmentField.setText(
                futureFederalMarginalRateAdjustment != null
                        ? toPercent(
                        futureFederalMarginalRateAdjustment)
                        : "");

        Integer futureFederalMarginalRateEffectiveYear =
                taxAssumptions
                        .getFutureFederalMarginalRateEffectiveYear();

        futureFederalMarginalRateEffectiveYearField.setText(
                futureFederalMarginalRateEffectiveYear != null
                        ? futureFederalMarginalRateEffectiveYear.toString()
                        : "");

        stateIncomeTaxRateField.setText(
                toPercent(
                        taxAssumptions
                                .getStateIncomeTaxRate()));

        localIncomeTaxRateField.setText(
                toPercent(
                        taxAssumptions
                                .getLocalIncomeTaxRate()));

        estimatedHeirTaxRateField.setText(
                toPercent(
                        taxAssumptions
                                .getEstimatedHeirTaxRateOnTaxDeferredAssets()));

        statusLabel.setText("");

        updateOpeningRmdButtonVisibility();
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
            updateOpeningRmdButtonVisibility();
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
            statusLabel.setText(exception.getMessage());
            exception.control.requestFocus();
            return false;
        }
    }

    public boolean applyChanges() {
        if (currentPlan == null) {
            return false;
        }
        PlanningAssumptions updated;
        try {
            updated = readValidated();
        }
        catch (InputException exception) {
            statusLabel.setText(exception.getMessage());
            exception.control.requestFocus();
            updateDirty();
            return false;
        }
        boolean changed = !values(updated).equals(values(currentPlan.getPlanningAssumptions()));
        if (changed) {
            // All parsing and domain validation completes before the single model write.
            currentPlan.setPlanningAssumptions(updated);
        }
        load(currentPlan);
        statusLabel.setText("Assumptions applied.");
        if (changed) {
            notifyPlanChanged();
        }
        return true;
    }

    private void updateDirty() {
        if (loading) {
            return;
        }
        try {
            dirty.set(currentPlan != null
                    && !values(readValidated()).equals(values(currentPlan.getPlanningAssumptions())));
        }
        catch (InputException exception) {
            dirty.set(currentPlan != null);
        }
    }

    private List<TextField> textFields() {
        return List.of(projectionLengthField, deathYearField, postDeathExpenseFactorField,
                investmentReturnField, inflationRateField, healthcareInflationField,
                socialSecurityColaField, federalBracketGrowthField, standardDeductionGrowthField,
                futureFederalMarginalRateAdjustmentField, futureFederalMarginalRateEffectiveYearField,
                stateIncomeTaxRateField, localIncomeTaxRateField, estimatedHeirTaxRateField);
    }

    private PlanningAssumptions readValidated() {
        LocalDate start = read(projectionStartDatePicker, "Projection start date", () ->
                Objects.requireNonNull(projectionStartDatePicker.getConverter().fromString(
                        projectionStartDatePicker.getEditor().getText()), "A date is required."));
        int length = read(projectionLengthField, "Projection length", () -> {
            int value = Integer.parseInt(projectionLengthField.getText().trim());
            if (value <= 0) {
                throw new IllegalArgumentException("Must be greater than zero.");
            }
            return value;
        });
        EconomicAssumptions economic = new EconomicAssumptions(
                percent(investmentReturnField, "Annual investment return"),
                percent(inflationRateField, "General inflation"),
                percent(healthcareInflationField, "Healthcare inflation"),
                percent(socialSecurityColaField, "Social Security COLA"));
        BigDecimal bracket = percent(federalBracketGrowthField, "Federal tax bracket growth");
        BigDecimal deduction = percent(standardDeductionGrowthField, "Standard deduction growth");
        PlanningAssumptions applied = currentPlan.getPlanningAssumptions();
        FutureFederalTaxRateChangeInput future =
                readFutureFederalRateChange(applied.getTaxAssumptions());
        BigDecimal state = percent(stateIncomeTaxRateField, "State income tax rate");
        BigDecimal local = percent(localIncomeTaxRateField, "Local income tax rate");
        BigDecimal heir = percent(estimatedHeirTaxRateField, "Estimated heir tax rate");
        TaxAssumptions tax = read(estimatedHeirTaxRateField, "Estimated heir tax rate", () ->
                new TaxAssumptions(bracket, deduction, state, local,
                        applied.getTaxAssumptions().getFilingStatus(), heir,
                        future.adjustment(), future.effectiveYear()));
        DeathScenario scenario = read(deathScenarioComboBox, "Death scenario", () ->
                Objects.requireNonNull(deathScenarioComboBox.getValue(), "A selection is required."));
        DeathScenarioAssumptions appliedDeath = applied.getDeathScenarioAssumptions();
        boolean active = scenario != DeathScenario.BOTH_SURVIVE;
        // Preserve stored dormant policy on an unrelated edit, but retain the existing
        // clear-year/age behavior when explicitly switching an active scenario off.
        Integer dormantYear = appliedDeath.getDeathScenario() == DeathScenario.BOTH_SURVIVE
                ? appliedDeath.getDeathYear() : null;
        Integer dormantAge = appliedDeath.getDeathScenario() == DeathScenario.BOTH_SURVIVE
                ? appliedDeath.getSurvivorClaimingAge() : null;
        Integer year = !active ? dormantYear : read(deathYearField, "Death year", () -> {
            String text = deathYearField.getText().trim();
            Integer value = text.isEmpty() ? null : Integer.valueOf(text);
            if (value == null) {
                throw new IllegalArgumentException("A year is required.");
            }
            if (value != null && value <= 0) {
                throw new IllegalArgumentException("Must be greater than zero.");
            }
            return value;
        });
        Integer age = !active ? dormantAge : read(survivorClaimingAgeComboBox, "Survivor claiming age", () -> {
            Integer value = survivorClaimingAgeComboBox.getValue();
            if (value == null) {
                throw new IllegalArgumentException("An age is required.");
            }
            if (value != null && (value < 62 || value > 70)) {
                throw new IllegalArgumentException("Must be between 62 and 70.");
            }
            return value;
        });
        BigDecimal factor = percent(postDeathExpenseFactorField, "Post-death expense factor");
        DeathScenarioAssumptions death = read(postDeathExpenseFactorField, "Post-death expense factor", () ->
                new DeathScenarioAssumptions(scenario, year, age, factor));
        return new PlanningAssumptions(economic, tax, applied.getWithdrawalAssumptions(), death, length, start);
    }

    private FutureFederalTaxRateChangeInput readFutureFederalRateChange(TaxAssumptions applied) {
        String adjustmentText = futureFederalMarginalRateAdjustmentField.getText().trim();
        String yearText = futureFederalMarginalRateEffectiveYearField.getText().trim();
        BigDecimal exactAdjustment = adjustmentText.replace("%", "").isBlank()
                ? null
                : read(futureFederalMarginalRateAdjustmentField, "Future federal tax rate change",
                        () -> parsePercent(adjustmentText.replace("%", "")));
        if (!yearText.isEmpty()) {
            read(futureFederalMarginalRateEffectiveYearField, "Effective year", () -> {
                int year = Integer.parseInt(yearText);
                if (year <= 0) {
                    throw new IllegalArgumentException("Must be positive.");
                }
                return year;
            });
        }
        Control missingField = exactAdjustment == null
                ? futureFederalMarginalRateAdjustmentField
                : futureFederalMarginalRateEffectiveYearField;
        FutureFederalTaxRateChangeInput parsed = read(missingField, "Future federal tax rate change",
                () -> FutureFederalTaxRateChangeInput.parse(adjustmentText, yearText));
        BigDecimal appliedAdjustment = applied.getFutureFederalMarginalRateAdjustment();
        // An untouched persisted rate must not be rounded by the shared input parser.
        if (exactAdjustment != null && appliedAdjustment != null
                && exactAdjustment.compareTo(appliedAdjustment) == 0) {
            return new FutureFederalTaxRateChangeInput(appliedAdjustment, parsed.effectiveYear());
        }
        return parsed;
    }

    // Only editable values participate. Normalize BigDecimals without losing numeric precision.
    private static List<Object> values(PlanningAssumptions assumptions) {
        EconomicAssumptions economic = assumptions.getEconomicAssumptions();
        TaxAssumptions tax = assumptions.getTaxAssumptions();
        DeathScenarioAssumptions death = assumptions.getDeathScenarioAssumptions();
        return Arrays.<Object>asList(assumptions.getProjectionStartDate(), assumptions.getProjectionLengthYears(),
                economic.getExpectedAnnualInvestmentReturn(), economic.getGeneralInflationRate(),
                economic.getHealthcareInflationRate(), economic.getSocialSecurityColaRate(),
                tax.getFederalTaxBracketGrowthRate(), tax.getStandardDeductionGrowthRate(),
                tax.getFutureFederalMarginalRateAdjustment(), tax.getFutureFederalMarginalRateEffectiveYear(),
                tax.getStateIncomeTaxRate(), tax.getLocalIncomeTaxRate(), tax.getEstimatedHeirTaxRateOnTaxDeferredAssets(),
                death.getDeathScenario(), death.getDeathYear(), death.getSurvivorClaimingAge(),
                death.getPostDeathExpenseFactor()).stream()
                .map(value -> value instanceof BigDecimal decimal ? decimal.stripTrailingZeros() : value)
                .toList();
    }

    private BigDecimal percent(TextField field, String name) {
        return read(field, name, () -> parsePercent(field.getText()));
    }

    private static <T> T read(Control control, String name, Supplier<T> parser) {
        try {
            return parser.get();
        }
        catch (RuntimeException exception) {
            throw new InputException(control, name + ": "
                    + (exception instanceof NumberFormatException ? "Enter a valid number."
                    : exception.getMessage() == null ? "Enter a valid value." : exception.getMessage()));
        }
    }

    private static final class InputException extends IllegalArgumentException {
        private final Control control;

        private InputException(Control control, String message) {
            super(message);
            this.control = control;
        }
    }

    private void updateDeathScenarioFields() {

        boolean deathScenarioActive =
                deathScenarioComboBox.getValue()
                        != null
                        && deathScenarioComboBox.getValue()
                        != DeathScenario.BOTH_SURVIVE;

        deathYearField.setDisable(
                !deathScenarioActive);

        survivorClaimingAgeComboBox.setDisable(
                !deathScenarioActive);

        postDeathExpenseFactorField.setDisable(
                !deathScenarioActive);
    }

    private BigDecimal parsePercent(
            String text) {

        return new BigDecimal(
                text.trim())
                .divide(
                        new BigDecimal("100"));
    }

    private String toPercent(
            BigDecimal rate) {

        return rate
                .multiply(
                        new BigDecimal("100"))
                .stripTrailingZeros()
                .toPlainString();
    }

    public void setOnPlanChanged(
            Runnable onPlanChanged) {

        this.onPlanChanged =
                onPlanChanged;
    }

    public void setOnOpeningRmdRequested(
            Runnable onOpeningRmdRequested) {

        this.onOpeningRmdRequested = onOpeningRmdRequested;
    }

    private void requestOpeningRmdInformation() {

        if (onOpeningRmdRequested != null) {
            onOpeningRmdRequested.run();
        }
    }

    private void updateOpeningRmdButtonVisibility() {

        boolean required = currentPlan != null
                && new OpeningRmdWorkflowService().isRequired(currentPlan);

        openingRmdButton.setVisible(required);
        openingRmdButton.setManaged(required);
    }

    private void notifyPlanChanged() {

        if (onPlanChanged != null) {
            onPlanChanged.run();
        }
    }
}
