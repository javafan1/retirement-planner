package com.daviddunn.retirementplanner.ui.summary;

import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ProjectionYearSummaryPane extends VBox {

    public ProjectionYearSummaryPane(
            ProjectionYear year) {

        setSpacing(15);
        setPadding(new Insets(15));

        getChildren().add(createTitle(year));

        GridPane grid =
                createGrid();

        int row = 0;

        row = addSectionHeading(
                grid,
                row,
                "Portfolio");

        row = addMoneyRow(
                grid,
                row,
                "Beginning Assets",
                year.getBeginningInvestableAssets());

        row = addMoneyRow(
                grid,
                row,
                "Investment Growth",
                year.getInvestmentGrowth());

        row = addMoneyRow(
                grid,
                row,
                "Ending Assets",
                year.getEndingInvestableAssets());

        row = addBlankRow(row);

        row = addSectionHeading(
                grid,
                row,
                "Income");

        row = addMoneyRow(
                grid,
                row,
                "Guaranteed Income",
                year.getGuaranteedIncome());

        row = addMoneyRow(
                grid,
                row,
                "Portfolio Withdrawal",
                year.getPortfolioWithdrawal());

        row = addBlankRow(row);

        row = addSectionHeading(
                grid,
                row,
                "Expenses");

        row = addMoneyRow(
                grid,
                row,
                "Annual Expenses",
                year.getAnnualExpenses());

        getChildren().add(grid);

        row = addBlankRow(row);

        row = addSectionHeading(
                grid,
                row,
                "Taxes");

        row = addMoneyRow(
                grid,
                row,
                "Federal Income Tax",
                year.getFederalIncomeTax());

        row = addMoneyRow(
                grid,
                row,
                "Tax Funding Withdrawal",
                year.getTaxFundingWithdrawal());

        row = addBlankRow(row);

        row = addSectionHeading(
                grid,
                row,
                "Retirement Rules");

        row = addMoneyRow(
                grid,
                row,
                "Required Minimum Distribution",
                year.getRequiredMinimumDistribution());

        row = addMoneyRow(
                grid,
                row,
                "Excess RMD",
                year.getExcessRmd());
    }

    private int addBlankRow(int row) {
        return row + 1;
    }

    private Label createTitle(
            ProjectionYear year) {

        Label label =
                new Label(
                        "Projection Summary - "
                                + year.getCalendarYear());

        label.setStyle(
                "-fx-font-size:18px; -fx-font-weight:bold;");

        return label;
    }

    private GridPane createGrid() {

        GridPane grid =
                new GridPane();

        grid.setHgap(20);
        grid.setVgap(8);

        ColumnConstraints labelColumn =
                new ColumnConstraints();

        ColumnConstraints valueColumn =
                new ColumnConstraints();

        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setHalignment(HPos.RIGHT);

        grid.getColumnConstraints().addAll(
                labelColumn,
                valueColumn);

        return grid;
    }

    private int addSectionHeading(
            GridPane grid,
            int row,
            String heading) {

        Label label =
                new Label(heading);

        label.setStyle(
                "-fx-font-size:14px; -fx-font-weight:bold;");

        grid.add(
                label,
                0,
                row,
                2,
                1);

        return row + 1;
    }

    private int addMoneyRow(
            GridPane grid,
            int row,
            String description,
            java.math.BigDecimal value) {

        grid.add(
                new Label(description),
                0,
                row);

        Label valueLabel =
                new Label(
                        UIFormatters.money(value));

        GridPane.setHalignment(
                valueLabel,
                HPos.RIGHT);

        grid.add(
                valueLabel,
                1,
                row);

        return row + 1;
    }
}