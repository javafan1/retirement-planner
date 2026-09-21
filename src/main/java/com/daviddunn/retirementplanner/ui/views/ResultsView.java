package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.ProjectedAccountSnapshot;
import com.daviddunn.retirementplanner.domain.projection.ProjectionAssetType;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;

import com.daviddunn.retirementplanner.ui.util.UIFormatters;
import com.daviddunn.retirementplanner.domain.breakeven.BreakEvenAnalysisResult;
import com.daviddunn.retirementplanner.ui.breakeven.BreakEvenAnalysisDialog;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;

import com.daviddunn.retirementplanner.ui.summary.ProjectionYearDetailsRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ResultsView extends BorderPane {

    private final TableView<ProjectionYear> table;

    private final Label summaryLabel;
    private final Button breakEvenButton = new Button("Break-Even Analysis");
    private BreakEvenAnalysisResult breakEvenAnalysis;
    private Consumer<BreakEvenAnalysisResult> breakEvenHandler;

    public void setOnBreakEvenAnalysis(Consumer<BreakEvenAnalysisResult> handler) {
        breakEvenHandler = handler;
    }

    private List<NonInvestableAssetProjection>
            nonInvestableAssetProjections =
            List.of();

    /**
     * Invoked when the user double-clicks a projection year.
     */
    private List<ProjectionYear> detailsYears = List.of();

    private Consumer<ProjectionYearDetailsRequest> yearDoubleClickHandler;

    public ResultsView() {

        table =
                new TableView<>();

        summaryLabel =
                new Label();

        createColumns();
        configureRowFactory();

        table.setPlaceholder(
                new Label("No projection available."));

        setPadding(
                new Insets(10));

        breakEvenButton.setId("break-even-action");
        breakEvenButton.setDisable(true);
        breakEvenButton.setOnAction(event -> {
            if (breakEvenAnalysis == null || breakEvenAnalysis.comparableYearCount() == 0) return;
            if (breakEvenHandler != null) {
                breakEvenHandler.accept(breakEvenAnalysis);
                return;
            }
            var dialog = new BreakEvenAnalysisDialog(breakEvenAnalysis);
            if (getScene() != null) dialog.initOwner(getScene().getWindow());
            dialog.showAndWait();
        });
        setTop(new HBox(18, summaryLabel, breakEvenButton));
        setCenter(table);

        BorderPane.setMargin(
                summaryLabel,
                new Insets(0, 0, 10, 0));
    }

    private void createColumns() {

        TableColumn<ProjectionYear, Integer> yearColumn =
                new TableColumn<>("Year");

        yearColumn.setCellValueFactory(data ->
                new ReadOnlyObjectWrapper<>(
                        data.getValue()
                                .getCalendarYear()));


        TableColumn<ProjectionYear, Integer> ageColumn =
                createIntegerColumn(
                        "Age",
                        ProjectionYear::getPrimaryPersonAge);


        TableColumn<ProjectionYear, BigDecimal> beginningAssetsColumn =
                createMoneyColumn(
                        "Beginning Assets",
                        ProjectionYear::getBeginningInvestableAssets);

        TableColumn<ProjectionYear, BigDecimal> growthColumn =
                createMoneyColumn(
                        "Investment Growth",
                        ProjectionYear::getInvestmentGrowth);

        TableColumn<ProjectionYear, BigDecimal> incomeColumn =
                createMoneyColumn(
                        "Income",
                        ProjectionYear::getGuaranteedIncome);

        TableColumn<ProjectionYear, BigDecimal> expensesColumn =
                createMoneyColumn(
                        "Expenses",
                        ProjectionYear::getAnnualExpenses);

        TableColumn<ProjectionYear, BigDecimal> federalTaxColumn =
                createMoneyColumn(
                        "Federal Tax",
                        ProjectionYear::getFederalIncomeTax);

        TableColumn<ProjectionYear, BigDecimal> combinedEffectiveTaxRateColumn =
                createPercentageColumn(
                        "Effective Tax Rate",
                        ProjectionYear::getCombinedEffectiveTaxRate);


        TableColumn<ProjectionYear, BigDecimal> michiganTaxColumn =
                createMoneyColumn(
                        "Michigan Tax",
                        ProjectionYear::getMichiganIncomeTax);

        TableColumn<ProjectionYear, BigDecimal> totalTaxColumn =
                createMoneyColumn(
                        "Total Tax",
                        ProjectionYear::getTotalIncomeTax);

        TableColumn<ProjectionYear, BigDecimal> cashFlowColumn =
                createMoneyColumn(
                        "Cash Needed",
                        ProjectionYear::getCashFlowNeed);

        TableColumn<ProjectionYear, BigDecimal> rmdColumn =
                createMoneyColumn(
                        "RMD",
                        ProjectionYear::getRequiredMinimumDistribution);

        TableColumn<ProjectionYear, BigDecimal> withdrawalColumn =
                createMoneyColumn(
                        "Withdrawal",
                        ProjectionYear::getPortfolioWithdrawal);


        TableColumn<ProjectionYear, String> medicareColumn =
                new TableColumn<>("Medicare");

        medicareColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        UIFormatters.money(
                                cellData.getValue()
                                        .getAnnualMedicarePremium())));


        TableColumn<ProjectionYear, BigDecimal> taxDeferredColumn =
                createMoneyColumn(
                        "Tax-Deferred",
                        year ->
                                getEndingBalanceByAssetType(
                                        year,
                                        ProjectionAssetType.TAX_DEFERRED));

        TableColumn<ProjectionYear, BigDecimal>
                nonInvestableAssetsColumn =
                createMoneyColumn(
                        "Non-Investable Assets",
                        year ->
                                getNonInvestableAssetValue(
                                        year.getCalendarYear()));

        TableColumn<ProjectionYear, BigDecimal>
                netWorthColumn =
                createMoneyColumn(
                        "Net Worth",
                        this::getNetWorth);


        /*
         * Roth conversion performed during the year.
         *
         * This is the annual conversion amount,
         * not the ending Roth account balance.
         */
        TableColumn<ProjectionYear, BigDecimal> rothConversionColumn =
                createMoneyColumn(
                        "Roth Conversion",
                        ProjectionYear::getRothConversion);


        TableColumn<ProjectionYear, BigDecimal> rothColumn =
                createMoneyColumn(
                        "Roth",
                        year ->
                                getEndingBalanceByAssetType(
                                        year,
                                        ProjectionAssetType.ROTH));

        TableColumn<ProjectionYear, BigDecimal> accumulatedRmdCashColumn =
                createMoneyColumn(
                        "Retained Non-Qualified Assets",
                        ProjectionYear::getEndingRetainedNonQualifiedAssets);


        TableColumn<ProjectionYear, BigDecimal> endingAssetsColumn =
                createMoneyColumn(
                        "Ending Assets",
                        ProjectionYear::getEndingInvestableAssets);

        TableColumn<ProjectionYear, BigDecimal> afterTaxEstateValueColumn =
                createMoneyColumn(
                        "Estate Value",
                        ProjectionYear::getAfterTaxEstateValue);

        table.getColumns().addAll(
                yearColumn,
                ageColumn,
                beginningAssetsColumn,
                growthColumn,
                incomeColumn,
                expensesColumn,
                rmdColumn,
                cashFlowColumn,
                withdrawalColumn,
                rothConversionColumn,
                federalTaxColumn,
                michiganTaxColumn,
                combinedEffectiveTaxRateColumn,
                totalTaxColumn,
                medicareColumn,
                taxDeferredColumn,
                rothColumn,
                accumulatedRmdCashColumn,
                nonInvestableAssetsColumn,
                endingAssetsColumn,
                afterTaxEstateValueColumn,
                netWorthColumn);

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    /**
     * Configures double-click handling for projection years.
     */
    private void configureRowFactory() {

        table.setRowFactory(tv -> {

            TableRow<ProjectionYear> row =
                    new TableRow<>();

            row.setOnMouseClicked(event -> {

                if (event.getClickCount() == 2
                        && !row.isEmpty()
                        && yearDoubleClickHandler != null) {

                    yearDoubleClickHandler.accept(
                            new ProjectionYearDetailsRequest(
                                    detailsYears,
                                    row.getItem(),
                                    nonInvestableAssetProjections));
                }
            });

            return row;
        });
    }

    private TableColumn<ProjectionYear, BigDecimal> createMoneyColumn(
            String title,
            Function<ProjectionYear, BigDecimal> valueFunction) {

        TableColumn<ProjectionYear, BigDecimal> column =
                new TableColumn<>(title);

        column.setCellValueFactory(data ->
                new ReadOnlyObjectWrapper<>(
                        valueFunction.apply(
                                data.getValue())));

        column.setCellFactory(c ->
                new TableCell<>() {

                    @Override
                    protected void updateItem(
                            BigDecimal value,
                            boolean empty) {

                        super.updateItem(value, empty);

                        if (empty || value == null) {
                            setText(null);
                        } else {
                            setText(UIFormatters.money(value));
                        }
                    }
                });

        return column;
    }

    public void load(
            Projection projection,
            List<NonInvestableAssetProjection>
                    nonInvestableAssetProjections) {

        setBreakEvenAnalysis(null);

        detailsYears = projection == null ? List.of() : projection.getYears();

        this.nonInvestableAssetProjections =
                nonInvestableAssetProjections != null
                        ? List.copyOf(nonInvestableAssetProjections)
                        : List.of();

        if (projection == null) {

            table.getItems().clear();
            summaryLabel.setText("");

            return;
        }

        table.getItems().setAll(
                projection.getYears());

        updateSummary(projection);
    }

    private void updateSummary(
            Projection projection) {

        if (projection.getYears().isEmpty()) {

            summaryLabel.setText(
                    "No projection results.");

            return;
        }

        ProjectionYear firstYear =
                projection.getYears().get(0);

        ProjectionYear lastYear =
                projection.getYears().get(
                        projection.getYears().size() - 1);

        summaryLabel.setText(
                "Projection: "
                        + firstYear.getCalendarYear()
                        + " - "
                        + lastYear.getCalendarYear());
    }

    /**
     * Registers a handler invoked when the user
     * double-clicks a projection year.
     */
    public void setOnYearDoubleClick(
            Consumer<ProjectionYearDetailsRequest> handler) {

        this.yearDoubleClickHandler = handler;
    }

    public TableView<ProjectionYear> getTable() {
        return table;
    }

    public void setBreakEvenAnalysis(BreakEvenAnalysisResult analysis) {
        breakEvenAnalysis = analysis;
        breakEvenButton.setDisable(analysis == null || analysis.comparableYearCount() == 0);
    }

    private TableColumn<ProjectionYear, Integer> createIntegerColumn(
            String title,
            Function<ProjectionYear, Integer> valueProvider) {

        TableColumn<ProjectionYear, Integer> column =
                new TableColumn<>(title);

        column.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleObjectProperty<>(
                        valueProvider.apply(cellData.getValue())));

        column.setCellFactory(col ->
                new TableCell<>() {

                    @Override
                    protected void updateItem(
                            Integer value,
                            boolean empty) {

                        super.updateItem(value, empty);

                        if (empty || value == null) {
                            setText(null);
                        } else {
                            setText(value.toString());
                        }

                        setAlignment(Pos.CENTER);
                    }
                });

        return column;
    }

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
                .map(ProjectedAccountSnapshot::getEndingBalance)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add);
    }

    private TableColumn<ProjectionYear, BigDecimal> createPercentageColumn(
            String title,
            Function<ProjectionYear, BigDecimal> valueFunction) {

        TableColumn<ProjectionYear, BigDecimal> column =
                new TableColumn<>(title);

        column.setCellValueFactory(data ->
                new ReadOnlyObjectWrapper<>(
                        valueFunction.apply(
                                data.getValue())));

        column.setCellFactory(c ->
                new TableCell<>() {

                    @Override
                    protected void updateItem(
                            BigDecimal value,
                            boolean empty) {

                        super.updateItem(value, empty);

                        if (empty || value == null) {
                            setText(null);
                        } else {
                            setText(
                                    UIFormatters.percent(
                                            value));
                        }
                    }
                });

        return column;
    }

    private BigDecimal getNonInvestableAssetValue(
            int calendarYear) {

        return nonInvestableAssetProjections
                .stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .map(NonInvestableAssetProjection::
                        getTotalValue)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal getNetWorth(
            ProjectionYear year) {

        return year.getEndingInvestableAssets()
                .add(
                        getNonInvestableAssetValue(
                                year.getCalendarYear()));
    }

}
