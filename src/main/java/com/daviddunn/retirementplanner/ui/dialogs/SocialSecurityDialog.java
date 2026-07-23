package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SocialSecurityDialog
        extends Dialog<SocialSecurityIncome> {

    private final TextField nameField;
    private final ComboBox<AccountOwnership> ownershipCombo;
    private final DatePicker startDatePicker;
    private final TextField fraBenefitField;
    private final ComboBox<Integer> claimingAgeCombo;
    private final TextField colaRateField;

    public SocialSecurityDialog(
            SocialSecurityIncome socialSecurity) {

        if (socialSecurity == null) {
            setTitle("Add Social Security");
            setHeaderText("Enter Social Security information.");
        } else {
            setTitle("Edit Social Security");
            setHeaderText("Update Social Security information.");
        }

        nameField = new TextField();

        ownershipCombo = new ComboBox<>();
        ownershipCombo.getItems().addAll(
                AccountOwnership.PRIMARY,
                AccountOwnership.SPOUSE);
        ownershipCombo.getSelectionModel().selectFirst();

        startDatePicker = new DatePicker();

        fraBenefitField = new TextField();

        claimingAgeCombo = new ComboBox<>();
        claimingAgeCombo.getItems().addAll(
                62, 63, 64, 65, 66, 67, 68, 69, 70);
        claimingAgeCombo.setValue(67);

        colaRateField = new TextField("0.025");

        if (socialSecurity != null) {

            nameField.setText(
                    socialSecurity.getName());

            ownershipCombo.setValue(
                    socialSecurity.getOwnership());

            startDatePicker.setValue(
                    socialSecurity.getStartDate());

            fraBenefitField.setText(
                    socialSecurity
                            .getFullRetirementMonthlyBenefit()
                            .toPlainString());

            claimingAgeCombo.setValue(
                    socialSecurity.getClaimingAge());

            colaRateField.setText(
                    socialSecurity
                            .getAnnualColaRate()
                            .toPlainString());
        }

        GridPane grid = new GridPane();

        grid.setPadding(new Insets(15));
        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        grid.add(new Label("Name:"), 0, row);
        grid.add(nameField, 1, row++);

        grid.add(new Label("Owner:"), 0, row);
        grid.add(ownershipCombo, 1, row++);

        grid.add(new Label("Benefit Start Date:"), 0, row);
        grid.add(startDatePicker, 1, row++);

        grid.add(new Label("FRA Monthly Benefit:"), 0, row);
        grid.add(fraBenefitField, 1, row++);

        grid.add(new Label("Claiming Age:"), 0, row);
        grid.add(claimingAgeCombo, 1, row++);

        grid.add(new Label("Annual COLA Rate:"), 0, row);
        grid.add(colaRateField, 1, row);

        getDialogPane().setContent(grid);

        getDialogPane().getButtonTypes().addAll(
                ButtonType.OK,
                ButtonType.CANCEL);

        setResultConverter(button -> {

            if (button != ButtonType.OK) {
                return null;
            }

            String name =
                    nameField.getText().trim();

            AccountOwnership ownership =
                    ownershipCombo.getValue();

            LocalDate startDate =
                    startDatePicker.getValue();

            BigDecimal fraBenefit =
                    new BigDecimal(
                            fraBenefitField.getText().trim());

            int claimingAge =
                    claimingAgeCombo.getValue();

            BigDecimal colaRate =
                    new BigDecimal(
                            colaRateField.getText().trim());

            return new SocialSecurityIncome(
                    name,
                    ownership,
                    startDate,
                    null,
                    fraBenefit,
                    claimingAge,
                    colaRate);
        });
    }
}