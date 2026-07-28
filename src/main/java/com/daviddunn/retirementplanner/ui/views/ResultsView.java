package com.daviddunn.retirementplanner.ui.views;

//import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
//import com.daviddunn.retirementplanner.domain.projection.ProjectionEngine;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;

import java.math.BigDecimal;
import java.text.NumberFormat;

public class ResultsView extends BorderPane {

    private final TableView<ProjectionYear> table;

    //private final ProjectionEngine projectionEngine;

    private final Label summaryLabel;

    public ResultsView() {

        //projectionEngine =
        //        new ProjectionEngine();

        table =
                new TableView<>();

        summaryLabel =
                new Label();

        createColumns();

        setPadding(
                new Insets(10));

        setTop(summaryLabel);
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
                        "Guaranteed Income",
                        ProjectionYear::getGuaranteedIncome);

        TableColumn<ProjectionYear, BigDecimal> expensesColumn =
                createMoneyColumn(
                        "Expenses",
                        ProjectionYear::getAnnualExpenses);

        TableColumn<ProjectionYear, BigDecimal> withdrawalColumn =
                createMoneyColumn(
                        "Portfolio Withdrawal",
                        ProjectionYear::getPortfolioWithdrawal);

        TableColumn<ProjectionYear, BigDecimal> endingAssetsColumn =
                createMoneyColumn(
                        "Ending Assets",
                        ProjectionYear::getEndingInvestableAssets);

        table.getColumns().addAll(
                yearColumn,
                beginningAssetsColumn,
                growthColumn,
                incomeColumn,
                expensesColumn,
                withdrawalColumn,
                endingAssetsColumn);

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    private TableColumn<ProjectionYear, BigDecimal> createMoneyColumn(
            String title,
            java.util.function.Function<
                    ProjectionYear,
                    BigDecimal> valueFunction) {

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

                        super.updateItem(
                                value,
                                empty);

                        if (empty || value == null) {

                            setText(null);

                        } else {

                            setText(
                                    NumberFormat
                                            .getCurrencyInstance()
                                            .format(value));
                        }
                    }
                });

        return column;
    }

    public void load(Projection projection) {

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

    public TableView<ProjectionYear> getTable() {
        return table;
    }
}