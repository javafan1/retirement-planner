package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.roth.RothConversionFrequency;
import com.daviddunn.retirementplanner.domain.roth.RothConversionRequest;
import com.daviddunn.retirementplanner.domain.roth.RothConversionStopRule;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

public class RothConversionView extends VBox {

    private final CheckBox enabledCheckBox;

    private final TextField conversionYearField;

    private final TextField conversionAmountField;

    private final ComboBox<RothConversionFrequency>
            frequencyComboBox;

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

        grid.add(
                new Label(
                        "Conversion Amount:"),
                0,
                row);


        conversionAmountField =
                new TextField();

        grid.add(
                conversionAmountField,
                1,
                row++);


        /*
         * Conversion frequency.
         */

        grid.add(
                new Label(
                        "Frequency:"),
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

            return;
        }


        enabledCheckBox.setSelected(
                request.isEnabled());


        conversionYearField.setText(
                Integer.toString(
                        request.getStartYear()));


        conversionAmountField.setText(
                request
                        .getAnnualAmount()
                        .stripTrailingZeros()
                        .toPlainString());


        frequencyComboBox
                .getSelectionModel()
                .select(
                        request.getFrequency());


        stopRuleComboBox
                .getSelectionModel()
                .select(
                        request.getStopRule());


        statusLabel.setText("");
    }


    public void save(
            RetirementPlan plan) {

        /*
         * Match the existing AssumptionsView
         * persistence pattern.
         *
         * The controls are already applied to
         * currentPlan when Apply is clicked.
         *
         * Save therefore only needs to make sure
         * the current plan reference is the plan
         * being persisted.
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
             * Conversion amount.
             */

            String amountText =
                    conversionAmountField
                            .getText()
                            .trim();


            if (amountText.isEmpty()) {

                throw new IllegalArgumentException(
                        "Conversion amount is required.");
            }


            BigDecimal conversionAmount =
                    new BigDecimal(
                            amountText);


            if (conversionAmount.signum() < 0) {

                throw new IllegalArgumentException(
                        "Conversion amount cannot be negative.");
            }


            /*
             * Conversion frequency.
             */

            RothConversionFrequency frequency =
                    frequencyComboBox.getValue();


            if (frequency == null) {

                throw new IllegalArgumentException(
                        "Conversion frequency is required.");
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
                            frequency);


            currentPlan.setRothConversionRequest(
                    request);


            statusLabel.setText(
                    "Roth conversion applied.");

        }
        catch (NumberFormatException ex) {

            statusLabel.setText(
                    "Conversion year and amount must be valid.");

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