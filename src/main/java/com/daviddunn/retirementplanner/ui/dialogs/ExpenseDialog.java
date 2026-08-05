package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.ExpenseType;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ExpenseDialog extends Dialog<Expense> {

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

                    if (!validateAndBuildExpense()) {
                        event.consume();
                    }
                });

        setResultConverter(button ->

                button == okButtonType
                        ? result
                        : null);


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
                        ? "Purchase Amount:"
                        : "Annual Amount:");

        effectiveDateLabel.setText(
                oneTime
                        ? "Purchase Date:"
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