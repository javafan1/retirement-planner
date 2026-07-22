
package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.text.NumberFormat;

public class ProjectionView extends BorderPane {

    private final ProjectionEngine engine = new ProjectionEngine();

    private Projection projection;
    private int currentYearIndex;

    private final Label positionLabel = new Label();

    private final Label yearLabel = new Label();
    private final Label beginningAssetsLabel = new Label();
    private final Label investmentGrowthLabel = new Label();
    private final Label guaranteedIncomeLabel = new Label();
    private final Label expensesLabel = new Label();
    private final Label endingAssetsLabel = new Label();

    private final Button previousButton = new Button("Previous");
    private final Button nextButton = new Button("Next");

    private final NumberFormat currency =
            NumberFormat.getCurrencyInstance();

    public ProjectionView() {

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(15));
        grid.setHgap(15);
        grid.setVgap(10);

        int row = 0;

        grid.add(positionLabel, 0, row++, 2, 1);

        grid.add(new Label("Calendar Year:"), 0, row);
        grid.add(yearLabel, 1, row++);

        grid.add(new Label("Beginning Assets:"), 0, row);
        grid.add(beginningAssetsLabel, 1, row++);

        grid.add(new Label("Investment Growth:"), 0, row);
        grid.add(investmentGrowthLabel, 1, row++);

        grid.add(new Label("Guaranteed Income:"), 0, row);
        grid.add(guaranteedIncomeLabel, 1, row++);

        grid.add(new Label("Annual Expenses:"), 0, row);
        grid.add(expensesLabel, 1, row++);

        grid.add(new Label("Ending Assets:"), 0, row);
        grid.add(endingAssetsLabel, 1, row);

        previousButton.setOnAction(e -> showPreviousYear());
        nextButton.setOnAction(e -> showNextYear());

        HBox navigation = new HBox(10);
        navigation.setPadding(new Insets(10));
        navigation.getChildren().addAll(previousButton, nextButton);

        setCenter(grid);
        setBottom(navigation);
    }

    public void load(RetirementPlan plan) {

        if (plan == null) {
            clear();
            return;
        }

        projection = engine.project(plan);

        if (projection == null || projection.isEmpty()) {
            clear();
            return;
        }

        currentYearIndex = 0;

        displayCurrentYear();
    }

    private void displayCurrentYear() {

        ProjectionYear year =
                projection.getYear(currentYearIndex);

        positionLabel.setText(
                "Year " +
                        (currentYearIndex + 1) +
                        " of " +
                        projection.size());

        display(year);

        previousButton.setDisable(currentYearIndex == 0);

        nextButton.setDisable(
                currentYearIndex ==
                        projection.size() - 1);
    }

    private void display(ProjectionYear year) {

        yearLabel.setText(
                Integer.toString(year.getCalendarYear()));

        beginningAssetsLabel.setText(
                currency.format(year.getBeginningInvestableAssets()));

        investmentGrowthLabel.setText(
                currency.format(year.getInvestmentGrowth()));

        guaranteedIncomeLabel.setText(
                currency.format(year.getGuaranteedIncome()));

        expensesLabel.setText(
                currency.format(year.getProjectedExpenses()));

        endingAssetsLabel.setText(
                currency.format(year.getEndingInvestableAssets()));
    }

    private void showPreviousYear() {

        if (currentYearIndex > 0) {
            currentYearIndex--;
            displayCurrentYear();
        }
    }

    private void showNextYear() {

        if (currentYearIndex < projection.size() - 1) {
            currentYearIndex++;
            displayCurrentYear();
        }
    }

    private void clear() {

        projection = null;

        positionLabel.setText("");

        yearLabel.setText("");
        beginningAssetsLabel.setText("");
        investmentGrowthLabel.setText("");
        guaranteedIncomeLabel.setText("");
        expensesLabel.setText("");
        endingAssetsLabel.setText("");

        previousButton.setDisable(true);
        nextButton.setDisable(true);
    }
}

