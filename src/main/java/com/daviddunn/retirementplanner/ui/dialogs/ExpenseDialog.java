package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.financial.GrowthCategory;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ExpenseDialog extends Dialog<Expense> {

    private final TextField descriptionField;
    private final TextField annualAmountField;

    private final ComboBox<GrowthCategory> growthCategoryComboBox;

    private final DatePicker effectiveDatePicker;
    private final DatePicker endDatePicker;

    private Expense result;

    public ExpenseDialog(Expense expense) {

        if (expense == null) {
            setTitle("Add Expense");
            setHeaderText("Enter expense information.");
        } else {
            setTitle("Edit Expense");
            setHeaderText("Update expense information.");
        }

        descriptionField = new TextField();
        descriptionField.setPrefColumnCount(30);

        annualAmountField = new TextField();
        annualAmountField.setPrefColumnCount(12);

        growthCategoryComboBox = new ComboBox<>();
        growthCategoryComboBox.getItems().addAll(GrowthCategory.values());
        growthCategoryComboBox.setValue(GrowthCategory.GENERAL);

        effectiveDatePicker = new DatePicker();
        endDatePicker = new DatePicker();

        /*
         * Populate fields when editing.
         */
        if (expense != null) {

            descriptionField.setText(
                    expense.getDescription());

            annualAmountField.setText(
                    expense.getAnnualAmount()
                            .toPlainString());

            growthCategoryComboBox.setValue(
                    expense.getGrowthCategory());

            effectiveDatePicker.setValue(
                    expense.getStartDate());

            endDatePicker.setValue(
                    expense.getEndDate());
        }

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
                new Label("Annual Amount:"),
                0,
                row);

        grid.add(
                annualAmountField,
                1,
                row++);

        grid.add(
                new Label("Growth Category:"),
                0,
                row);

        grid.add(
                growthCategoryComboBox,
                1,
                row++);

        grid.add(
                new Label("Effective Date:"),
                0,
                row);

        grid.add(
                effectiveDatePicker,
                1,
                row++);

        grid.add(
                new Label("End Date:"),
                0,
                row);

        grid.add(
                endDatePicker,
                1,
                row++);

        getDialogPane().setContent(grid);

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

        setResultConverter(button -> {

            if (button == okButtonType) {
                return result;
            }

            return null;
        });
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

            LocalDate endDate =
                    endDatePicker.getValue();

            if (effectiveDate != null &&
                    endDate != null &&
                    endDate.isBefore(effectiveDate)) {

                showValidationError(
                        "The end date cannot be before the effective date.");

                return false;
            }

            result =
                    new Expense(
                            description,
                            annualAmount,
                            growthCategoryComboBox.getValue(),
                            effectiveDate,
                            endDate);

            return true;

        } catch (NumberFormatException ex) {

            showValidationError(
                    "Please enter a valid annual amount.");

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

        alert.setContentText(message);

        alert.showAndWait();
    }
}