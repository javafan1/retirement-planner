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
                createCashFlowRows(
                        grid,
                        row);

        row =
                createIncomeSourceRows(
                        grid,
                        row);

        row =
                createRetirementActivityRows(
                        grid,
                        row);

        row =
                createTaxRows(
                        grid,
                        row);

        row =
                createMedicareRows(
                        grid,
                        row);

        row =
                createPortfolioRows(
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
    // Year Cash Flow
    // ============================================================

    private int createCashFlowRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Year Cash Flow");

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
                        "Annual Expenses",
                        year.getAnnualExpenses(),
                        getBaselineValue(
                                ProjectionYear::
                                        getAnnualExpenses));

        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Cash Flow Need",
                        year.getCashFlowNeed(),
                        getBaselineValue(
                                ProjectionYear::getCashFlowNeed));

        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Portfolio Withdrawal",
                        year.getPortfolioWithdrawal(),
                        getBaselineValue(
                                ProjectionYear::getPortfolioWithdrawal));

        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Tax Funding Withdrawal",
                        year.getTaxFundingWithdrawal(),
                        getBaselineValue(
                                ProjectionYear::getTaxFundingWithdrawal));

        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Total Income Tax",
                        year.getTotalIncomeTax(),
                        getBaselineValue(
                                ProjectionYear::getTotalIncomeTax));

        return addMoneyComparisonRow(
                grid,
                row,
                "Total Annual Medicare Premium",
                year.getAnnualMedicarePremium(),
                getBaselineValue(
                        ProjectionYear::getAnnualMedicarePremium));
    }


    // ============================================================
    // Income Sources
    // ============================================================

    private int createIncomeSourceRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Income Sources");

        row =
                addSubsectionHeader(
                        grid,
                        row,
                        "Social Security");

        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Primary Own Benefit / Candidate",
                        year.getSocialSecurityResult()
                                .primaryOwnBenefit(),
                        getBaselineValue(value ->
                                value.getSocialSecurityResult()
                                        .primaryOwnBenefit()));

        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Spouse Own Benefit / Candidate",
                        year.getSocialSecurityResult()
                                .spouseOwnBenefit(),
                        getBaselineValue(value ->
                                value.getSocialSecurityResult()
                                        .spouseOwnBenefit()));

        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Primary Survivor Candidate",
                        year.getSocialSecurityResult()
                                .primarySurvivorCandidate(),
                        getBaselineValue(value ->
                                value.getSocialSecurityResult()
                                        .primarySurvivorCandidate()));

        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Spouse Survivor Candidate",
                        year.getSocialSecurityResult()
                                .spouseSurvivorCandidate(),
                        getBaselineValue(value ->
                                value.getSocialSecurityResult()
                                        .spouseSurvivorCandidate()));

        row =
                addTextComparisonRow(
                        grid,
                        row,
                        "Primary Selected Benefit",
                        year.getSocialSecurityResult()
                                .primarySelection()
                                .getDisplayName(),
                        getBaselineText(value ->
                                value.getSocialSecurityResult()
                                        .primarySelection()
                                        .getDisplayName()));

        row =
                addTextComparisonRow(
                        grid,
                        row,
                        "Spouse Selected Benefit",
                        year.getSocialSecurityResult()
                                .spouseSelection()
                                .getDisplayName(),
                        getBaselineText(value ->
                                value.getSocialSecurityResult()
                                        .spouseSelection()
                                        .getDisplayName()));

        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Household Social Security Received",
                        year.getSocialSecurityResult()
                                .householdBenefit(),
                        getBaselineValue(value ->
                                value.getSocialSecurityResult()
                                        .householdBenefit()));

        return row;
    }


    // ============================================================
    // Retirement Account Activity
    // ============================================================

    private int createRetirementActivityRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Retirement Account Activity");

        row =
                addSubsectionHeader(
                        grid,
                        row,
                        "Roth Conversions");

        row = addMoneyComparisonRow(
                grid, row, "Requested Roth Conversion",
                year.getRequestedRothConversion(), null);
        row = addMoneyComparisonRow(
                grid, row, "Roth Conversion",
                year.getRothConversion(),
                getBaselineValue(ProjectionYear::getRothConversion));
        row = addMoneyComparisonRow(
                grid, row, "Roth Conversion Shortfall",
                year.getRothConversionShortfall(), null);
        row = addMoneyComparisonRow(
                grid, row, "Primary Roth Conversion",
                year.getPrimaryRothConversion(), null);
        row = addMoneyComparisonRow(
                grid, row, "Spouse Roth Conversion",
                year.getSpouseRothConversion(), null);

        row = addSubsectionHeader(
                grid, row, "Required Minimum Distributions");

        row = addMoneyComparisonRow(
                grid, row, "Required Minimum Distribution",
                year.getRequiredMinimumDistribution(),
                getBaselineValue(
                        ProjectionYear::getRequiredMinimumDistribution));
        row = addMoneyComparisonRow(
                grid, row, "RMD Distributed Before Projection",
                year.getRmdDistributedBeforeProjection(),
                getBaselineValue(
                        ProjectionYear::getRmdDistributedBeforeProjection));
        row = addMoneyComparisonRow(
                grid, row, "RMD Distributed in Projection",
                year.getRmdDistributedInProjection(),
                getBaselineValue(
                        ProjectionYear::getRmdDistributedInProjection));

        return addMoneyComparisonRow(
                grid, row, "Excess RMD",
                year.getExcessRmd(),
                getBaselineValue(ProjectionYear::getExcessRmd));
    }


    // ============================================================
    // Taxes
    // ============================================================

    private int createTaxRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Taxes");

        row = createFederalTaxRows(grid, row);
        row = createMichiganTaxRows(grid, row);

        row = addSubsectionHeader(
                grid, row, "Tax Summary");

        row = addMoneyComparisonRow(
                grid, row, "Total Income Tax",
                year.getTotalIncomeTax(),
                getBaselineValue(ProjectionYear::getTotalIncomeTax));

        return addPercentageComparisonRow(
                grid, row, "Combined Effective Tax Rate",
                year.getCombinedEffectiveTaxRate(),
                getBaselineValue(
                        ProjectionYear::getCombinedEffectiveTaxRate));
    }

    private int createFederalTaxRows(
            GridPane grid,
            int row) {

        row =
                addSubsectionHeader(
                        grid,
                        row,
                        "Federal");


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
                        "Standard Deduction",
                        year.getFederalStandardDeduction(),
                        getBaselineValue(
                                ProjectionYear::
                                        getFederalStandardDeduction));


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Federal Taxable Income",
                        year.getFederalTaxableIncome(),
                        getBaselineValue(
                                ProjectionYear::
                                        getFederalTaxableIncome));


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
    private int createMichiganTaxRows(
            GridPane grid,
            int row) {

        row =
                addSubsectionHeader(
                        grid,
                        row,
                        "Michigan");


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
    // Medicare / IRMAA
    // ============================================================

    private int createMedicareRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Medicare / IRMAA");


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
                addSubsectionHeader(
                        grid,
                        row,
                        "Part B");

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
                addSubsectionHeader(
                        grid,
                        row,
                        "Part D");

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
    // Portfolio
    // ============================================================

    private int createPortfolioRows(
            GridPane grid,
            int row) {

        row = addSectionHeader(grid, row, "Portfolio");
        row = addSubsectionHeader(
                grid, row, "Portfolio Activity");

        row = addMoneyComparisonRow(
                grid, row, "Beginning Assets",
                year.getBeginningInvestableAssets(),
                getBaselineValue(
                        ProjectionYear::getBeginningInvestableAssets));
        row = addMoneyComparisonRow(
                grid, row, "Investment Growth",
                year.getInvestmentGrowth(),
                getBaselineValue(ProjectionYear::getInvestmentGrowth));
        row = addMoneyComparisonRow(
                grid, row, "Ending Assets",
                year.getEndingInvestableAssets(),
                getBaselineValue(
                        ProjectionYear::getEndingInvestableAssets));

        row = addSubsectionHeader(
                grid, row, "Ending Portfolio Composition");

        row = addMoneyComparisonRow(
                grid, row, "Tax-Deferred Accounts",
                getEndingBalanceByAssetType(
                        year, ProjectionAssetType.TAX_DEFERRED),
                getBaselineAccountBalanceByAssetType(
                        ProjectionAssetType.TAX_DEFERRED));
        row = addMoneyComparisonRow(
                grid, row, "Roth Accounts",
                getEndingBalanceByAssetType(
                        year, ProjectionAssetType.ROTH),
                getBaselineAccountBalanceByAssetType(
                        ProjectionAssetType.ROTH));
        row = addMoneyComparisonRow(
                grid, row, "Taxable Accounts",
                getEndingBalanceByTaxTreatment(
                        year, TaxTreatment.TAXABLE),
                getBaselineAccountBalanceByTaxTreatment(
                        TaxTreatment.TAXABLE));
        row = addMoneyComparisonRow(
                grid, row, "Cash Accounts",
                getEndingBalanceByTaxTreatment(
                        year, TaxTreatment.CASH),
                getBaselineAccountBalanceByTaxTreatment(
                        TaxTreatment.CASH));

        row = addSubsectionHeader(
                grid, row, "Retained RMD Assets");

        row = addMoneyComparisonRow(
                grid, row, "Beginning Retained RMD Assets",
                year.getBeginningRetainedRmdAssets(),
                getBaselineValue(
                        ProjectionYear::getBeginningRetainedRmdAssets));
        row = addMoneyComparisonRow(
                grid, row, "Growth on Retained RMD Assets",
                year.getRetainedRmdAssetGrowth(),
                getBaselineValue(
                        ProjectionYear::getRetainedRmdAssetGrowth));
        row = addMoneyComparisonRow(
                grid, row, "New Excess RMD",
                year.getExcessRmd(),
                getBaselineValue(ProjectionYear::getExcessRmd));

        return addMoneyComparisonRow(
                grid, row, "Ending Retained RMD Assets",
                year.getEndingRetainedRmdAssets(),
                getBaselineValue(
                        ProjectionYear::getEndingRetainedRmdAssets));
    }


    // ============================================================
    // Estate & Net Worth
    // ============================================================

    private int createEstateRows(
            GridPane grid,
            int row) {

        row =
                addSectionHeader(
                        grid,
                        row,
                        "Estate & Net Worth");


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Ending Investable Assets",
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


        BigDecimal totalNetWorth =
                year.getEndingInvestableAssets()
                        .add(
                                nonInvestableAssetValue);


        BigDecimal baselineTotalNetWorth =
                getBaselineTotalGrossEstate();

        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Total Net Worth",
                        totalNetWorth,
                        baselineTotalNetWorth);


        row =
                addMoneyComparisonRow(
                        grid,
                        row,
                        "Estimated Heir Tax",
                        year.getEstimatedHeirTax(),
                        getBaselineValue(
                                ProjectionYear::
                                        getEstimatedHeirTax));


        return addMoneyComparisonRow(
                grid,
                row,
                "Projected After-Tax Estate",
                year.getAfterTaxEstateValue(),
                getBaselineValue(
                        ProjectionYear::
                                getAfterTaxEstateValue));
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


    private int addSubsectionHeader(
            GridPane grid,
            int row,
            String title) {

        Label label =
                new Label(title);

        label.setStyle(
                "-fx-text-fill: #334155; " +
                        "-fx-font-size: 12px; " +
                        "-fx-font-weight: bold;");

        GridPane.setMargin(
                label,
                new Insets(
                        8,
                        0,
                        0,
                        10));

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
