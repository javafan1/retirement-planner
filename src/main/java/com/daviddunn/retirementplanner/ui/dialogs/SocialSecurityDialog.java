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
    private final int benefitValuationYear;
    private final int planProjectionStartYear;
    private final CheckBox useTodaysDollarConventionCheckBox;

    public SocialSecurityDialog(
            SocialSecurityIncome socialSecurity,
            int benefitValuationYear) {

        this.planProjectionStartYear = benefitValuationYear;
        this.benefitValuationYear = socialSecurity != null
                ? socialSecurity.getBenefitValuationYear()
                : benefitValuationYear;

        this.useTodaysDollarConventionCheckBox =
                new CheckBox(
                        "Use Today's-Dollar Convention");

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

        grid.add(new Label(
                "FRA Monthly Benefit (Today's Dollars):"), 0, row);
        grid.add(fraBenefitField, 1, row++);

        Label valuationYearLabel = new Label(
                "Benefit valuation year: "
                        + this.benefitValuationYear);
        grid.add(valuationYearLabel, 0, row, 2, 1);
        row++;

        grid.add(new Label("Claiming Age:"), 0, row);
        grid.add(claimingAgeCombo, 1, row++);

        Label help = new Label(
                "Enter the FRA benefit in today's dollars. "
                        + "Future benefits are projected using the "
                        + "Social Security COLA assumption.");
        help.setWrapText(true);
        grid.add(help, 0, row, 2, 1);
        row++;

        if (socialSecurity != null
                && this.benefitValuationYear
                != planProjectionStartYear) {

            Label conventionNote = new Label(
                    "This legacy source does not use the plan's "
                            + "today's-dollar valuation year ("
                            + planProjectionStartYear + ").");
            conventionNote.setWrapText(true);

            grid.add(conventionNote, 0, row, 2, 1);
            row++;

            grid.add(
                    useTodaysDollarConventionCheckBox,
                    0,
                    row,
                    2,
                    1);
        }

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

            int resolvedValuationYear =
                    useTodaysDollarConventionCheckBox.isSelected()
                            ? planProjectionStartYear
                            : this.benefitValuationYear;

            return new SocialSecurityIncome(
                    name,
                    ownership,
                    startDate,
                    null,
                    fraBenefit,
                    claimingAge,
                    socialSecurity != null
                            ? socialSecurity.getAnnualColaRate()
                            : BigDecimal.ZERO,
                    resolvedValuationYear);
        });
    }
}
