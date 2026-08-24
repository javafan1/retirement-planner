package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStrategy;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;

public class RothConversionView extends VBox {

    private final CheckBox enabledCheckBox;

    private final TextField conversionYearField;

    private final Label conversionAmountLabel;

    private final TextField conversionAmountField;

    private final Label targetTaxableIncomeLabel;

    private final TextField targetTaxableIncomeField;

    private final Label frequencyLabel;

    private final ComboBox<RothConversionFrequency>
            frequencyComboBox;

    private final ComboBox<RothConversionStrategy>
            strategyComboBox;

    private final ComboBox<RothConversionStopRule>
            stopRuleComboBox;

    private final Label statusLabel;

    private RetirementPlan currentPlan;

    private Runnable onPlanChanged;


    public RothConversionView() {

        setPadding(
                new Insets(20));

        setSpacing(15);


        GridPane grid =
                new GridPane();

        grid.setHgap(10);
        grid.setVgap(10);


        int row = 0;


        /*
         * =================================================
         * Roth Conversion
         * =================================================
         */

        Label heading =
                new Label(
                        "Roth Conversion");

        heading.setStyle(
                "-fx-font-weight: bold;");


        grid.add(
                heading,
                0,
                row++,
                2,
                1);


        grid.add(
                new Separator(),
                0,
                row++,
                2,
                1);


        /*
         * Enable conversion.
         */

        enabledCheckBox =
                new CheckBox(
                        "Enable Roth conversion");

        grid.add(
                enabledCheckBox,
                0,
                row++,
                2,
                1);


        /*
         * Strategy.
         */

        grid.add(
                new Label(
                        "Strategy:"),
                0,
                row);


        strategyComboBox =
                new ComboBox<>();

        strategyComboBox
                .getItems()
                .addAll(
                        RothConversionStrategy.values());

        strategyComboBox.setConverter(
                new StringConverter<>() {

                    @Override
                    public String toString(
                            RothConversionStrategy strategy) {

                        if (strategy == null) {
                            return "";
                        }

                        return strategy ==
                                RothConversionStrategy
                                        .CUSTOM_TAXABLE_INCOME_TARGET
                                ? "Custom Taxable Income Target"
                                : strategy.name();
                    }

                    @Override
                    public RothConversionStrategy fromString(
                            String value) {

                        if ("Custom Taxable Income Target"
                                .equals(value)) {

                            return RothConversionStrategy
                                    .CUSTOM_TAXABLE_INCOME_TARGET;
                        }

                        return RothConversionStrategy.valueOf(value);
                    }
                });

        strategyComboBox
                .getSelectionModel()
                .select(
                        RothConversionStrategy.FIXED_AMOUNT);

        strategyComboBox
                .valueProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                updateStrategyFields());

        grid.add(
                strategyComboBox,
                1,
                row++);


        /*
         * Conversion year.
         */

        grid.add(
                new Label(
                        "Conversion Year:"),
                0,
                row);


        conversionYearField =
                new TextField();

        grid.add(
                conversionYearField,
                1,
                row++);


        /*
         * Conversion amount.
         */

        conversionAmountLabel =
                new Label(
                        "Conversion Amount:");

        grid.add(
                conversionAmountLabel,
                0,
                row);


        conversionAmountField =
                new TextField();

        grid.add(
                conversionAmountField,
                1,
                row++);

        targetTaxableIncomeLabel =
                new Label(
                        "Target Taxable Income:");

        grid.add(
                targetTaxableIncomeLabel,
                0,
                row);

        targetTaxableIncomeField =
                new TextField();

        targetTaxableIncomeField.setPromptText(
                "Target Taxable Income");

        grid.add(
                targetTaxableIncomeField,
                1,
                row++);


        /*
         * Conversion frequency.
         */

        frequencyLabel =
                new Label(
                        "Frequency:");

        grid.add(
                frequencyLabel,
                0,
                row);


        frequencyComboBox =
                new ComboBox<>();

        frequencyComboBox
                .getItems()
                .addAll(
                        RothConversionFrequency.values());

        frequencyComboBox
                .getSelectionModel()
                .select(
                        RothConversionFrequency.ONE_TIME);

        grid.add(
                frequencyComboBox,
                1,
                row++);


        /*
         * Stop rule.
         */

        grid.add(
                new Label(
                        "Stop Rule:"),
                0,
                row);


        stopRuleComboBox =
                new ComboBox<>();

        stopRuleComboBox
                .getItems()
                .addAll(
                        RothConversionStopRule.values());

        stopRuleComboBox
                .getSelectionModel()
                .select(
                        RothConversionStopRule
                                .FIRST_HOUSEHOLD_RMD);

        grid.add(
                stopRuleComboBox,
                1,
                row++);


        /*
         * Apply button.
         */

        Button applyButton =
                new Button(
                        "Apply");

        applyButton.setOnAction(
                e -> applyChanges());


        grid.add(
                applyButton,
                1,
                row++);


        statusLabel =
                new Label();


        getChildren().addAll(
                grid,
                statusLabel);


        /*
         * Configure the initial field visibility.
         */

        updateStrategyFields();
    }


    private void updateStrategyFields() {

        boolean fixedAmount =
                strategyComboBox.getValue()
                        == RothConversionStrategy.FIXED_AMOUNT;

        boolean customTarget =
                strategyComboBox.getValue()
                        == RothConversionStrategy
                        .CUSTOM_TAXABLE_INCOME_TARGET;


        /*
         * Fixed-dollar conversions require
         * both an amount and a frequency.
         */

        conversionAmountLabel.setVisible(
                fixedAmount);

        conversionAmountLabel.setManaged(
                fixedAmount);

        conversionAmountField.setVisible(
                fixedAmount);

        conversionAmountField.setManaged(
                fixedAmount);

        targetTaxableIncomeLabel.setVisible(
                customTarget);

        targetTaxableIncomeLabel.setManaged(
                customTarget);

        targetTaxableIncomeField.setVisible(
                customTarget);

        targetTaxableIncomeField.setManaged(
                customTarget);
    }


    public void load(
            RetirementPlan plan) {

        currentPlan =
                plan;

        RothConversionRequest request =
                plan.getRothConversionRequest();


        if (request == null) {

            enabledCheckBox.setSelected(
                    false);

            conversionYearField.setText("");

            conversionAmountField.setText("");

            targetTaxableIncomeField.setText("");

            strategyComboBox
                    .getSelectionModel()
                    .select(
                            RothConversionStrategy
                                    .FIXED_AMOUNT);

            frequencyComboBox
                    .getSelectionModel()
                    .select(
                            RothConversionFrequency
                                    .ONE_TIME);

            stopRuleComboBox
                    .getSelectionModel()
                    .select(
                            RothConversionStopRule
                                    .FIRST_HOUSEHOLD_RMD);

            statusLabel.setText("");

            updateStrategyFields();

            return;
        }


        enabledCheckBox.setSelected(
                request.isEnabled());


        conversionYearField.setText(
                Integer.toString(
                        request.getStartYear()));


        strategyComboBox
                .getSelectionModel()
                .select(
                        request.getStrategy());


        if (request.getStrategy()
                == RothConversionStrategy.FIXED_AMOUNT) {

            conversionAmountField.setText(
                    request
                            .getAnnualAmount()
                            .stripTrailingZeros()
                            .toPlainString());

        } else {

            conversionAmountField.setText("");
        }

        if (request.getStrategy()
                == RothConversionStrategy
                .CUSTOM_TAXABLE_INCOME_TARGET) {

            targetTaxableIncomeField.setText(
                    request
                            .getCustomTargetTaxableIncome()
                            .stripTrailingZeros()
                            .toPlainString());

        } else {

            targetTaxableIncomeField.setText("");
        }


        frequencyComboBox
                .getSelectionModel()
                .select(
                        request.getFrequency());


        stopRuleComboBox
                .getSelectionModel()
                .select(
                        request.getStopRule());


        updateStrategyFields();

        statusLabel.setText("");
    }


    public void save(
            RetirementPlan plan) {

        /*
         * Match the existing AssumptionsView
         * persistence pattern.
         */

        if (plan == currentPlan) {

            applyChangesToModel();
        }
    }


    private void applyChangesToModel() {

        if (currentPlan == null) {
            return;
        }


        /*
         * If the user disabled the feature,
         * remove the request from the plan.
         */

        if (!enabledCheckBox.isSelected()) {

            currentPlan.setRothConversionRequest(
                    null);

            statusLabel.setText(
                    "Roth conversion disabled.");

            return;
        }


        try {

            /*
             * Conversion year.
             */

            String yearText =
                    conversionYearField
                            .getText()
                            .trim();


            if (yearText.isEmpty()) {

                throw new IllegalArgumentException(
                        "Conversion year is required.");
            }


            int conversionYear =
                    Integer.parseInt(
                            yearText);


            if (conversionYear < 1900) {

                throw new IllegalArgumentException(
                        "Conversion year is invalid.");
            }


            /*
             * Strategy.
             */

            RothConversionStrategy strategy =
                    strategyComboBox.getValue();


            if (strategy == null) {

                throw new IllegalArgumentException(
                        "Conversion strategy is required.");
            }


            /*
             * Conversion amount.
             *
             * Fixed-dollar conversions require
             * a user-entered amount.
             *
             * Bracket-fill conversions calculate
             * the amount automatically.
             */

            BigDecimal conversionAmount =
                    BigDecimal.ZERO;


            if (strategy ==
                    RothConversionStrategy.FIXED_AMOUNT) {

                String amountText =
                        conversionAmountField
                                .getText()
                                .trim();


                if (amountText.isEmpty()) {

                    throw new IllegalArgumentException(
                            "Conversion amount is required.");
                }


                conversionAmount =
                        new BigDecimal(
                                amountText);


                if (conversionAmount.signum() < 0) {

                    throw new IllegalArgumentException(
                            "Conversion amount cannot be negative.");
                }
            }


            RothConversionFrequency frequency =
                    frequencyComboBox.getValue();

            if (frequency == null) {

                throw new IllegalArgumentException(
                        "Conversion frequency is required.");
            }

            BigDecimal customTargetTaxableIncome =
                    null;

            if (strategy ==
                    RothConversionStrategy
                            .CUSTOM_TAXABLE_INCOME_TARGET) {

                String targetText =
                        targetTaxableIncomeField
                                .getText()
                                .trim();

                if (targetText.isEmpty()) {

                    throw new IllegalArgumentException(
                            "Target taxable income is required.");
                }

                customTargetTaxableIncome =
                        new BigDecimal(targetText);

                if (customTargetTaxableIncome.signum() < 0) {

                    throw new IllegalArgumentException(
                            "Target taxable income cannot be negative.");
                }
            }


            /*
             * Stop rule.
             */

            RothConversionStopRule stopRule =
                    stopRuleComboBox.getValue();


            if (stopRule == null) {

                throw new IllegalArgumentException(
                        "Stop rule is required.");
            }


            /*
             * Create the domain request.
             */

            RothConversionRequest request =
                    new RothConversionRequest(
                            true,
                            conversionYear,
                            conversionAmount,
                            stopRule,
                            strategy,
                            frequency,
                            customTargetTaxableIncome);


            currentPlan.setRothConversionRequest(
                    request);


            statusLabel.setText(
                    "Roth conversion applied.");

        }
        catch (NumberFormatException ex) {

            statusLabel.setText(
                    "Conversion year, amount, and target taxable income must be valid.");

        }
        catch (IllegalArgumentException ex) {

            statusLabel.setText(
                    ex.getMessage());
        }
    }


    private void applyChanges() {

        applyChangesToModel();

        notifyPlanChanged();
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
