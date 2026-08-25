package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.DeathScenario;
import com.daviddunn.retirementplanner.domain.model.DeathScenarioAssumptions;
import com.daviddunn.retirementplanner.domain.model.EconomicAssumptions;
import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.TaxAssumptions;
import com.daviddunn.retirementplanner.domain.model.WithdrawalAssumptions;



import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.ui.controls.HelpIcon;
import com.daviddunn.retirementplanner.ui.controls.HelpLabel;
import com.daviddunn.retirementplanner.ui.help.HelpText;


import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

public class AssumptionsView extends VBox {

    /*
     * Projection assumptions.
     */
    private final DatePicker projectionStartDatePicker;
    private final TextField projectionLengthField;

    /*
     * Death scenario assumptions.
     */
    private final ComboBox<DeathScenario>
            deathScenarioComboBox;

    private final TextField deathYearField;

    private final ComboBox<Integer>
            survivorClaimingAgeComboBox;

    private final TextField postDeathExpenseFactorField;

    /*
     * Economic assumptions.
     */
    private final TextField investmentReturnField;
    private final TextField inflationRateField;
    private final TextField healthcareInflationField;
    private final TextField socialSecurityColaField;

    /*
     * Tax assumptions.
     */
    private final TextField federalBracketGrowthField;
    private final TextField standardDeductionGrowthField;
    private final TextField futureFederalMarginalRateAdjustmentField;
    private final TextField futureFederalMarginalRateEffectiveYearField;
    private final TextField stateIncomeTaxRateField;
    private final TextField localIncomeTaxRateField;
    private final TextField estimatedHeirTaxRateField;

    private final Label statusLabel;

    private RetirementPlan currentPlan;

    /*
     * MainWindow registers a callback here so
     * projections can be refreshed whenever
     * assumptions change.
     */
    private Runnable onPlanChanged;

    public AssumptionsView() {

        setPadding(new Insets(20));
        setSpacing(15);

        /*
         * Projection fields.
         */
        projectionStartDatePicker =
                new DatePicker();

        projectionLengthField =
                new TextField();

        /*
         * Death scenario fields.
         */
        deathScenarioComboBox =
                new ComboBox<>();

        deathScenarioComboBox
                .getItems()
                .addAll(
                        DeathScenario.values());

        deathYearField =
                new TextField();

        survivorClaimingAgeComboBox =
                new ComboBox<>();

        for (int age = 62; age <= 70; age++) {
            survivorClaimingAgeComboBox
                    .getItems()
                    .add(age);
        }

        deathScenarioComboBox
                .valueProperty()
                .addListener(
                        (observable,
                         oldValue,
                         newValue) ->
                                updateDeathScenarioFields());

        postDeathExpenseFactorField =
                new TextField("100");

        /*
         * Economic fields.
         */
        investmentReturnField =
                new TextField();

        inflationRateField =
                new TextField();

        healthcareInflationField =
                new TextField();

        socialSecurityColaField =
                new TextField();

        /*
         * Tax fields.
         */
        federalBracketGrowthField =
                new TextField();

        standardDeductionGrowthField =
                new TextField();

        futureFederalMarginalRateAdjustmentField =
                new TextField();

        futureFederalMarginalRateEffectiveYearField =
                new TextField();

        stateIncomeTaxRateField =
                new TextField();

        localIncomeTaxRateField =
                new TextField();

        estimatedHeirTaxRateField =
                new TextField();

        statusLabel =
                new Label();

        Button applyButton =
                new Button("Apply");

        applyButton.setOnAction(
                e -> applyChanges());

        GridPane grid =
                new GridPane();

        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        /*
         * =================================================
         * Projection
         * =================================================
         */

        Label projectionHeading =
                new Label("Projection");

        projectionHeading.setStyle(
                "-fx-font-weight: bold;");

        grid.add(
                projectionHeading,
                0,
                row++,
                3,
                1);

        grid.add(
                new Separator(),
                0,
                row++,
                3,
                1);

        grid.add(
                new Label("Projection Start Date:"),
                0,
                row);

        grid.add(
                projectionStartDatePicker,
                1,
                row++);

        grid.add(
                new Label("Projection Length (Years):"),
                0,
                row);

        grid.add(
                projectionLengthField,
                1,
                row++);


        /*
         * =================================================
         * Economic Assumptions
         * =================================================
         */

        Label economicHeading =
                new Label("Economic Assumptions");

        economicHeading.setStyle(
                "-fx-font-weight: bold;");

        grid.add(
                economicHeading,
                0,
                row++,
                3,
                1);

        grid.add(
                new Separator(),
                0,
                row++,
                3,
                1);

        grid.add(
                new Label(
                        "Annual Investment Return (%):"),
                0,
                row);

        grid.add(
                new HelpIcon(
                        HelpText.INVESTMENT_RETURN),
                1,
                row);

        grid.add(
                investmentReturnField,
                2,
                row++);

        grid.add(
                new Label(
                        "General Inflation Rate (%):"),
                0,
                row);

        grid.add(
                new HelpIcon(
                        HelpText.GENERAL_INFLATION),
                1,
                row);

        grid.add(
                inflationRateField,
                2,
                row++);

        grid.add(
                new Label(
                        "Healthcare Inflation Rate (%):"),
                0,
                row);

        grid.add(
                new HelpIcon(
                        HelpText.HEALTHCARE_INFLATION),
                1,
                row);

        grid.add(
                healthcareInflationField,
                2,
                row++);

        grid.add(
                new Label(
                        "Social Security COLA (%):"),
                0,
                row);

        grid.add(
                new HelpIcon(
                        HelpText.SOCIAL_SECURITY_COLA),
                1,
                row);

        grid.add(
                socialSecurityColaField,
                2,
                row++);

        /*
         * =================================================
         * Tax Assumptions
         * =================================================
         */

        Label taxHeading =
                new Label("Tax Assumptions");

        taxHeading.setStyle(
                "-fx-font-weight: bold;");

        grid.add(
                taxHeading,
                0,
                row++,
                3,
                1);

        grid.add(
                new Separator(),
                0,
                row++,
                3,
                1);

        grid.add(
                new Label(
                        "Federal Tax Bracket Growth (%):"),
                0,
                row);

        grid.add(
                federalBracketGrowthField,
                1,
                row++);

        grid.add(
                new Label(
                        "Standard Deduction Growth (%):"),
                0,
                row);

        grid.add(
                standardDeductionGrowthField,
                1,
                row++);

        grid.add(
                new Label(
                        "Future Federal Tax Rate Change (% points):"),
                0,
                row);

        grid.add(
                futureFederalMarginalRateAdjustmentField,
                1,
                row++);

        grid.add(
                new Label("Effective Year:"),
                0,
                row);

        grid.add(
                futureFederalMarginalRateEffectiveYearField,
                1,
                row++);

        grid.add(
                new Label(
                        "State Income Tax Rate (%):"),
                0,
                row);

        grid.add(
                stateIncomeTaxRateField,
                1,
                row++);

        grid.add(
                new Label(
                        "Local Income Tax Rate (%):"),
                0,
                row);

        grid.add(
                localIncomeTaxRateField,
                1,
                row++);

        grid.add(
                new Label(
                        "Estimated Heir Tax Rate (%):"),
                0,
                row);

        grid.add(
                estimatedHeirTaxRateField,
                1,
                row++);

        /*
         * =================================================
         * Death Scenario
         * =================================================
         */

        Label deathScenarioHeading =
                new Label("Death Scenario");

        deathScenarioHeading.setStyle(
                "-fx-font-weight: bold;");

        grid.add(
                deathScenarioHeading,
                0,
                row++,
                3,
                1);

        grid.add(
                new Separator(),
                0,
                row++,
                3,
                1);

        grid.add(
                new Label("Death Scenario:"),
                0,
                row);

        grid.add(
                deathScenarioComboBox,
                1,
                row++);

        grid.add(
                new Label("Death Year:"),
                0,
                row);

        grid.add(
                deathYearField,
                1,
                row++);

        grid.add(
                new Label("Survivor SSC Claiming Age:"),
                0,
                row);

        grid.add(
                survivorClaimingAgeComboBox,
                1,
                row++);

        grid.add(
                new Label("Post-Death Expense Factor (%):"),
                0,
                row);

        grid.add(
                postDeathExpenseFactorField,
                1,
                row++);


        grid.add(
                applyButton,
                1,
                row);

        getChildren().addAll(
                grid,
                statusLabel);

        /*
         * Establish the initial disabled state.
         */
        updateDeathScenarioFields();
    }

    public void load(
            RetirementPlan plan) {

        currentPlan = plan;

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        EconomicAssumptions economicAssumptions =
                assumptions
                        .getEconomicAssumptions();

        TaxAssumptions taxAssumptions =
                assumptions
                        .getTaxAssumptions();

        DeathScenarioAssumptions
                deathScenarioAssumptions =
                assumptions
                        .getDeathScenarioAssumptions();

        /*
         * Projection.
         */
        projectionStartDatePicker.setValue(
                assumptions
                        .getProjectionStartDate());

        projectionLengthField.setText(
                Integer.toString(
                        assumptions
                                .getProjectionLengthYears()));

        /*
         * Death scenario.
         */
        deathScenarioComboBox.setValue(
                deathScenarioAssumptions
                        .getDeathScenario());

        Integer deathYear =
                deathScenarioAssumptions
                        .getDeathYear();

        deathYearField.setText(
                deathYear != null
                        ? Integer.toString(deathYear)
                        : "");

        survivorClaimingAgeComboBox.setValue(
                deathScenarioAssumptions
                        .getSurvivorClaimingAge());

        BigDecimal postDeathExpenseFactor =
                deathScenarioAssumptions
                        .getPostDeathExpenseFactor();

        postDeathExpenseFactorField.setText(
                postDeathExpenseFactor
                        .multiply(BigDecimal.valueOf(100))
                        .stripTrailingZeros()
                        .toPlainString());

        updateDeathScenarioFields();

        /*
         * Economic assumptions.
         */
        investmentReturnField.setText(
                toPercent(
                        economicAssumptions
                                .getExpectedAnnualInvestmentReturn()));

        inflationRateField.setText(
                toPercent(
                        economicAssumptions
                                .getExpectedAnnualInflationRate()));

        healthcareInflationField.setText(
                toPercent(
                        economicAssumptions
                                .getHealthcareInflationRate()));

        socialSecurityColaField.setText(
                toPercent(
                        economicAssumptions
                                .getSocialSecurityColaRate()));

        /*
         * Tax assumptions.
         */
        federalBracketGrowthField.setText(
                toPercent(
                        taxAssumptions
                                .getFederalTaxBracketGrowthRate()));

        standardDeductionGrowthField.setText(
                toPercent(
                        taxAssumptions
                                .getStandardDeductionGrowthRate()));

        futureFederalMarginalRateAdjustmentField.setText(
                toPercent(
                        taxAssumptions
                                .getFutureFederalMarginalRateAdjustment()));

        Integer futureFederalMarginalRateEffectiveYear =
                taxAssumptions
                        .getFutureFederalMarginalRateEffectiveYear();

        futureFederalMarginalRateEffectiveYearField.setText(
                futureFederalMarginalRateEffectiveYear != null
                        ? futureFederalMarginalRateEffectiveYear.toString()
                        : "");

        stateIncomeTaxRateField.setText(
                toPercent(
                        taxAssumptions
                                .getStateIncomeTaxRate()));

        localIncomeTaxRateField.setText(
                toPercent(
                        taxAssumptions
                                .getLocalIncomeTaxRate()));

        estimatedHeirTaxRateField.setText(
                toPercent(
                        taxAssumptions
                                .getEstimatedHeirTaxRateOnTaxDeferredAssets()));

        statusLabel.setText("");
    }

    public void save(
            RetirementPlan plan) {

        /*
         * Make sure values currently displayed
         * in the view are copied into the plan
         * before JSON persistence.
         */
        if (plan == currentPlan) {
            applyChangesToModel();
        }
    }

    private void applyChangesToModel() {

        if (currentPlan == null) {
            return;
        }

        try {

            if (projectionStartDatePicker.getValue() == null) {

                throw new IllegalArgumentException(
                        "Projection start date is required.");
            }

            /*
             * Projection.
             */
            int projectionLength =
                    Integer.parseInt(
                            projectionLengthField
                                    .getText()
                                    .trim());

            /*
             * Death scenario.
             */
            DeathScenario deathScenario =
                    deathScenarioComboBox.getValue();

            if (deathScenario == null) {

                throw new IllegalArgumentException(
                        "Death scenario is required.");
            }

            Integer deathYear = null;
            Integer survivorClaimingAge = null;

            BigDecimal postDeathExpenseFactor =
                    parsePercent(
                            postDeathExpenseFactorField
                                    .getText());

            if (deathScenario != DeathScenario.BOTH_SURVIVE) {

                String deathYearText =
                        deathYearField
                                .getText()
                                .trim();

                if (deathYearText.isEmpty()) {

                    throw new IllegalArgumentException(
                            "Death year is required.");
                }

                deathYear =
                        Integer.parseInt(
                                deathYearText);

                survivorClaimingAge =
                        survivorClaimingAgeComboBox
                                .getValue();

                if (survivorClaimingAge == null) {

                    throw new IllegalArgumentException(
                            "Survivor claiming age is required.");
                }
            }

            DeathScenarioAssumptions
                    deathScenarioAssumptions =
                    new DeathScenarioAssumptions(
                            deathScenario,
                            deathYear,
                            survivorClaimingAge,
                            postDeathExpenseFactor);

            /*
             * Economic assumptions.
             */
            BigDecimal investmentReturn =
                    parsePercent(
                            investmentReturnField
                                    .getText());

            BigDecimal inflationRate =
                    parsePercent(
                            inflationRateField
                                    .getText());

            BigDecimal healthcareInflationRate =
                    parsePercent(
                            healthcareInflationField
                                    .getText());

            BigDecimal socialSecurityColaRate =
                    parsePercent(
                            socialSecurityColaField
                                    .getText());

            /*
             * Tax assumptions.
             */
            BigDecimal federalBracketGrowth =
                    parsePercent(
                            federalBracketGrowthField
                                    .getText());

            BigDecimal standardDeductionGrowth =
                    parsePercent(
                            standardDeductionGrowthField
                                    .getText());

            BigDecimal futureFederalMarginalRateAdjustment =
                    parsePercent(
                            futureFederalMarginalRateAdjustmentField
                                    .getText());

            String futureFederalMarginalRateEffectiveYearText =
                    futureFederalMarginalRateEffectiveYearField
                            .getText()
                            .trim();

            Integer futureFederalMarginalRateEffectiveYear =
                    futureFederalMarginalRateEffectiveYearText.isEmpty()
                            ? null
                            : Integer.parseInt(
                            futureFederalMarginalRateEffectiveYearText);

            if (futureFederalMarginalRateAdjustment.signum() != 0
                    && futureFederalMarginalRateEffectiveYear == null) {

                throw new IllegalArgumentException(
                        "Effective year is required for a future federal tax rate change.");
            }

            BigDecimal stateIncomeTaxRate =
                    parsePercent(
                            stateIncomeTaxRateField
                                    .getText());

            BigDecimal localIncomeTaxRate =
                    parsePercent(
                            localIncomeTaxRateField
                                    .getText());

            BigDecimal estimatedHeirTaxRate =
                    parsePercent(
                            estimatedHeirTaxRateField
                                    .getText());

            EconomicAssumptions
                    economicAssumptions =
                    new EconomicAssumptions(
                            investmentReturn,
                            inflationRate,
                            healthcareInflationRate,
                            socialSecurityColaRate);

            /*
             * Preserve the existing filing status.
             */
            FilingStatus
                    filingStatus =
                    currentPlan
                            .getPlanningAssumptions()
                            .getTaxAssumptions()
                            .getFilingStatus();

            TaxAssumptions taxAssumptions =
                    new TaxAssumptions(
                            federalBracketGrowth,
                            standardDeductionGrowth,
                            stateIncomeTaxRate,
                            localIncomeTaxRate,
                            filingStatus,
                            estimatedHeirTaxRate,
                            futureFederalMarginalRateAdjustment,
                            futureFederalMarginalRateEffectiveYear);

            /*
             * Preserve the existing withdrawal assumptions.
             */
            WithdrawalAssumptions
                    withdrawalAssumptions =
                    currentPlan
                            .getPlanningAssumptions()
                            .getWithdrawalAssumptions();

            /*
             * Rebuild PlanningAssumptions while
             * preserving all current assumption groups.
             */
            PlanningAssumptions updated =
                    new PlanningAssumptions(
                            economicAssumptions,
                            taxAssumptions,
                            withdrawalAssumptions,
                            deathScenarioAssumptions,
                            projectionLength,
                            projectionStartDatePicker
                                    .getValue());

            currentPlan.setPlanningAssumptions(
                    updated);

            statusLabel.setText(
                    "Assumptions applied.");

        }
        catch (Exception ex) {

            statusLabel.setText(
                    ex.getMessage() != null
                            ? ex.getMessage()
                            : "Please enter valid assumption values.");
        }
    }

    private void applyChanges() {

        applyChangesToModel();

        notifyPlanChanged();
    }

    private void updateDeathScenarioFields() {

        boolean deathScenarioActive =
                deathScenarioComboBox.getValue()
                        != null
                        && deathScenarioComboBox.getValue()
                        != DeathScenario.BOTH_SURVIVE;

        deathYearField.setDisable(
                !deathScenarioActive);

        survivorClaimingAgeComboBox.setDisable(
                !deathScenarioActive);

        postDeathExpenseFactorField.setDisable(
                !deathScenarioActive);
    }

    private BigDecimal parsePercent(
            String text) {

        return new BigDecimal(
                text.trim())
                .divide(
                        new BigDecimal("100"));
    }

    private String toPercent(
            BigDecimal rate) {

        return rate
                .multiply(
                        new BigDecimal("100"))
                .stripTrailingZeros()
                .toPlainString();
    }

    public void setOnPlanChanged(
            Runnable onPlanChanged) {

        this.onPlanChanged =
                onPlanChanged;
    }

    private void notifyPlanChanged() {

        if (onPlanChanged != null) {
            onPlanChanged.run();
        }
    }
}
