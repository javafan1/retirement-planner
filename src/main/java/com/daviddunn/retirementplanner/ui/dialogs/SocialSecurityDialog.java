package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityBenefitStartDateCalculator;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.Person;
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
    private final Household household;
    private final Label startDateValidationLabel;

    public SocialSecurityDialog(
            SocialSecurityIncome socialSecurity,
            int benefitValuationYear,
            Household household) {

        this.household = household;

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
        startDatePicker.setEditable(false);
        startDatePicker.setDisable(true);

        startDateValidationLabel = new Label();
        startDateValidationLabel.setWrapText(true);

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

        Label startDateHelp = new Label(
                "Derived from the owner's DOB and claiming age.");
        startDateHelp.setWrapText(true);
        grid.add(startDateHelp, 1, row++);

        grid.add(startDateValidationLabel, 1, row++);

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

        ownershipCombo.valueProperty().addListener(
                (observable, oldValue, newValue) ->
                        updateDerivedStartDate());

        claimingAgeCombo.valueProperty().addListener(
                (observable, oldValue, newValue) ->
                        updateDerivedStartDate());

        updateDerivedStartDate();

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

    private void updateDerivedStartDate() {

        AccountOwnership ownership =
                ownershipCombo.getValue();

        Integer claimingAge =
                claimingAgeCombo.getValue();

        LocalDate startDate = null;

        if (ownership != null && claimingAge != null) {
            startDate = SocialSecurityBenefitStartDateCalculator
                    .calculate(
                            getPerson(ownership),
                            claimingAge)
                    .orElse(null);
        }

        startDatePicker.setValue(startDate);

        boolean missingBirthDate = startDate == null;

        startDateValidationLabel.setText(
                missingBirthDate
                        ? "Enter the selected owner's birth date before saving Social Security."
                        : "");

        getDialogPane()
                .lookupButton(ButtonType.OK)
                .setDisable(missingBirthDate);
    }

    private Person getPerson(
            AccountOwnership ownership) {

        return switch (ownership) {
            case PRIMARY -> household.getPrimaryPerson();
            case SPOUSE -> household.getSpouse();
            case JOINT -> throw new IllegalArgumentException(
                    "Social Security cannot have joint ownership.");
        };
    }
}
