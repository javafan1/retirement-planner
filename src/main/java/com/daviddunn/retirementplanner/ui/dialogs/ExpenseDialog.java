package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;

public class ExpenseDialog extends Dialog<Expense> {

    private final TextField descriptionField;
    private final TextField annualAmountField;

    public ExpenseDialog(Expense expense) {

        if (expense == null) {
            setTitle("Add Expense");
            setHeaderText("Enter expense information.");
        } else {
            setTitle("Edit Expense");
            setHeaderText("Update expense information.");
        }

        descriptionField = new TextField();
        annualAmountField = new TextField();

        /*
         * Populate fields when editing.
         */
        if (expense != null) {

            descriptionField.setText(
                    expense.getDescription());

            annualAmountField.setText(
                    expense.getAnnualAmount()
                            .toPlainString());
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
                row);

        getDialogPane().setContent(grid);

        getDialogPane()
                .getButtonTypes()
                .addAll(
                        ButtonType.OK,
                        ButtonType.CANCEL);

        setResultConverter(button -> {

            if (button != ButtonType.OK) {
                return null;
            }

            String description =
                    descriptionField
                            .getText()
                            .trim();

            BigDecimal annualAmount =
                    new BigDecimal(
                            annualAmountField
                                    .getText()
                                    .trim());

            return new Expense(
                    description,
                    annualAmount);
        });
    }
}