package com.daviddunn.retirementplanner.ui.summary;

import com.daviddunn.retirementplanner.domain.model.TaxTreatment;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountSnapshot;
import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;
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
import java.math.RoundingMode;
import java.util.Objects;
import java.util.function.Function;

public class ProjectionYearDetailsPane
        extends BorderPane {

    private final ProjectionYear year;

    /*
     * The baseline is optional.
     *
     * null means that the user has not created
     * a baseline projection yet.
     */
    private final ProjectionYear baselineYear;

    private final BigDecimal
            nonInvestableAssetValue;

    private final BigDecimal
            baselineNonInvestableAssetValue;


    public ProjectionYearDetailsPane(
            ProjectionYear year,
            ProjectionYear baselineYear,
            BigDecimal nonInvestableAssetValue,
            BigDecimal baselineNonInvestableAssetValue) {

        this.year =
                Objects.requireNonNull(
                        year,
                        "Current projection year is required.");

        this.baselineYear =
                baselineYear;

        this.nonInvestableAssetValue =
                Objects.requireNonNull(
                        nonInvestableAssetValue,
                        "Non-investable asset value is required.");

        /*
         * This is intentionally allowed to be null.
         *
         * null means there is no baseline.
         */
        this.baselineNonInvestableAssetValue =
                baselineNonInvestableAssetValue;


        VBox content =
                new VBox(15);

        content.setPadding(
                new Insets(15));

        content.setMinWidth(
                1000);


        content.getChildren().add(
                createTitle(year));


        GridPane grid =
                createComparisonGrid();


        int row = 1;


        row =
                createPortfolioRows(
                        grid,
                        row);


        row =
                createIncomeRows(
                        grid,
                        row);


        row =
                createExpenseRows(
                        grid,
                        row);


        row =
                createFederalTaxRows(
                        grid,
                        row);


        row =
                createMichiganTaxRows(
                        grid,
                        row);


        row =
                createMedicareRows(
                        grid,
                        row);


        row =
                createTotalsRows(
                        grid,
                        row);


        createEstateRows(
                grid,
                row);


        content.getChildren().add(
                grid);


        ScrollPane scrollPane =
                new ScrollPane(
                        content);

        scrollPane.setFitToWidth(
                true);

        scrollPane.setFitToHeight(
                false);

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER);

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED);


        setCenter(
                scrollPane);
    }


    // ============================================================
    // Comparison Grid
    // ============================================================

    private GridPane createComparisonGrid() {

        GridPane grid =
                new GridPane();

        grid.setHgap(15);
        grid.setVgap(6);


        ColumnConstraints labelColumn =
                new ColumnConstraints();

        labelColumn.setMinWidth(230);

        labelColumn.setHgrow(
                Priority.ALWAYS);


        ColumnConstraints valueColumn =
                new ColumnConstraints();

        valueColumn.setMinWidth(130);

        valueColumn.setHalignment(
                HPos.RIGHT);

        valueColumn.setHgrow(
                Priority.ALWAYS);


        grid.getColumnConstraints().addAll(
                labelColumn,
                valueColumn,
                valueColumn,
                valueColumn,
                valueColumn);


        addComparisonHeaders(
                grid);


        return grid;
    }


    private void addComparisonHeaders(
            GridPane grid) {

        addHeader(
                grid,
                "Metric",
                0,
                0);

        addHeader(
                grid,
                "Current Projection",
                1,
                0);

        addHeader(
                grid,
                "Baseline Projection",
                2,
                0);

        addHeader(
                grid,
                "$ Difference",
                3,
                0);

        addHeader(
                grid,
                "% Difference",
                4,
                0);
    }


    private void addHeader(
            GridPane grid,
            String text,
            int column,
            int row) {

        Label label =
                createHeaderLabel(
                        text);

        GridPane.setHalignment(
                label,
                HPos.RIGHT);

        grid.add(
                label,
                column,
                row);
    }


    private Label createHeaderLabel(
            String text) {

        Label label =
                new Label(text);

        label.setStyle(
                "-fx-text-fill: #2563eb; " +
                        "-fx-font-weight: bold;");

        return label;
    }


    // ============================================================
    // Portfolio
    // ============================================================

    private int createPortfolioRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Portfolio");


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Beginning Assets",
                        year.getBeginningInvestableAssets(),
                        getBaselineValue(
                                ProjectionYear::
                                        getBeginningInvestableAssets));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Investment Growth",
                        year.getInvestmentGrowth(),
                        getBaselineValue(
                                ProjectionYear::
                                        getInvestmentGrowth));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Tax-Deferred Accounts",
                        getEndingBalanceByAssetType(
                                year,
                                ProjectionAssetType.TAX_DEFERRED),
                        getBaselineAccountBalanceByAssetType(
                                ProjectionAssetType.TAX_DEFERRED));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Taxable Accounts",
                        getEndingBalanceByTaxTreatment(
                                year,
                                TaxTreatment.TAXABLE),
                        getBaselineAccountBalanceByTaxTreatment(
                                TaxTreatment.TAXABLE));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Roth Accounts",
                        getEndingBalanceByAssetType(
                                year,
                                ProjectionAssetType.ROTH),
                        getBaselineAccountBalanceByAssetType(
                                ProjectionAssetType.ROTH));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Accumulated RMD Cash",
                        year.getUnallocatedCash(),
                        getBaselineValue(
                                ProjectionYear::
                                        getUnallocatedCash));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Cash Accounts",
                        getEndingBalanceByTaxTreatment(
                                year,
                                TaxTreatment.CASH),
                        getBaselineAccountBalanceByTaxTreatment(
                                TaxTreatment.CASH));


        return addMoneyComparisonRow(
                grid,
                row,
                "Ending Assets",
                year.getEndingInvestableAssets(),
                getBaselineValue(
                        ProjectionYear::
                                getEndingInvestableAssets));
    }


    // ============================================================
    // Income & Withdrawals
    // ============================================================

    private int createIncomeRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Income & Withdrawals");


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Guaranteed Income",
                        year.getGuaranteedIncome(),
                        getBaselineValue(
                                ProjectionYear::
                                        getGuaranteedIncome));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Portfolio Withdrawal",
                        year.getPortfolioWithdrawal(),
                        getBaselineValue(
                                ProjectionYear::
                                        getPortfolioWithdrawal));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Roth Conversion",
                        year.getRothConversion(),
                        getBaselineValue(
                                ProjectionYear::
                                        getRothConversion));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Tax Funding Withdrawal",
                        year.getTaxFundingWithdrawal(),
                        getBaselineValue(
                                ProjectionYear::
                                        getTaxFundingWithdrawal));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Required Minimum Distribution",
                        year.getRequiredMinimumDistribution(),
                        getBaselineValue(
                                ProjectionYear::
                                        getRequiredMinimumDistribution));


        return addMoneyComparisonRow(
                grid,
                row,
                "Excess RMD",
                year.getExcessRmd(),
                getBaselineValue(
                        ProjectionYear::
                                getExcessRmd));
    }


    // ============================================================
    // Expenses
    // ============================================================

    private int createExpenseRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Expenses");


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Annual Expenses",
                        year.getAnnualExpenses(),
                        getBaselineValue(
                                ProjectionYear::
                                        getAnnualExpenses));


        return addMoneyComparisonRow(
                grid,
                row,
                "Cash Flow Need",
                year.getCashFlowNeed(),
                getBaselineValue(
                        ProjectionYear::
                                getCashFlowNeed));
    }


    // ============================================================
    // Federal Tax
    // ============================================================

    private int createFederalTaxRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Federal Income Tax");


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Adjusted Gross Income",
                        year.getAdjustedGrossIncome(),
                        getBaselineValue(
                                ProjectionYear::
                                        getAdjustedGrossIncome));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Taxable Social Security",
                        year.getTaxableSocialSecurity(),
                        getBaselineValue(
                                ProjectionYear::
                                        getTaxableSocialSecurity));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Federal Taxable Income",
                        year.getFederalTaxableIncome(),
                        getBaselineValue(
                                ProjectionYear::
                                        getFederalTaxableIncome));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Standard Deduction",
                        year.getFederalStandardDeduction(),
                        getBaselineValue(
                                ProjectionYear::
                                        getFederalStandardDeduction));


        return addMoneyComparisonRow(
                grid,
                row,
                "Federal Income Tax",
                year.getFederalIncomeTax(),
                getBaselineValue(
                        ProjectionYear::
                                getFederalIncomeTax));
    }


    // ============================================================
    // Michigan Tax
    // ============================================================

    private int createMichiganTaxRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Michigan Income Tax");


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Retirement Income",
                        year.getMichiganRetirementIncome(),
                        getBaselineValue(
                                ProjectionYear::
                                        getMichiganRetirementIncome));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Retirement Deduction",
                        year.getMichiganRetirementDeduction(),
                        getBaselineValue(
                                ProjectionYear::
                                        getMichiganRetirementDeduction));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Michigan Taxable Income",
                        year.getMichiganTaxableIncome(),
                        getBaselineValue(
                                ProjectionYear::
                                        getMichiganTaxableIncome));


        return addMoneyComparisonRow(
                grid,
                row,
                "Michigan Income Tax",
                year.getMichiganIncomeTax(),
                getBaselineValue(
                        ProjectionYear::
                                getMichiganIncomeTax));
    }


    // ============================================================
    // Medicare
    // ============================================================

    private int createMedicareRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Medicare");


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Modified Adjusted Gross Income",
                        year.getModifiedAdjustedGrossIncome(),
                        getBaselineValue(
                                ProjectionYear::
                                        getModifiedAdjustedGrossIncome));


        row =
                addTextComparisonRow(
                        grid,
                        row,
                        "IRMAA Bracket",
                        year.getIrmaaBracketDisplay(),
                        getBaselineText(
                                ProjectionYear::
                                        getIrmaaBracketDisplay));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Monthly Part B Premium",
                        year.getMonthlyPartBPremium(),
                        getBaselineValue(
                                ProjectionYear::
                                        getMonthlyPartBPremium));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Annual Part B Premium",
                        year.getAnnualPartBPremium(),
                        getBaselineValue(
                                ProjectionYear::
                                        getAnnualPartBPremium));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Monthly Part D Premium",
                        year.getMonthlyPartDPremium(),
                        getBaselineValue(
                                ProjectionYear::
                                        getMonthlyPartDPremium));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Annual Part D Premium",
                        year.getAnnualPartDPremium(),
                        getBaselineValue(
                                ProjectionYear::
                                        getAnnualPartDPremium));


        return addMoneyComparisonRow(
                grid,
                row,
                "Total Annual Medicare Premium",
                year.getAnnualMedicarePremium(),
                getBaselineValue(
                        ProjectionYear::
                                getAnnualMedicarePremium));
    }


    // ============================================================
    // Totals
    // ============================================================

    private int createTotalsRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Totals");


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Total Income Tax",
                        year.getTotalIncomeTax(),
                        getBaselineValue(
                                ProjectionYear::
                                        getTotalIncomeTax));


        return addPercentageComparisonRow(
                grid,
                row,
                "Combined Effective Tax Rate",
                year.getCombinedEffectiveTaxRate(),
                getBaselineValue(
                        ProjectionYear::
                                getCombinedEffectiveTaxRate));
    }


    // ============================================================
    // Estate
    // ============================================================

    private int createEstateRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Estimated Estate Value");


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Gross Investable Estate",
                        year.getEndingInvestableAssets(),
                        getBaselineValue(
                                ProjectionYear::
                                        getEndingInvestableAssets));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Non-Investable Assets",
                        nonInvestableAssetValue,
                        baselineNonInvestableAssetValue);


        BigDecimal totalGrossEstate =
                year.getEndingInvestableAssets()
                        .add(
                                nonInvestableAssetValue);


        BigDecimal baselineTotalGrossEstate =
                getBaselineTotalGrossEstate();


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Total Gross Estate",
                        totalGrossEstate,
                        baselineTotalGrossEstate);


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Estimated Heir Tax",
                        year.getEstimatedHeirTax(),
                        getBaselineValue(
                                ProjectionYear::
                                        getEstimatedHeirTax));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Projected After-Tax Estate",
                        year.getAfterTaxEstateValue(),
                        getBaselineValue(
                                ProjectionYear::
                                        getAfterTaxEstateValue));


        return addMoneyComparisonRow(
                grid,
                row,
                "Total Net Worth",
                totalGrossEstate,
                baselineTotalGrossEstate);
    }


    // ============================================================
    // Comparison Rows
    // ============================================================

    private int addMoneyComparisonRow(
            GridPane grid,
            int row,
            String description,
            BigDecimal current,
            BigDecimal baseline) {

        addDescription(
                grid,
                description,
                row);


        addRightAligned(
                grid,
                UIFormatters.money(current),
                1,
                row);


        /*
         * No baseline exists.
         *
         * Do not treat this as zero.
         */
        if (baseline == null) {

            addRightAligned(
                    grid,
                    "—",
                    2,
                    row);

            addDifferenceAligned(
                    grid,
                    "—",
                    null,
                    3,
                    row);

            addDifferenceAligned(
                    grid,
                    "—",
                    null,
                    4,
                    row);

            return row + 1;
        }


        BigDecimal difference =
                current.subtract(
                        baseline);


        addRightAligned(
                grid,
                UIFormatters.money(
                        baseline),
                2,
                row);


        addDifferenceAligned(
                grid,
                formatMoneyDifference(
                        difference),
                difference,
                3,
                row);


        addDifferenceAligned(
                grid,
                formatPercentDifference(
                        baseline,
                        difference),
                difference,
                4,
                row);


        return row + 1;
    }


    private int addPercentageComparisonRow(
            GridPane grid,
            int row,
            String description,
            BigDecimal current,
            BigDecimal baseline) {

        addDescription(
                grid,
                description,
                row);


        addRightAligned(
                grid,
                UIFormatters.percent(
                        current),
                1,
                row);


        if (baseline == null) {

            addRightAligned(
                    grid,
                    "—",
                    2,
                    row);

            addDifferenceAligned(
                    grid,
                    "—",
                    null,
                    3,
                    row);

            addDifferenceAligned(
                    grid,
                    "—",
                    null,
                    4,
                    row);

            return row + 1;
        }


        BigDecimal difference =
                current.subtract(
                        baseline);


        addRightAligned(
                grid,
                UIFormatters.percent(
                        baseline),
                2,
                row);


        /*
         * Effective tax rate is shown as a
         * percentage-point difference.
         */
        addDifferenceAligned(
                grid,
                formatPercentagePointDifference(
                        difference),
                difference,
                3,
                row);


        /*
         * We don't show a relative percentage
         * change for a percentage metric.
         */
        addDifferenceAligned(
                grid,
                "—",
                null,
                4,
                row);


        return row + 1;
    }


    private int addTextComparisonRow(
            GridPane grid,
            int row,
            String description,
            String current,
            String baseline) {

        addDescription(
                grid,
                description,
                row);


        addRightAligned(
                grid,
                current,
                1,
                row);


        addRightAligned(
                grid,
                baseline == null
                        ? "—"
                        : baseline,
                2,
                row);


        addDifferenceAligned(
                grid,
                "—",
                null,
                3,
                row);


        addDifferenceAligned(
                grid,
                "—",
                null,
                4,
                row);


        return row + 1;
    }


    private void addDescription(
            GridPane grid,
            String description,
            int row) {

        grid.add(
                new Label(description),
                0,
                row);
    }


    private void addRightAligned(
            GridPane grid,
            String text,
            int column,
            int row) {

        Label label =
                new Label(text);

        GridPane.setHalignment(
                label,
                HPos.RIGHT);

        grid.add(
                label,
                column,
                row);
    }


    private void addDifferenceAligned(
            GridPane grid,
            String text,
            BigDecimal difference,
            int column,
            int row) {

        Label label =
                createDifferenceLabel(
                        text,
                        difference);

        GridPane.setHalignment(
                label,
                HPos.RIGHT);

        grid.add(
                label,
                column,
                row);
    }


    private Label createDifferenceLabel(
            String text,
            BigDecimal difference) {

        Label label =
                new Label(text);


        if (difference == null) {

            label.setStyle(
                    "-fx-text-fill: #64748b;");

            return label;
        }


        int comparison =
                difference.compareTo(
                        BigDecimal.ZERO);


        if (comparison > 0) {

            label.setStyle(
                    "-fx-text-fill: #16a34a; " +
                            "-fx-font-weight: bold;");

        } else if (comparison < 0) {

            label.setStyle(
                    "-fx-text-fill: #dc2626; " +
                            "-fx-font-weight: bold;");

        } else {

            label.setStyle(
                    "-fx-text-fill: #64748b; " +
                            "-fx-font-weight: bold;");
        }


        return label;
    }


    private int addSectionHeader(
            GridPane grid,
            int row,
            String title) {

        Label label =
                new Label(title);

        label.setStyle(
                "-fx-text-fill: #2563eb; " +
                        "-fx-font-size: 14px; " +
                        "-fx-font-weight: bold;");


        grid.add(
                label,
                0,
                row,
                5,
                1);


        return row + 1;
    }


    // ============================================================
    // Baseline Helpers
    // ============================================================

    private BigDecimal getBaselineValue(
            Function<
                    ProjectionYear,
                    BigDecimal> getter) {

        if (baselineYear == null) {
            return null;
        }

        return getter.apply(
                baselineYear);
    }


    private String getBaselineText(
            Function<
                    ProjectionYear,
                    String> getter) {

        if (baselineYear == null) {
            return null;
        }

        return getter.apply(
                baselineYear);
    }


    private BigDecimal
    getBaselineAccountBalanceByAssetType(
            ProjectionAssetType assetType) {

        if (baselineYear == null) {
            return null;
        }

        return getEndingBalanceByAssetType(
                baselineYear,
                assetType);
    }


    private BigDecimal
    getBaselineAccountBalanceByTaxTreatment(
            TaxTreatment taxTreatment) {

        if (baselineYear == null) {
            return null;
        }

        return getEndingBalanceByTaxTreatment(
                baselineYear,
                taxTreatment);
    }


    private BigDecimal
    getBaselineTotalGrossEstate() {

        if (baselineYear == null
                || baselineNonInvestableAssetValue
                == null) {

            return null;
        }

        return baselineYear
                .getEndingInvestableAssets()
                .add(
                        baselineNonInvestableAssetValue);
    }


    // ============================================================
    // Formatting
    // ============================================================

    private String formatMoneyDifference(
            BigDecimal difference) {

        if (difference.compareTo(
                BigDecimal.ZERO) > 0) {

            return "+"
                    + UIFormatters.money(
                    difference);
        }

        return UIFormatters.money(
                difference);
    }


    private String formatPercentDifference(
            BigDecimal baseline,
            BigDecimal difference) {

        if (baseline.compareTo(
                BigDecimal.ZERO) == 0) {

            return "—";
        }


        BigDecimal percent =
                difference
                        .divide(
                                baseline,
                                6,
                                RoundingMode.HALF_UP)
                        .multiply(
                                BigDecimal.valueOf(100));


        String sign =
                percent.compareTo(
                        BigDecimal.ZERO) > 0
                        ? "+"
                        : "";


        return sign
                + percent
                .setScale(
                        1,
                        RoundingMode.HALF_UP)
                .toPlainString()
                + "%";
    }


    private String
    formatPercentagePointDifference(
            BigDecimal difference) {

        String sign =
                difference.compareTo(
                        BigDecimal.ZERO) > 0
                        ? "+"
                        : "";


        return sign
                + UIFormatters.percent(
                difference);
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
                        ProjectedAccountSnapshot::
                                getEndingBalance)
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
                        ProjectedAccountSnapshot::
                                getEndingBalance)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }
}