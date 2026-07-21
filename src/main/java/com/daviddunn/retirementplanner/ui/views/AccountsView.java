package com.daviddunn.retirementplanner.ui.views;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountPortfolio;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.ui.dialogs.AccountDialog;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Optional;

public class AccountsView extends BorderPane {

    private final TableView<Account> table;

    private final Button addButton;
    private final Button editButton;
    private final Button removeButton;

    private RetirementPlan currentPlan;

    public AccountsView() {

        table = new TableView<>();

        createColumns();

        addButton = new Button("Add");
        editButton = new Button("Edit");
        removeButton = new Button("Remove");

        editButton.disableProperty().bind(
                table.getSelectionModel()
                        .selectedItemProperty()
                        .isNull());

        removeButton.disableProperty().bind(
                table.getSelectionModel()
                        .selectedItemProperty()
                        .isNull());

        addButton.setOnAction(e -> onAdd());
        editButton.setOnAction(e -> onEdit());
        removeButton.setOnAction(e -> onRemove());

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

        TableColumn<Account, String> nameColumn =
                new TableColumn<>("Name");

        nameColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().getName()));

        TableColumn<Account, String> typeColumn =
                new TableColumn<>("Type");

        typeColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().getType().toString()));

        TableColumn<Account, BigDecimal> balanceColumn =
                new TableColumn<>("Balance");

        balanceColumn.setCellValueFactory(cellData ->
                 new ReadOnlyObjectWrapper<>(
                         cellData.getValue().getCurrentBalance()));

        balanceColumn.setCellFactory(column ->
                new TableCell<>() {

                    @Override
                    protected void updateItem(BigDecimal value,
                                              boolean empty) {

                        super.updateItem(value, empty);

                        if (empty || value == null) {
                            setText(null);
                        } else {
                            setText(NumberFormat
                                    .getCurrencyInstance()
                                    .format(value));
                        }
                    }
                });

        table.getColumns().addAll(
                nameColumn,
                typeColumn,
                balanceColumn);

        table.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    public void load(RetirementPlan plan) {

        currentPlan = plan;

        refreshTable();
    }

    public void save(RetirementPlan plan) {

        // Nothing to do.
        // All changes are applied directly to the domain model.
    }

    private void refreshTable() {

        if (currentPlan == null) {
            table.getItems().clear();
            return;
        }

        table.getItems().setAll(
                getPortfolio().getAccounts());
    }

    private AccountPortfolio getPortfolio() {
        return currentPlan.getAccountPortfolio();
    }

    private Account getSelectedAccount() {
        return table.getSelectionModel().getSelectedItem();
    }

    private void onAdd() {

        AccountDialog dialog =
                new AccountDialog(null);

        Optional<Account> result =
                dialog.showAndWait();

        result.ifPresent(account -> {

            getPortfolio().add(account);

            refreshTable();
        });
    }

    private void onEdit() {

        Account selected = getSelectedAccount();

        if (selected == null) {
            return;
        }

        AccountDialog dialog =
                new AccountDialog(selected);

        Optional<Account> result =
                dialog.showAndWait();

        result.ifPresent(account -> {

            getPortfolio().replace(
                    selected,
                    account);

            refreshTable();
        });
    }
    private void onRemove() {

        Account selected = getSelectedAccount();

        if (selected == null) {
            return;
        }

        getPortfolio().remove(selected);

        refreshTable();
    }

    public TableView<Account> getTable() {
        return table;
    }
}