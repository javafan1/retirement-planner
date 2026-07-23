package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.dialogs.ExpenseDialog;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.text.NumberFormat;
import java.util.Optional;

public class ExpensesView extends BorderPane {

    private final TableView<Expense> table;

    private final Button addButton;
    private final Button editButton;
    private final Button removeButton;

    private RetirementPlan currentPlan;

    private final NumberFormat currency =
            NumberFormat.getCurrencyInstance();

    public ExpensesView() {

        table = new TableView<>();

        createColumns();

        addButton = new Button("Add");
        editButton = new Button("Edit");
        removeButton = new Button("Remove");

        addButton.setOnAction(e -> onAdd());
        editButton.setOnAction(e -> onEdit());
        removeButton.setOnAction(e -> onRemove());

        editButton.disableProperty().bind(
                table.getSelectionModel()
                        .selectedItemProperty()
                        .isNull());

        removeButton.disableProperty().bind(
                table.getSelectionModel()
                        .selectedItemProperty()
                        .isNull());

        HBox buttonBar = new HBox(10);
        buttonBar.setPadding(new Insets(10));

        buttonBar.getChildren().addAll(
                addButton,
                editButton,
                removeButton);

        setCenter(table);
        setBottom(buttonBar);
    }

    private void createColumns() {

        TableColumn<Expense, String> descriptionColumn =
                new TableColumn<>("Description");

        descriptionColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue()
                                .getDescription()));

        TableColumn<Expense, String> amountColumn =
                new TableColumn<>("Annual Amount");

        amountColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        currency.format(
                                cellData.getValue()
                                        .getAnnualAmount())));

        table.getColumns().addAll(
                descriptionColumn,
                amountColumn);

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    public void load(RetirementPlan plan) {

        currentPlan = plan;

        refreshTable();
    }

    public void save(RetirementPlan plan) {

        /*
         * Nothing required here.
         *
         * Add/Edit/Remove modify the
         * Household directly.
         */
    }

    private void refreshTable() {

        table.getItems().clear();

        if (currentPlan == null) {
            return;
        }

        table.getItems().addAll(
                currentPlan
                        .getHousehold()
                        .getExpenses());
    }

    private void onAdd() {

        if (currentPlan == null) {
            return;
        }

        ExpenseDialog dialog =
                new ExpenseDialog(null);

        Optional<Expense> result =
                dialog.showAndWait();

        result.ifPresent(expense -> {

            getHousehold()
                    .addExpense(expense);

            refreshTable();
        });
    }

    private void onEdit() {

        Expense selected =
                getSelectedExpense();

        if (selected == null) {
            return;
        }

        ExpenseDialog dialog =
                new ExpenseDialog(selected);

        Optional<Expense> result =
                dialog.showAndWait();

        result.ifPresent(updated -> {

            getHousehold()
                    .replaceExpense(
                            selected,
                            updated);

            refreshTable();
        });
    }

    private void onRemove() {

        Expense selected =
                getSelectedExpense();

        if (selected == null) {
            return;
        }

        getHousehold()
                .removeExpense(selected);

        refreshTable();
    }

    private Expense getSelectedExpense() {

        return table
                .getSelectionModel()
                .getSelectedItem();
    }

    private Household getHousehold() {

        return currentPlan.getHousehold();
    }

    public TableView<Expense> getTable() {
        return table;
    }
}