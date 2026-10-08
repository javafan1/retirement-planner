package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
import com.daviddunn.retirementplanner.ui.wizard.WizardDialogSupport;
import com.daviddunn.retirementplanner.ui.wizard.WizardDialogSupport.Field;
import com.daviddunn.retirementplanner.ui.wizard.WizardDialogSupport.Kind;
import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ExpenseDialog extends Dialog<Expense> {

    private final boolean guidedCreation;

    private final TextField descriptionField;
    private final TextField annualAmountField;

    private final ComboBox<ExpenseType> expenseTypeComboBox;
    private final ComboBox<GrowthCategory> growthCategoryComboBox;

    private final DatePicker effectiveDatePicker;
    private final DatePicker endDatePicker;

    private final Label amountLabel;
    private final Label effectiveDateLabel;
    private final Label endDateLabel;

    private Expense result;

    private final Label growthCategoryLabel;



    public ExpenseDialog(Expense expense) {

        this(expense, false);
    }

    public ExpenseDialog(Expense expense, boolean guidedCreation) {

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

        expenseTypeComboBox = new ComboBox<>();
        expenseTypeComboBox.getItems().addAll(
                ExpenseType.values());
        expenseTypeComboBox.setValue(
                ExpenseType.RECURRING);

        growthCategoryComboBox = new ComboBox<>();
        growthCategoryComboBox.getItems().addAll(
                GrowthCategory.values());
        growthCategoryComboBox.setValue(
                GrowthCategory.GENERAL);

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
                new Label("Growth Category:");

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

        grid.add(
                new Label("Description:"),
                0,
                row);

        grid.add(
                descriptionField,
                1,
                row++);

        grid.add(
                new Label("Expense Type:"),
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

        InputHelp.install(expenseTypeComboBox, "Recurring expenses repeat in active calendar years. One Time records a purchase in its purchase year, uses General Inflation, and is not reduced by the post-death expense factor.");
        InputHelp.install(annualAmountField, "Dollars in the projection's opening-year spending base: annual spending for Recurring, or the single purchase amount for One Time. Future amounts grow from that base using the applicable inflation assumption.");
        InputHelp.install(growthCategoryComboBox, "Select General or Healthcare Inflation for a recurring expense. One-time purchases always use General Inflation, so this choice is hidden for them.");
        InputHelp.install(effectiveDatePicker, "First active date for recurring spending; blank means no start restriction. For a one-time expense, a purchase date is required. Recurring expenses are included by calendar-year overlap, with opening-year proration from the projection start.");
        InputHelp.install(endDatePicker, "Optional last active date for a recurring expense; blank means no scheduled end. A calendar year is included when the expense overlaps it. One-time expenses instead use their purchase date.");
        InputHelp.linkGridLabels(grid);
        getDialogPane().setContent(grid);

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
                    new Field("Growth Category", growthCategoryComboBox, Kind.CHOICE, true),
                    new Field("Effective Date", effectiveDatePicker, Kind.DATE, false),
                    new Field("End Date", endDatePicker, Kind.DATE, false)), () -> {
                        if (!validateAndBuildExpense()) throw new IllegalArgumentException("Please complete the expense.");
                        return result;
                    });
            updateExpenseTypeControls(expenseTypeComboBox.getValue());
        }

    }

    private boolean validateAndBuildExpense() {

        try {

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

            result =
                    new Expense(
                            description,
                            annualAmount,
                            growthCategory,
                            effectiveDate,
                            endDate,
                            expenseTypeComboBox.getValue());
            return true;

        }
        catch (NumberFormatException ex) {

            showValidationError(
                    "Please enter a valid amount.");

            return false;
        }
    }

    private void showValidationError(
            String message) {

        if (guidedCreation) {
            throw new IllegalArgumentException(message);
        }

        Alert alert =
                new Alert(Alert.AlertType.ERROR);

        alert.setTitle(
                "Invalid Expense");

        alert.setHeaderText(
                "Unable to save expense");

        alert.setContentText(
                message);

        alert.showAndWait();
    }

    private void updateExpenseTypeControls(
            ExpenseType expenseType) {

        boolean oneTime =
                expenseType == ExpenseType.ONE_TIME;

        amountLabel.setMinWidth(130);

        amountLabel.setText(
                oneTime
                        ? (guidedCreation ? "Purchase Amount *" : "Purchase Amount:")
                        : (guidedCreation ? "Annual Amount *" : "Annual Amount:"));

        effectiveDateLabel.setText(
                oneTime
                        ? (guidedCreation ? "Purchase Date *" : "Purchase Date:")
                        : "Effective Date:");

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

        growthCategoryComboBox.setVisible(!oneTime);
        growthCategoryComboBox.setManaged(!oneTime);
    }



}
