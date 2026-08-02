package com.daviddunn.retirementplanner.ui.summary;

import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

public class ProjectionYearDetailsPane extends BorderPane {

    public ProjectionYearDetailsPane(
            ProjectionYear year) {

        VBox content =
                new VBox();

        content.setSpacing(15);
        content.setPadding(
                new Insets(15));

        content.getChildren().add(
                createTitle(year));

        GridPane grid =
                createGrid();

        int row = 0;

        // ----------------------------------------------------
        // Portfolio
        // ----------------------------------------------------

        row = addPortfolioSection(
                grid,
                row,
                year);

        // ----------------------------------------------------
        // Income & Withdrawals
        // ----------------------------------------------------

        row = addIncomeSection(
                grid,
                row,
                year);

        // ----------------------------------------------------
        // Expenses
        // ----------------------------------------------------

        row = addExpenseSection(
                grid,
                row,
                year);

        // ----------------------------------------------------
        // Federal Taxes
        // ----------------------------------------------------

        row = addFederalTaxSection(
                grid,
                row,
                year);

        // ----------------------------------------------------
        // Michigan Taxes
        // ----------------------------------------------------

        row = addMichiganTaxSection(
                grid,
                row,
                year);

        // ----------------------------------------------------
        // Totals
        // ----------------------------------------------------

        addTotalsSection(
                grid,
                row,
                year);

        content.getChildren().add(grid);

        ScrollPane scrollPane =
                new ScrollPane(content);

        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER);

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED);

        setCenter(scrollPane);
    }

    private int addPortfolioSection(
            GridPane grid,
            int row,
            ProjectionYear year) {

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

        return addBlankRow(row);
    }

    private int addIncomeSection(
            GridPane grid,
            int row,
            ProjectionYear year) {

        row = addSectionHeading(
                grid,
                row,
                "Income & Withdrawals");

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

        row = addMoneyRow(
                grid,
                row,
                "Tax Funding Withdrawal",
                year.getTaxFundingWithdrawal());

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

        return addBlankRow(row);
    }

    private int addExpenseSection(
            GridPane grid,
            int row,
            ProjectionYear year) {

        row = addSectionHeading(
                grid,
                row,
                "Expenses");

        row = addMoneyRow(
                grid,
                row,
                "Annual Expenses",
                year.getAnnualExpenses());

        row = addMoneyRow(
                grid,
                row,
                "Cash Flow Need",
                year.getCashFlowNeed());

        return addBlankRow(row);
    }

    private int addFederalTaxSection(
            GridPane grid,
            int row,
            ProjectionYear year) {

        row = addSectionHeading(
                grid,
                row,
                "Federal Income Tax");

        row = addMoneyRow(
                grid,
                row,
                "Adjusted Gross Income",
                year.getAdjustedGrossIncome());

        row = addMoneyRow(
                grid,
                row,
                "Taxable Social Security",
                year.getTaxableSocialSecurity());


        row = addMoneyRow(
                grid,
                row,
                "Federal Taxable Income",
                year.getFederalTaxableIncome());

        row = addMoneyRow(
                grid,
                row,
                "Standard Deduction",
                year.getFederalStandardDeduction());

        row = addMoneyRow(
                grid,
                row,
                "Federal Income Tax",
                year.getFederalIncomeTax());

        return addBlankRow(row);
    }

    private int addMichiganTaxSection(
            GridPane grid,
            int row,
            ProjectionYear year) {

        row = addSectionHeading(
                grid,
                row,
                "Michigan Income Tax");

        row = addMoneyRow(
                grid,
                row,
                "Retirement Income",
                year.getMichiganRetirementIncome());

        row = addMoneyRow(
                grid,
                row,
                "Retirement Deduction",
                year.getMichiganRetirementDeduction());

        row = addMoneyRow(
                grid,
                row,
                "Michigan Taxable Income",
                year.getMichiganTaxableIncome());

        row = addMoneyRow(
                grid,
                row,
                "Michigan Income Tax",
                year.getMichiganIncomeTax());

        return addBlankRow(row);
    }

    private int addTotalsSection(
            GridPane grid,
            int row,
            ProjectionYear year) {

        row = addSectionHeading(
                grid,
                row,
                "Totals");

        row = addMoneyRow(
                grid,
                row,
                "Total Income Tax",
                year.getTotalIncomeTax());

        return row;
    }

    private int addBlankRow(
            int row) {

        return row + 1;
    }

    private Label createTitle(
            ProjectionYear year) {

        Label label =
                new Label(
                        "Projection Summary - "
                                + year.getCalendarYear()
                                + " (Age "
                                + year.getPrimaryPersonAge()
                                + ")");

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

        valueColumn.setHgrow(
                Priority.ALWAYS);

        valueColumn.setHalignment(
                HPos.RIGHT);

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
            BigDecimal value) {

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