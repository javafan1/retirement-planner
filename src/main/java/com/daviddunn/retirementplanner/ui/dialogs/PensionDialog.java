package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.ui.controls.InputHelp;
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

        InputHelp.install(ownershipCombo, "Person whose pension this is. Their death determines when the separate survivor benefit, if provided, replaces the owner's benefit.");
        InputHelp.install(startDatePicker, "Date pension payments begin. Income is counted for active calendar months; this date also establishes the base year for this pension's COLA.");
        InputHelp.install(endDatePicker, "Optional last date of pension payments. Leave blank for no scheduled termination; death and any survivor benefit still affect modeled income.");
        InputHelp.install(monthlyBenefitField, "Gross monthly pension benefit in dollars at the pension start, before income taxes. The projection applies this pension's COLA and counts active months.");
        InputHelp.install(survivorMonthlyBenefitField, "Monthly dollar benefit payable to the surviving spouse after the pension owner's death, before COLA. Enter an amount, not a percentage; blank means no survivor benefit.");
        InputHelp.install(colaRateField, "Annual pension COLA as a decimal: enter 0.02 for 2%, or 0 for no increase. Compounded from the pension start year for both owner and survivor benefits; independent of General Inflation.");
        InputHelp.linkGridLabels(grid);
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