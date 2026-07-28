package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.projection.Projection;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

import java.text.NumberFormat;

public class DashboardView extends BorderPane {

    private final Label yearsProjectedLabel = new Label();
    private final Label startYearLabel = new Label();
    private final Label endYearLabel = new Label();
    private final Label endingAssetsLabel = new Label();
    private final Label highestAssetsLabel = new Label();
    private final Label lowestAssetsLabel = new Label();
    private final Label statusLabel = new Label();

    private final NumberFormat currency =
            NumberFormat.getCurrencyInstance();

    public DashboardView() {

        GridPane grid = new GridPane();

        grid.setPadding(new Insets(20));
        grid.setHgap(20);
        grid.setVgap(12);

        int row = 0;

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

        setCenter(grid);

        clear();
    }

    public void load(Projection projection) {

        if (projection == null || projection.isEmpty()) {
            clear();
            return;
        }

        yearsProjectedLabel.setText(
                Integer.toString(projection.getYearsProjected()));

        startYearLabel.setText(
                Integer.toString(projection.getStartYear()));

        endYearLabel.setText(
                Integer.toString(projection.getEndYear()));

        endingAssetsLabel.setText(
                currency.format(projection.getFinalInvestableAssets()));

        highestAssetsLabel.setText(
                currency.format(projection.getHighestInvestableAssets()));

        lowestAssetsLabel.setText(
                currency.format(projection.getLowestInvestableAssets()));

        statusLabel.setText(
                projection.depletedPortfolio()
                        ? "Portfolio Depleted"
                        : "Portfolio Sustained");
    }

    private void clear() {

        yearsProjectedLabel.setText("");
        startYearLabel.setText("");
        endYearLabel.setText("");
        endingAssetsLabel.setText("");
        highestAssetsLabel.setText("");
        lowestAssetsLabel.setText("");
        statusLabel.setText("");
    }
}