package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PensionDialog extends Dialog<Pension> {

    private final TextField nameField;
    private final ComboBox<AccountOwnership> ownershipCombo;
    private final DatePicker startDatePicker;
    private final DatePicker endDatePicker;
    private final TextField monthlyBenefitField;
    private final TextField survivorMonthlyBenefitField;
    private final TextField colaRateField;

    public PensionDialog(Pension pension) {

        if (pension == null) {
            setTitle("Add Pension");
            setHeaderText("Enter pension information.");
        } else {
            setTitle("Edit Pension");
            setHeaderText("Update pension information.");
        }

        nameField = new TextField();

        ownershipCombo = new ComboBox<>();

        /*
         * Income sources belong to an individual,
         * so JOINT is intentionally excluded.
         */
        ownershipCombo.getItems().addAll(
                AccountOwnership.PRIMARY,
                AccountOwnership.SPOUSE);

        ownershipCombo.getSelectionModel().selectFirst();

        startDatePicker = new DatePicker();

        endDatePicker = new DatePicker();

        monthlyBenefitField = new TextField();

        survivorMonthlyBenefitField =
                new TextField();

        /*
         * COLA is stored as a decimal:
         *
         * 0.0   = no COLA
         * 0.02  = 2%
         * 0.025 = 2.5%
         */
        colaRateField =
                new TextField("0.0");

        /*
         * Populate controls when editing
         * an existing pension.
         */
        if (pension != null) {

            nameField.setText(
                    pension.getName());

            ownershipCombo.setValue(
                    pension.getOwnership());

            startDatePicker.setValue(
                    pension.getStartDate());

            endDatePicker.setValue(
                    pension.getEndDate());

            monthlyBenefitField.setText(
                    pension.getMonthlyBenefit()
                            .toPlainString());

            if (pension.getSurvivorMonthlyBenefit()
                    != null) {

                survivorMonthlyBenefitField.setText(
                        pension
                                .getSurvivorMonthlyBenefit()
                                .toPlainString());
            }

            colaRateField.setText(
                    pension.getAnnualColaRate()
                            .toPlainString());
        }

        GridPane grid =
                new GridPane();

        grid.setPadding(
                new Insets(15));

        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        grid.add(
                new Label("Name:"),
                0,
                row);

        grid.add(
                nameField,
                1,
                row++);

        grid.add(
                new Label("Owner:"),
                0,
                row);

        grid.add(
                ownershipCombo,
                1,
                row++);

        grid.add(
                new Label("Start Date:"),
                0,
                row);

        grid.add(
                startDatePicker,
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

        grid.add(
                new Label("Monthly Benefit:"),
                0,
                row);

        grid.add(
                monthlyBenefitField,
                1,
                row++);

        grid.add(
                new Label(
                        "Survivor Monthly Benefit:"),
                0,
                row);

        grid.add(
                survivorMonthlyBenefitField,
                1,
                row++);

        grid.add(
                new Label("Annual COLA Rate:"),
                0,
                row);

        grid.add(
                colaRateField,
                1,
                row);

        getDialogPane()
                .setContent(grid);

        getDialogPane()
                .getButtonTypes()
                .addAll(
                        ButtonType.OK,
                        ButtonType.CANCEL);

        setResultConverter(button -> {

            if (button != ButtonType.OK) {
                return null;
            }

            String name =
                    nameField
                            .getText()
                            .trim();

            AccountOwnership ownership =
                    ownershipCombo.getValue();

            LocalDate startDate =
                    startDatePicker.getValue();

            LocalDate endDate =
                    endDatePicker.getValue();

            BigDecimal monthlyBenefit =
                    new BigDecimal(
                            monthlyBenefitField
                                    .getText()
                                    .trim());

            /*
             * Survivor benefit is optional.
             *
             * Blank = this pension has no
             * survivor benefit.
             */
            String survivorText =
                    survivorMonthlyBenefitField
                            .getText()
                            .trim();

            BigDecimal survivorMonthlyBenefit =
                    survivorText.isEmpty()
                            ? null
                            : new BigDecimal(
                            survivorText);

            BigDecimal annualColaRate =
                    new BigDecimal(
                            colaRateField
                                    .getText()
                                    .trim());

            return new Pension(
                    name,
                    ownership,
                    startDate,
                    endDate,
                    monthlyBenefit,
                    annualColaRate,
                    survivorMonthlyBenefit);
        });
    }
}