
package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountFactory;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;

public class AccountDialog extends Dialog<Account> {

    private final TextField nameField;
    private final ComboBox<AccountType> typeCombo;
    private final ComboBox<AccountOwnership> ownershipCombo;
    private final TextField balanceField;

    public AccountDialog(Account account) {

        if (account == null) {
            setTitle("Add Account");
            setHeaderText("Enter account information.");
        } else {
            setTitle("Edit Account");
            setHeaderText("Update account information.");
        }

        nameField = new TextField();

        typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(AccountType.values());
        typeCombo.getSelectionModel().selectFirst();

        ownershipCombo = new ComboBox<>();
        ownershipCombo.getItems().addAll(AccountOwnership.values());
        ownershipCombo.getSelectionModel().selectFirst();

        balanceField = new TextField();

        if (account != null) {
            nameField.setText(account.getName());
            typeCombo.setValue(account.getType());
            ownershipCombo.setValue(account.getOwnership());
            balanceField.setText(
                    account.getCurrentBalance().toPlainString());
        }

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(15));
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);

        grid.add(new Label("Type:"), 0, 1);
        grid.add(typeCombo, 1, 1);

        grid.add(new Label("Owner:"), 0, 2);
        grid.add(ownershipCombo, 1, 2);

        grid.add(new Label("Current Balance:"), 0, 3);
        grid.add(balanceField, 1, 3);

        getDialogPane().setContent(grid);

        getDialogPane().getButtonTypes().addAll(
                ButtonType.OK,
                ButtonType.CANCEL);

        setResultConverter(button -> {

            if (button != ButtonType.OK) {
                return null;
            }

            String name = nameField.getText().trim();
            AccountType type = typeCombo.getValue();
            AccountOwnership ownership = ownershipCombo.getValue();

            BigDecimal balance =
                    new BigDecimal(balanceField.getText().trim());

            return AccountFactory.create(
                    type,
                    name,
                    ownership,
                    balance);
        });
    }
}
/*
package com.daviddunn.retirementplanner.ui.dialogs;


import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountFactory;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;

public class AccountDialog extends Dialog<Account> {

    private final TextField nameField;
    private final ComboBox<AccountType> typeCombo;
    private final TextField balanceField;

    public AccountDialog(Account account) {

        if (account == null) {
            setTitle("Add Account");
            setHeaderText("Enter account information.");
        }
        else {
            setTitle("Edit Account");
            setHeaderText("Update account information.");
        }

        nameField = new TextField();

        typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(AccountType.values());
        typeCombo.getSelectionModel().selectFirst();

        balanceField = new TextField();

        if (account != null) {
            nameField.setText(account.getName());
            typeCombo.setValue(account.getType());
            balanceField.setText(
                    account.getCurrentBalance().toPlainString());
        }

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(15));
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);

        grid.add(new Label("Type:"), 0, 1);
        grid.add(typeCombo, 1, 1);

        grid.add(new Label("Current Balance:"), 0, 2);
        grid.add(balanceField, 1, 2);

        getDialogPane().setContent(grid);

        getDialogPane().getButtonTypes().addAll(
                ButtonType.OK,
                ButtonType.CANCEL);

        setResultConverter(button -> {

            if (button != ButtonType.OK) {
                return null;
            }

            String name = nameField.getText().trim();

            AccountType type = typeCombo.getValue();

            BigDecimal balance =
                    new BigDecimal(balanceField.getText().trim());

            return AccountFactory.create(
                    type,
                    name,
                    balance);
        });
    }
}

 */