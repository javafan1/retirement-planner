package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.controls.RequiredFieldSupport;
import com.daviddunn.retirementplanner.ui.wizard.WizardDialogSupport;
import com.daviddunn.retirementplanner.ui.wizard.WizardDialogSupport.Field;
import com.daviddunn.retirementplanner.ui.wizard.WizardDialogSupport.Kind;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import com.daviddunn.retirementplanner.domain.financial.ContractualPremiumPolicy;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ExpenseDialog extends Dialog<Expense> {

    private final boolean guidedCreation;
    private final ComboBox<ContractualPremiumPolicy.Insured> insuredField = new ComboBox<>();
    private final Label insuredLabel = new Label("Insured Person:");
    private final Label premiumNotice = new Label(com.daviddunn.retirementplanner.app.projection.ContractualPremiumDisclosures.SCOPE);
    private final Label validationMessage = new Label();
    private final Label fixedPremiumEscalation = new Label("Fixed nominal (no escalation)");

    private final TextField descriptionField;
    private final TextField annualAmountField;
    private final TextField volatilityField = new TextField("0");
    private final Label volatilityLabel = new Label("Annual Expense Volatility (%):");
    private final Label volatilityNotice = new Label();
    private String originalVolatilityText = "0";

    private final ComboBox<ExpenseType> expenseTypeComboBox;
    private final ComboBox<GrowthCategory> growthCategoryComboBox;

    private final DatePicker effectiveDatePicker;
    private final DatePicker endDatePicker;

    private final Label amountLabel;
    private final Label effectiveDateLabel;
    private final Label endDateLabel;

    private Expense result;

    private final Label growthCategoryLabel;
    private final List<RequiredFieldSupport.Attachment> requiredFieldAttachments = new ArrayList<>();



    public ExpenseDialog(Expense expense) {

        this(expense, false);
    }

    public ExpenseDialog(Expense expense, boolean guidedCreation) {

        this(expense, guidedCreation, null);
    }

    public ExpenseDialog(Expense expense, boolean guidedCreation,
            com.daviddunn.retirementplanner.domain.model.Household household) {

        this.guidedCreation = guidedCreation;

        if (expense == null) {
            setTitle("Add Expense");
            setHeaderText("Enter expense information.");
        }
        else {
            setTitle("Edit Expense");
            setHeaderText("Update expense information.");
        }

        descriptionField = new TextField();
        descriptionField.setPrefColumnCount(30);

        annualAmountField = new TextField();
        annualAmountField.setPrefColumnCount(12);
        volatilityField.setPrefColumnCount(10);
        volatilityField.setAccessibleText("Annual Expense Volatility (%)");
        volatilityNotice.setWrapText(true);
        volatilityNotice.setMaxWidth(330);
        volatilityNotice.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        if (expense != null) {
            originalVolatilityText = expense.getEffectiveAnnualExpenseVolatility().movePointRight(2).toPlainString();
            volatilityField.setText(originalVolatilityText);
        }

        expenseTypeComboBox = new ComboBox<>();
        expenseTypeComboBox.getItems().addAll(
                ExpenseType.values());
        if (household == null && (expense == null || !expense.isContractualInsurancePremium()))
            expenseTypeComboBox.getItems().remove(ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM);
        expenseTypeComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ExpenseType value) { return value == null ? "" : switch (value) {
                case RECURRING -> "Recurring";
                case ONE_TIME -> "One-Time";
                case CONTRACTUAL_INSURANCE_PREMIUM -> "Contractual Insurance Premium";
            }; }
            @Override public ExpenseType fromString(String value) { throw new UnsupportedOperationException(); }
        });
        insuredField.setId("premium-insured");
        insuredField.setAccessibleText("Premium insured person");
        insuredField.getItems().add(ContractualPremiumPolicy.Insured.PRIMARY);
        if (household != null && household.hasSpouse() || expense != null && expense.isContractualInsurancePremium()
                && expense.getContractualPremium().insured() == ContractualPremiumPolicy.Insured.SPOUSE)
            insuredField.getItems().add(ContractualPremiumPolicy.Insured.SPOUSE);
        insuredField.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ContractualPremiumPolicy.Insured insured) {
                if (insured == null) return "";
                String role=insured == ContractualPremiumPolicy.Insured.PRIMARY ? "Primary" : "Spouse";
                if (household == null) return role;
                var person=insured == ContractualPremiumPolicy.Insured.PRIMARY ? household.getPrimaryPerson() : household.getSpouse();
                return role + " — " + person.getFirstName();
            }
            @Override public ContractualPremiumPolicy.Insured fromString(String value) { throw new UnsupportedOperationException(); }
        });
        if (expense != null && expense.isContractualInsurancePremium()) insuredField.setValue(expense.getContractualPremium().insured());
        premiumNotice.setWrapText(true); premiumNotice.setMaxWidth(350);
        premiumNotice.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        validationMessage.setWrapText(true); validationMessage.setId("expense-validation");
        validationMessage.getStyleClass().add("wizard-field-error");
        validationMessage.setMaxWidth(470);
        validationMessage.managedProperty().bind(validationMessage.textProperty().isNotEmpty());
        validationMessage.visibleProperty().bind(validationMessage.managedProperty());
        expenseTypeComboBox.setValue(
                ExpenseType.RECURRING);

        growthCategoryComboBox = new ComboBox<>();
        growthCategoryComboBox.getItems().addAll(
                GrowthCategory.values());
        growthCategoryComboBox.setValue(
                GrowthCategory.GENERAL);
        growthCategoryComboBox.setAccessibleText("Recurring expense escalation");
        growthCategoryComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(GrowthCategory value) {
                return value == null ? "" : value.displayName();
            }
            @Override public GrowthCategory fromString(String value) {
                return java.util.Arrays.stream(GrowthCategory.values())
                        .filter(category -> category.displayName().equals(value)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Select an expense escalation policy."));
            }
        });

        effectiveDatePicker = new DatePicker();
        endDatePicker = new DatePicker();

        effectiveDatePicker.valueProperty().addListener(
                (obs, oldDate, newDate) -> {

                    if (expenseTypeComboBox.getValue()
                            == ExpenseType.ONE_TIME) {

                        endDatePicker.setValue(newDate);
                    }
                });

        growthCategoryLabel =
                new Label("Escalation:");

        amountLabel =
                new Label("Annual Amount:");

        effectiveDateLabel =
                new Label("Effective Date:");

        endDateLabel =
                new Label("End Date:");

        if (expense != null) {

            descriptionField.setText(
                    expense.getDescription());

            annualAmountField.setText(
                    expense.getAnnualAmount()
                            .toPlainString());

            expenseTypeComboBox.setValue(
                    expense.getExpenseType());

            growthCategoryComboBox.setValue(
                    expense.getGrowthCategory());

            effectiveDatePicker.setValue(
                    expense.getStartDate());

            endDatePicker.setValue(
                    expense.getEndDate());
        }

        expenseTypeComboBox
                .valueProperty()
                .addListener((obs, oldValue, newValue) ->
                        updateExpenseTypeControls(newValue));

        GridPane grid = new GridPane();

        grid.setPadding(new Insets(15));
        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        Label descriptionLabel = new Label("Description:");
        grid.add(
                descriptionLabel,
                0,
                row);

        grid.add(
                descriptionField,
                1,
                row++);

        Label expenseTypeLabel = new Label("Expense Type:");
        grid.add(
                expenseTypeLabel,
                0,
                row);

        grid.add(
                expenseTypeComboBox,
                1,
                row++);

        grid.add(
                amountLabel,
                0,
                row);

        grid.add(
                annualAmountField,
                1,
                row++);

        grid.add(
                growthCategoryLabel,
                0,
                row);

        grid.add(
                growthCategoryComboBox,
                1,
                row++);
        grid.add(fixedPremiumEscalation,1,row-1);

        grid.add(volatilityLabel, 0, row);
        grid.add(volatilityField, 1, row++);
        grid.add(volatilityNotice, 0, row++, 2, 1);
        grid.add(insuredLabel,0,row); grid.add(insuredField,1,row++);
        grid.add(premiumNotice,0,row++,2,1);

        grid.add(
                effectiveDateLabel,
                0,
                row);

        grid.add(
                effectiveDatePicker,
                1,
                row++);

        grid.add(
                endDateLabel,
                0,
                row);

        grid.add(
                endDatePicker,
                1,
                row++);
        grid.add(validationMessage,0,row++,2,1);
        grid.add(RequiredFieldSupport.createLegend(), 0, row, 2, 1);

        requiredFieldAttachments.add(RequiredFieldSupport.attach(descriptionLabel, descriptionField));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(expenseTypeLabel, expenseTypeComboBox));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(amountLabel, annualAmountField));
        var oneTime = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> expenseTypeComboBox.getValue() == ExpenseType.ONE_TIME || expenseTypeComboBox.getValue() == ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM,
                expenseTypeComboBox.valueProperty());
        requiredFieldAttachments.add(RequiredFieldSupport.attach(effectiveDateLabel, effectiveDatePicker, oneTime));
        requiredFieldAttachments.add(RequiredFieldSupport.attach(insuredLabel, insuredField, javafx.beans.binding.Bindings.createBooleanBinding(
                () -> expenseTypeComboBox.getValue() == ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM, expenseTypeComboBox.valueProperty())));
        InputHelp.install(insuredField,"Only this named person's life controls premium termination. A modeled death is January 1 of the selected year; no premium is due that year. This does not create a death benefit.");

        InputHelp.install(expenseTypeComboBox, "Recurring expenses repeat in active calendar years. One Time records a purchase in its purchase year, uses General Inflation, and is not reduced by the post-death expense factor.");
        InputHelp.install(annualAmountField, "Dollars in the projection's opening-year spending base: annual spending for Recurring, or the single purchase amount for One Time. Later-starting expenses use that same base. Inflation applies only under the selected escalation policy.");
        InputHelp.install(growthCategoryComboBox, "General Inflation and Healthcare Inflation each use the corresponding existing plan assumption once. No Inflation keeps the entered recurring amount fixed in nominal dollars; existing active-year, opening-year proration and survivor adjustments still apply. One-time purchases retain General Inflation and hide this selector.");
        InputHelp.install(effectiveDatePicker, "First active date for recurring spending; blank means no start restriction. For a one-time expense, a purchase date is required. Recurring expenses are included by calendar-year overlap, with opening-year proration from the projection start.");
        InputHelp.install(endDatePicker, "Optional last active date for a recurring expense; blank means no scheduled end. A calendar year is included when the expense overlaps it. One-time expenses instead use their purchase date.");
        InputHelp.install(volatilityField, "Annual relative standard deviation around this recurring expense's expected escalated annual amount. "
                + "For example, 20% is not a maximum increase or decrease; actual variation can exceed it. "
                + "Supported generated Monte Carlo analyses and strategy comparisons use independent annual lognormal expense levels with an unbounded positive tail. "
                + "This is separate from investment returns and inflation, and shocks do not compound. "
                + "One-time expenses and ordinary deterministic projections are unaffected.");
        annualAmountField.textProperty().addListener((observable, before, after) -> updateVolatilityAvailability());
        InputHelp.linkGridLabels(grid);
        getDialogPane().setContent(grid);
        setOnHidden(event -> {
            requiredFieldAttachments.forEach(RequiredFieldSupport.Attachment::close);
            requiredFieldAttachments.clear();
        });

        updateExpenseTypeControls(
                expenseTypeComboBox.getValue());

        ButtonType okButtonType =
                ButtonType.OK;

        getDialogPane()
                .getButtonTypes()
                .addAll(
                        okButtonType,
                        ButtonType.CANCEL);

        Button okButton =
                (Button) getDialogPane()
                        .lookupButton(okButtonType);

        okButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {

                    if (!guidedCreation && !validateAndBuildExpense()) {
                        event.consume();
                    }
                });

        setResultConverter(button ->

                button == okButtonType
                        ? result
                        : null);

        if (guidedCreation) {
            WizardDialogSupport.install(this, java.util.List.of(
                    new Field("Description", descriptionField, Kind.TEXT, true),
                    new Field("Expense Type", expenseTypeComboBox, Kind.CHOICE, true),
                    new Field("Amount", annualAmountField, Kind.NUMBER, true),
                    new Field("Escalation", growthCategoryComboBox, Kind.CHOICE, true),
                    new Field("Annual Expense Volatility (%)", volatilityField, Kind.NUMBER, true),
                    new Field("Effective Date", effectiveDatePicker, Kind.DATE, false),
                    new Field("End Date", endDatePicker, Kind.DATE, false)), () -> {
                        if (!validateAndBuildExpense()) throw new IllegalArgumentException("Please complete the expense.");
                        return result;
                    });
            updateExpenseTypeControls(expenseTypeComboBox.getValue());
            WizardDialogSupport.guidance(this, "Amount is required in the projection's opening-year spending base: "
                    + "dollars/year for Recurring, single-purchase dollars for One Time. There is no monthly conversion. "
                    + "Recurring dates are optional restrictions on active calendar years. A one-time purchase date is required; "
                    + "this editor uses General inflation and the purchase is not repeated annually.");
        }

        if (!guidedCreation && (household != null || expense != null && expense.isContractualInsurancePremium())) {
            var scroll = new ScrollPane(grid);
            scroll.setFitToWidth(true); scroll.setPrefViewportHeight(440);
            getDialogPane().setContent(scroll);
            getDialogPane().setPrefWidth(610);
            setResizable(true);
        }
        updateExpenseTypeControls(expenseTypeComboBox.getValue());

    }

    private boolean validateAndBuildExpense() {

        try {
            validationMessage.setText("");

            String description =
                    descriptionField
                            .getText()
                            .trim();

            if (description.isBlank()) {

                showValidationError(
                        "Description is required.");

                return false;
            }

            BigDecimal annualAmount =
                    new BigDecimal(
                            annualAmountField
                                    .getText()
                                    .trim());

            LocalDate effectiveDate =
                    effectiveDatePicker.getValue();
            if (expenseTypeComboBox.getValue() == ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM) {
                try { effectiveDatePicker.commitValue(); endDatePicker.commitValue(); }
                catch (java.time.format.DateTimeParseException invalid) { showValidationError("Enter valid premium dates using the calendar format."); return false; }
                effectiveDate = effectiveDatePicker.getValue();
            }

            LocalDate endDate;

            if (expenseTypeComboBox.getValue()
                    == ExpenseType.ONE_TIME) {

                if (effectiveDate == null) {

                    showValidationError(
                            "A purchase date is required.");

                    return false;
                }

                endDate = effectiveDate;

            } else {

                endDate =
                        endDatePicker.getValue();

                if (effectiveDate != null &&
                        endDate != null &&
                        endDate.isBefore(effectiveDate)) {

                    showValidationError(
                            "The end date cannot be before the effective date.");

                    return false;
                }
            }

            GrowthCategory growthCategory =
                    expenseTypeComboBox.getValue() ==
                            ExpenseType.ONE_TIME
                            ? GrowthCategory.GENERAL
                            : growthCategoryComboBox.getValue();

            BigDecimal volatility = BigDecimal.ZERO;
            if (expenseTypeComboBox.getValue() == ExpenseType.RECURRING) {
                String text = volatilityField.getText().trim();
                BigDecimal percent = new BigDecimal(text);
                if (percent.signum() < 0 || percent.compareTo(new BigDecimal("100")) > 0
                        || (!text.equals(originalVolatilityText) && percent.scale() > 2)) {
                    showValidationError("Annual expense volatility must be 0–100%, with up to two decimal places for new values.");
                    return false;
                }
                volatility = percent.movePointLeft(2);
                if (annualAmount.signum() < 0 && volatility.signum() > 0) {
                    showValidationError("Negative legacy expenses cannot have nonzero volatility.");
                    return false;
                }
            }

            result =
                    new Expense(
                            description,
                            annualAmount,
                            growthCategory,
                            effectiveDate,
                            endDate,
                            expenseTypeComboBox.getValue(), volatility,
                            expenseTypeComboBox.getValue() == ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM
                                    ? new ContractualPremiumPolicy(ContractualPremiumPolicy.Model.INSURED_ALIVE_FIXED_NOMINAL_MONTHS_V1,insuredField.getValue()) : null);
            return true;

        }
        catch (NumberFormatException ex) {

            showValidationError(
                    "Please enter a valid amount.");

            return false;
        }
        catch (IllegalArgumentException | NullPointerException ex) {
            showValidationError(ex.getMessage() == null ? "Complete the required premium fields." : ex.getMessage());
            return false;
        }
    }

    private void showValidationError(
            String message) {

        if (guidedCreation) {
            throw new IllegalArgumentException(message);
        }

        validationMessage.setText(message);
    }

    private void updateExpenseTypeControls(
            ExpenseType expenseType) {

        boolean oneTime =
                expenseType == ExpenseType.ONE_TIME;
        boolean premium = expenseType == ExpenseType.CONTRACTUAL_INSURANCE_PREMIUM;
        insuredLabel.setVisible(premium); insuredLabel.setManaged(premium);
        insuredField.setVisible(premium); insuredField.setManaged(premium);
        premiumNotice.setVisible(premium); premiumNotice.setManaged(premium);
        if (premium) growthCategoryComboBox.setValue(GrowthCategory.NONE);

        amountLabel.setMinWidth(130);

        amountLabel.setText(
                oneTime
                        ? "Purchase Amount:"
                        : "Annual Amount:");

        effectiveDateLabel.setText(
                oneTime
                        ? "Purchase Date:"
                        : premium ? "Premium Start Date:" : "Effective Date:");
        endDateLabel.setText(premium ? "Premium End Date:" : "End Date:");
        InputHelp.install(annualAmountField, premium
                ? "Annualized fixed nominal premium rate in dollars. Each eligible calendar month counts once, including a partial month; a full year uses the full annual rate. This is not a carrier billing schedule."
                : "Dollars in the projection's opening-year spending base: annual spending for Recurring, or the single purchase amount for One Time. Later-starting expenses use that same base. Inflation applies only under the selected escalation policy.");
        InputHelp.install(effectiveDatePicker, premium
                ? "Required first eligible premium date. Payments begin no earlier than the projection opening. Partial eligible months count as whole months."
                : "First active date for recurring spending; blank means no start restriction. For a one-time expense, a purchase date is required. Recurring expenses are included by calendar-year overlap, with opening-year proration from the projection start.");
        InputHelp.install(endDatePicker, premium
                ? "Optional inclusive last premium payment date, independent of any separately configured death benefit's coverage. A partial eligible final month counts as a whole month."
                : "Optional last active date for a recurring expense; blank means no scheduled end. A calendar year is included when the expense overlaps it. One-time expenses instead use their purchase date.");

        /*
         * One-time expenses use a single purchase date.
         * Internally we keep the end date synchronized
         * with the purchase date.
         */
        if (oneTime) {

            endDatePicker.setValue(
                    effectiveDatePicker.getValue());
        }

        endDateLabel.setVisible(!oneTime);
        endDateLabel.setManaged(!oneTime);

        endDatePicker.setVisible(!oneTime);
        endDatePicker.setManaged(!oneTime);

        growthCategoryLabel.setVisible(!oneTime);
        growthCategoryLabel.setManaged(!oneTime);
        growthCategoryComboBox.setDisable(premium);

        growthCategoryComboBox.setVisible(!oneTime);
        growthCategoryComboBox.setManaged(!oneTime);
        if (premium) { growthCategoryComboBox.setVisible(false); growthCategoryComboBox.setManaged(false); }
        fixedPremiumEscalation.setVisible(premium); fixedPremiumEscalation.setManaged(premium);
        var save=getDialogPane().lookupButton(ButtonType.OK);
        if (save instanceof Button button) button.setText(premium ? "Save" : "OK");
        if (oneTime && !volatilityField.getText().equals("0")) {
            volatilityField.setText("0");
            volatilityNotice.setText("Annual expense volatility was cleared: one-time expenses do not support it.");
        }
        volatilityLabel.setVisible(!oneTime && !premium);
        volatilityLabel.setManaged(!oneTime && !premium);
        volatilityField.setVisible(!oneTime && !premium);
        volatilityField.setManaged(!oneTime && !premium);
        updateVolatilityAvailability();
    }

    private void updateVolatilityAvailability() {
        boolean negative = false;
        try { negative = new BigDecimal(annualAmountField.getText().trim()).signum() < 0; }
        catch (NumberFormatException ignored) { /* Amount validation remains authoritative on Save. */ }
        // Keep an imported/just-edited positive value editable so the user can clear it.
        boolean zero = false;
        try { zero = new BigDecimal(volatilityField.getText().trim()).signum() == 0; }
        catch (NumberFormatException ignored) { /* Save reports the invalid value. */ }
        volatilityField.setDisable(negative && zero);
        if (negative) volatilityNotice.setText("Negative legacy expenses cannot have nonzero annual expense volatility. Enter 0% before using a negative amount.");
        else if (!volatilityNotice.getText().startsWith("Annual expense volatility was cleared")) volatilityNotice.setText("");
        volatilityNotice.setVisible(!volatilityNotice.getText().isEmpty());
        volatilityNotice.setManaged(volatilityNotice.isVisible());
    }



}
