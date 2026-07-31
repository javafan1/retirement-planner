package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.summary.IncomeSummary;
import com.daviddunn.retirementplanner.domain.projection.summary.ProjectionSummary;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;

public class DashboardView extends BorderPane {

    // Projection Summary

    private final Label yearsProjectedLabel = new Label();
    private final Label startYearLabel = new Label();
    private final Label endYearLabel = new Label();
    private final Label endingAssetsLabel = new Label();
    private final Label highestAssetsLabel = new Label();
    private final Label lowestAssetsLabel = new Label();
    private final Label statusLabel = new Label();

    // Guaranteed Income

    private final Label primarySocialSecurityLabel = new Label();
    private final Label spouseSocialSecurityLabel = new Label();

    private final Label primaryPensionLabel = new Label();
    private final Label spousePensionLabel = new Label();

    private final Label totalGuaranteedIncomeLabel = new Label();

    private final NumberFormat currency =
            NumberFormat.getCurrencyInstance();

    public DashboardView() {

        VBox root = new VBox(25);
        root.setPadding(new Insets(20));

        root.getChildren().addAll(
                createProjectionSection(),
                createIncomeSection());

        setCenter(root);

        clear();
    }

    private GridPane createProjectionSection() {

        GridPane grid = new GridPane();

        grid.setHgap(20);
        grid.setVgap(10);

        int row = 0;

        Label heading =
                new Label("Projection Summary");

        heading.setStyle(
                "-fx-font-size:16; -fx-font-weight:bold;");

        grid.add(heading, 0, row++, 2, 1);

        grid.add(new Label("Projection Years:"), 0, row);
        grid.add(yearsProjectedLabel, 1, row++);

        grid.add(new Label("Start Year:"), 0, row);
        grid.add(startYearLabel, 1, row++);

        grid.add(new Label("End Year:"), 0, row);
        grid.add(endYearLabel, 1, row++);

        grid.add(new Label("Ending Portfolio:"), 0, row);
        grid.add(endingAssetsLabel, 1, row++);

        grid.add(new Label("Highest Portfolio:"), 0, row);
        grid.add(highestAssetsLabel, 1, row++);

        grid.add(new Label("Lowest Portfolio:"), 0, row);
        grid.add(lowestAssetsLabel, 1, row++);

        grid.add(new Label("Projection Status:"), 0, row);
        grid.add(statusLabel, 1, row);

        return grid;
    }

    private GridPane createIncomeSection() {

        GridPane grid = new GridPane();

        grid.setHgap(20);
        grid.setVgap(10);

        int row = 0;

        Label heading =
                new Label("Guaranteed Monthly Income");

        heading.setStyle(
                "-fx-font-size:16; -fx-font-weight:bold;");

        grid.add(heading, 0, row++, 2, 1);

        grid.add(new Label("Primary Social Security:"), 0, row);
        grid.add(primarySocialSecurityLabel, 1, row++);

        grid.add(new Label("Spouse Social Security:"), 0, row);
        grid.add(spouseSocialSecurityLabel, 1, row++);

        grid.add(new Label("Primary Pension:"), 0, row);
        grid.add(primaryPensionLabel, 1, row++);

        grid.add(new Label("Spouse Pension:"), 0, row);
        grid.add(spousePensionLabel, 1, row++);

        grid.add(new Label("Total Guaranteed Income:"), 0, row);
        grid.add(totalGuaranteedIncomeLabel, 1, row);

        return grid;
    }

    public void load(
            ProjectionSummary summary) {

        if (summary == null) {
            clear();
            return;
        }

        Projection projection =
                summary.getProjection();

        if (projection == null ||
                projection.isEmpty()) {

            clear();
            return;
        }

        yearsProjectedLabel.setText(
                Integer.toString(
                        projection.getYearsProjected()));

        startYearLabel.setText(
                Integer.toString(
                        projection.getStartYear()));

        endYearLabel.setText(
                Integer.toString(
                        projection.getEndYear()));

        endingAssetsLabel.setText(
                currency.format(
                        projection.getFinalInvestableAssets()));

        highestAssetsLabel.setText(
                currency.format(
                        projection.getHighestInvestableAssets()));

        lowestAssetsLabel.setText(
                currency.format(
                        projection.getLowestInvestableAssets()));

        statusLabel.setText(
                projection.depletedPortfolio()
                        ? "Portfolio Sustained"
                        : "Portfolio Depleted");

        IncomeSummary income =
                summary.getIncomeSummary();

        primarySocialSecurityLabel.setText(
                currency.format(
                        income.getPrimarySocialSecurityMonthly()));

        spouseSocialSecurityLabel.setText(
                currency.format(
                        income.getSpouseSocialSecurityMonthly()));

        primaryPensionLabel.setText(
                currency.format(
                        income.getPrimaryPensionMonthly()));

        spousePensionLabel.setText(
                currency.format(
                        income.getSpousePensionMonthly()));

        totalGuaranteedIncomeLabel.setText(
                currency.format(
                        income.getTotalGuaranteedMonthlyIncome()));
    }

    private void clear() {

        yearsProjectedLabel.setText("");
        startYearLabel.setText("");
        endYearLabel.setText("");
        endingAssetsLabel.setText("");
        highestAssetsLabel.setText("");
        lowestAssetsLabel.setText("");
        statusLabel.setText("");

        primarySocialSecurityLabel.setText("");
        spouseSocialSecurityLabel.setText("");
        primaryPensionLabel.setText("");
        spousePensionLabel.setText("");
        totalGuaranteedIncomeLabel.setText("");
    }
}