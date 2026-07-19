package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;

import java.math.BigDecimal;

public class AccountsView extends BorderPane {

    private final TableView<Account> table;

    public AccountsView() {

        table = new TableView<>();

        createColumns();

        setCenter(table);
    }

    private void createColumns() {

        TableColumn<Account, String> nameColumn =
                new TableColumn<>("Name");

        nameColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().getName()));

        nameColumn.setPrefWidth(250);

        TableColumn<Account, String> typeColumn =
                new TableColumn<>("Type");

        typeColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().getType().toString()));

        typeColumn.setPrefWidth(175);

        TableColumn<Account, BigDecimal> balanceColumn =
                new TableColumn<>("Balance");

        balanceColumn.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(
                        cellData.getValue().getCurrentBalance()));

        balanceColumn.setPrefWidth(150);

        table.getColumns().addAll(
                nameColumn,
                typeColumn,
                balanceColumn);

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY);
    }

    public void load(RetirementPlan plan) {

        table.getItems().setAll(
                plan.getAccountPortfolio().getAccounts());
    }

    public void save(RetirementPlan plan) {
        // Nothing to do.
        // The table displays the domain objects directly.
    }

    public TableView<Account> getTable() {
        return table;
    }
}