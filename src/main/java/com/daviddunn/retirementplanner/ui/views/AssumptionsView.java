package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

public class AssumptionsView extends VBox {

    private final DatePicker projectionStartDatePicker;

    private final TextField investmentReturnField;
    private final TextField inflationRateField;
    private final TextField projectionLengthField;

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

        projectionStartDatePicker =
                new DatePicker();

        investmentReturnField =
                new TextField();

        inflationRateField =
                new TextField();

        projectionLengthField =
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

        grid.add(
                new Label("Projection Start Date:"),
                0,
                row);

        grid.add(
                projectionStartDatePicker,
                1,
                row++);

        grid.add(
                new Label("Expected Investment Return (%):"),
                0,
                row);

        grid.add(
                investmentReturnField,
                1,
                row++);

        grid.add(
                new Label("Expected Inflation Rate (%):"),
                0,
                row);

        grid.add(
                inflationRateField,
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

        grid.add(
                applyButton,
                1,
                row);

        getChildren().addAll(
                grid,
                statusLabel);
    }

    public void load(
            RetirementPlan plan) {

        currentPlan = plan;

        PlanningAssumptions assumptions =
                plan.getPlanningAssumptions();

        projectionStartDatePicker.setValue(
                assumptions
                        .getProjectionStartDate());

        investmentReturnField.setText(
                toPercent(
                        assumptions
                                .getExpectedAnnualInvestmentReturn()));

        inflationRateField.setText(
                toPercent(
                        assumptions
                                .getExpectedAnnualInflationRate()));

        projectionLengthField.setText(
                Integer.toString(
                        assumptions
                                .getProjectionLengthYears()));

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
            applyChanges();
        }
    }

    private void applyChanges() {

        if (currentPlan == null) {
            return;
        }

        try {

            if (projectionStartDatePicker.getValue() == null) {

                throw new IllegalArgumentException(
                        "Projection start date is required.");
            }

            BigDecimal investmentReturn =
                    parsePercent(
                            investmentReturnField
                                    .getText());

            BigDecimal inflationRate =
                    parsePercent(
                            inflationRateField
                                    .getText());

            int projectionLength =
                    Integer.parseInt(
                            projectionLengthField
                                    .getText()
                                    .trim());

            PlanningAssumptions updated =
                    new PlanningAssumptions(
                            investmentReturn,
                            inflationRate,
                            projectionLength,
                            projectionStartDatePicker
                                    .getValue());

            currentPlan.setPlanningAssumptions(
                    updated);

            statusLabel.setText(
                    "Assumptions applied.");

            /*
             * Tell MainWindow that the plan
             * changed so projections can be
             * recalculated.
             */
            notifyPlanChanged();
        }
        catch (Exception ex) {

            statusLabel.setText(
                    "Please enter valid assumption values.");
        }
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