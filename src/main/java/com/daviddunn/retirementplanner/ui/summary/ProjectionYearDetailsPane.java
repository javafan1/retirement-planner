package com.daviddunn.retirementplanner.ui.summary;

import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountSnapshot;
import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.ui.util.UIFormatters;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

public class ProjectionYearDetailsPane extends BorderPane {

    public ProjectionYearDetailsPane(
            ProjectionYear year) {

        VBox content =
                new VBox(15);

        content.setPadding(
                new Insets(15));

        content.getChildren().add(
                createTitle(year));

        /*
         * Two-column layout.
         *
         * Left:
         *   Portfolio
         *   Federal Income Tax
         *   Michigan Income Tax
         *   Estimated Estate Value
         *
         * Right:
         *   Income & Withdrawals
         *   Expenses
         *   Medicare
         *   Totals
         */
        HBox columns =
                new HBox(
                        30);

        VBox leftColumn =
                new VBox(
                        15,
                        createPortfolioSection(year),
                        createFederalTaxSection(year),
                        createMichiganTaxSection(year),
                        createEstateSection(year));

        VBox rightColumn =
                new VBox(
                        15,
                        createIncomeSection(year),
                        createExpenseSection(year),
                        createMedicareSection(year),
                        createTotalsSection(year));

        HBox.setHgrow(
                leftColumn,
                Priority.ALWAYS);

        HBox.setHgrow(
                rightColumn,
                Priority.ALWAYS);

        leftColumn.setMaxWidth(
                Double.MAX_VALUE);

        rightColumn.setMaxWidth(
                Double.MAX_VALUE);

        columns.getChildren().addAll(
                leftColumn,
                rightColumn);

        content.getChildren().add(
                columns);

        ScrollPane scrollPane =
                new ScrollPane(content);

        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER);

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED);

        setCenter(scrollPane);
    }


    // ============================================================
    // Portfolio
    // ============================================================

    private VBox createPortfolioSection(
            ProjectionYear year) {

        GridPane grid =
                createSectionGrid();

        int row = 0;

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
                "Tax-Deferred Accounts",
                getEndingBalanceByAssetType(
                        year,
                        ProjectionAssetType.TAX_DEFERRED));

        row = addMoneyRow(
                grid,
                row,
                "Taxable Accounts",
                getEndingBalanceByTaxTreatment(
                        year,
                        TaxTreatment.TAXABLE));

        row = addMoneyRow(
                grid,
                row,
                "Roth Accounts",
                getEndingBalanceByAssetType(
                        year,
                        ProjectionAssetType.ROTH));

        row = addMoneyRow(
                grid,
                row,
                "Accumulated RMD Cash",
                year.getUnallocatedCash());

        row = addMoneyRow(
                grid,
                row,
                "Cash Accounts",
                getEndingBalanceByTaxTreatment(
                        year,
                        TaxTreatment.CASH));

        addMoneyRow(
                grid,
                row,
                "Ending Assets",
                year.getEndingInvestableAssets());

        return createSection(
                "Portfolio",
                grid);
    }


    // ============================================================
    // Income & Withdrawals
    // ============================================================

    private VBox createIncomeSection(
            ProjectionYear year) {

        GridPane grid =
                createSectionGrid();

        int row = 0;

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
                "Roth Conversion",
                year.getRothConversion());

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

        addMoneyRow(
                grid,
                row,
                "Excess RMD",
                year.getExcessRmd());

        return createSection(
                "Income & Withdrawals",
                grid);
    }


    // ============================================================
    // Expenses
    // ============================================================

    private VBox createExpenseSection(
            ProjectionYear year) {

        GridPane grid =
                createSectionGrid();

        int row = 0;

        row = addMoneyRow(
                grid,
                row,
                "Annual Expenses",
                year.getAnnualExpenses());

        addMoneyRow(
                grid,
                row,
                "Cash Flow Need",
                year.getCashFlowNeed());

        return createSection(
                "Expenses",
                grid);
    }


    // ============================================================
    // Federal Tax
    // ============================================================

    private VBox createFederalTaxSection(
            ProjectionYear year) {

        GridPane grid =
                createSectionGrid();

        int row = 0;

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

        addMoneyRow(
                grid,
                row,
                "Federal Income Tax",
                year.getFederalIncomeTax());

        return createSection(
                "Federal Income Tax",
                grid);
    }


    // ============================================================
    // Michigan Tax
    // ============================================================

    private VBox createMichiganTaxSection(
            ProjectionYear year) {

        GridPane grid =
                createSectionGrid();

        int row = 0;

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

        addMoneyRow(
                grid,
                row,
                "Michigan Income Tax",
                year.getMichiganIncomeTax());

        return createSection(
                "Michigan Income Tax",
                grid);
    }


    // ============================================================
    // Medicare
    // ============================================================

    private VBox createMedicareSection(
            ProjectionYear year) {

        GridPane grid =
                createSectionGrid();

        int row = 0;

        row = addMoneyRow(
                grid,
                row,
                "Modified Adjusted Gross Income",
                year.getModifiedAdjustedGrossIncome());

        row = addTextRow(
                grid,
                row,
                "IRMAA Bracket",
                year.getIrmaaBracketDisplay());

        row = addMoneyRow(
                grid,
                row,
                "Monthly Part B Premium",
                year.getMonthlyPartBPremium());

        row = addMoneyRow(
                grid,
                row,
                "Annual Part B Premium",
                year.getAnnualPartBPremium());

        row = addMoneyRow(
                grid,
                row,
                "Monthly Part D Premium",
                year.getMonthlyPartDPremium());

        row = addMoneyRow(
                grid,
                row,
                "Annual Part D Premium",
                year.getAnnualPartDPremium());

        addMoneyRow(
                grid,
                row,
                "Total Annual Medicare Premium",
                year.getAnnualMedicarePremium());

        return createSection(
                "Medicare",
                grid);
    }


    // ============================================================
    // Totals
    // ============================================================

    private VBox createTotalsSection(
            ProjectionYear year) {

        GridPane grid =
                createSectionGrid();

        int row = 0;

        row = addMoneyRow(
                grid,
                row,
                "Total Income Tax",
                year.getTotalIncomeTax());

        addPercentageRow(
                grid,
                row,
                "Combined Effective Tax Rate",
                year.getCombinedEffectiveTaxRate());

        return createSection(
                "Totals",
                grid);
    }


    // ============================================================
    // Estate
    // ============================================================

    private VBox createEstateSection(
            ProjectionYear year) {

        GridPane grid =
                createSectionGrid();

        int row = 0;

        row = addMoneyRow(
                grid,
                row,
                "Gross Estate Value",
                year.getEndingInvestableAssets());

        row = addMoneyRow(
                grid,
                row,
                "Estimated Heir Tax",
                year.getEstimatedHeirTax());

        addMoneyRow(
                grid,
                row,
                "Projected After-Tax Estate",
                year.getAfterTaxEstateValue());

        return createSection(
                "Estimated Estate Value",
                grid);
    }


    // ============================================================
    // Section / Grid Helpers
    // ============================================================

    private VBox createSection(
            String heading,
            GridPane grid) {

        Label headingLabel =
                new Label(heading);

        headingLabel.setStyle(
                "-fx-font-size:14px; " +
                        "-fx-font-weight:bold;");

        VBox section =
                new VBox(
                        6,
                        headingLabel,
                        grid);

        section.setMaxWidth(
                Double.MAX_VALUE);

        return section;
    }


    private GridPane createSectionGrid() {

        GridPane grid =
                new GridPane();

        grid.setHgap(15);
        grid.setVgap(6);

        ColumnConstraints labelColumn =
                new ColumnConstraints();

        ColumnConstraints valueColumn =
                new ColumnConstraints();

        labelColumn.setHgrow(
                Priority.ALWAYS);

        valueColumn.setHalignment(
                HPos.RIGHT);

        grid.getColumnConstraints().addAll(
                labelColumn,
                valueColumn);

        return grid;
    }


    // ============================================================
    // Row Helpers
    // ============================================================

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


    private int addPercentageRow(
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
                        UIFormatters.percent(value));

        GridPane.setHalignment(
                valueLabel,
                HPos.RIGHT);

        grid.add(
                valueLabel,
                1,
                row);

        return row + 1;
    }


    private int addTextRow(
            GridPane grid,
            int row,
            String description,
            String value) {

        grid.add(
                new Label(description),
                0,
                row);

        Label valueLabel =
                new Label(value);

        GridPane.setHalignment(
                valueLabel,
                HPos.RIGHT);

        grid.add(
                valueLabel,
                1,
                row);

        return row + 1;
    }


    // ============================================================
    // Title
    // ============================================================

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
                "-fx-font-size:18px; " +
                        "-fx-font-weight:bold;");

        return label;
    }


    // ============================================================
    // Account Balance Helpers
    // ============================================================

    private BigDecimal getEndingBalanceByAssetType(
            ProjectionYear year,
            ProjectionAssetType assetType) {

        return year
                .getEndingAccountSnapshots()
                .stream()
                .filter(snapshot ->
                        snapshot.getAccount()
                                .getProjectionAssetType()
                                == assetType)
                .map(
                        ProjectedAccountSnapshot
                                ::getEndingBalance)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }


    private BigDecimal getEndingBalanceByTaxTreatment(
            ProjectionYear year,
            TaxTreatment taxTreatment) {

        return year
                .getEndingAccountSnapshots()
                .stream()
                .filter(snapshot ->
                        snapshot
                                .getAccount()
                                .getTaxTreatment()
                                == taxTreatment)
                .map(
                        ProjectedAccountSnapshot
                                ::getEndingBalance)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }
}