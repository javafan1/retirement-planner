package com.daviddunn.retirementplanner.ui.dialogs;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.domain.financial.AccountFactory;
import com.daviddunn.retirementplanner.domain.financial.InheritedAccountInformation;
import com.daviddunn.retirementplanner.domain.financial.InheritedRothIRA;
import com.daviddunn.retirementplanner.domain.financial.InheritedTraditionalIRA;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.domain.model.AccountType;
import com.daviddunn.retirementplanner.domain.model.BeneficiaryRelationship;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import javafx.application.Platform;

public class AccountDialog extends Dialog<Account> {

    private final TextField nameField;
    private final ComboBox<AccountType> typeCombo;
    private final ComboBox<AccountOwnership> ownershipCombo;
    private final TextField balanceField;

    private final Label originalOwnerDobLabel;
    private final DatePicker originalOwnerDobPicker;

    private final Label originalOwnerDeathLabel;
    private final DatePicker originalOwnerDeathPicker;

    private final Label beneficiaryRelationshipLabel;
    private final ComboBox<BeneficiaryRelationship>
            beneficiaryRelationshipCombo;

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
        typeCombo.getItems().addAll(
                AccountFactory.getSupportedTypes());
        typeCombo.getSelectionModel().selectFirst();

        ownershipCombo = new ComboBox<>();
        ownershipCombo.getItems().addAll(
                AccountOwnership.values());
        ownershipCombo.getSelectionModel().selectFirst();

        balanceField = new TextField();

        /*
         * Inherited-account fields
         */

        originalOwnerDobLabel =
                new Label("Original Owner DOB:");

        originalOwnerDobPicker =
                new DatePicker();

        originalOwnerDeathLabel =
                new Label("Original Owner Date of Death:");

        originalOwnerDeathPicker =
                new DatePicker();

        beneficiaryRelationshipLabel =
                new Label("Beneficiary Relationship:");

        beneficiaryRelationshipCombo =
                new ComboBox<>();

        beneficiaryRelationshipCombo
                .getItems()
                .addAll(BeneficiaryRelationship.values());

        beneficiaryRelationshipCombo
                .getSelectionModel()
                .selectFirst();

        /*
         * Populate fields when editing.
         */

        if (account != null) {

            nameField.setText(
                    account.getName());

            typeCombo.setValue(
                    account.getType());

            ownershipCombo.setValue(
                    account.getOwnership());

            balanceField.setText(
                    account.getCurrentBalance()
                            .toPlainString());

            InheritedAccountInformation inheritedInfo =
                    getInheritedInformation(account);

            if (inheritedInfo != null) {

                originalOwnerDobPicker.setValue(
                        inheritedInfo
                                .getOriginalOwnerDateOfBirth());

                originalOwnerDeathPicker.setValue(
                        inheritedInfo
                                .getOriginalOwnerDateOfDeath());

                beneficiaryRelationshipCombo.setValue(
                        inheritedInfo
                                .getBeneficiaryRelationship());
            }
        }

        GridPane grid = new GridPane();

        grid.setPadding(new Insets(15));
        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        grid.add(
                new Label("Name:"),
                0,
                row);

        grid.add(
                nameField,
                1,
                row++);

        grid.add(
                new Label("Type:"),
                0,
                row);

        grid.add(
                typeCombo,
                1,
                row++);

        grid.add(
                new Label("Owner:"),
                0,
                row);

        grid.add(
                ownershipCombo,
                1,
                row++);

        grid.add(
                new Label("Current Balance:"),
                0,
                row);

        grid.add(
                balanceField,
                1,
                row++);

        grid.add(
                originalOwnerDobLabel,
                0,
                row);

        grid.add(
                originalOwnerDobPicker,
                1,
                row++);

        grid.add(
                originalOwnerDeathLabel,
                0,
                row);

        grid.add(
                originalOwnerDeathPicker,
                1,
                row++);

        grid.add(
                beneficiaryRelationshipLabel,
                0,
                row);

        grid.add(
                beneficiaryRelationshipCombo,
                1,
                row);

        /*
         * Show inherited fields only when appropriate.
         */

        typeCombo.valueProperty().addListener(
                (observable, oldValue, newValue) ->
                        updateInheritedFieldsVisibility());

        updateInheritedFieldsVisibility();

        getDialogPane().setContent(grid);

        getDialogPane().getButtonTypes().addAll(
                ButtonType.OK,
                ButtonType.CANCEL);

        setResultConverter(button -> {

            if (button != ButtonType.OK) {
                return null;
            }

            String name =
                    nameField.getText().trim();

            AccountType type =
                    typeCombo.getValue();

            AccountOwnership ownership =
                    ownershipCombo.getValue();

            BigDecimal balance =
                    new BigDecimal(
                            balanceField
                                    .getText()
                                    .trim());

            if (isInheritedType(type)) {

                if (originalOwnerDobPicker.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Original owner date of birth is required.");
                }

                if (originalOwnerDeathPicker.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Original owner date of death is required.");
                }

                if (beneficiaryRelationshipCombo.getValue() == null) {
                    throw new IllegalArgumentException(
                            "Beneficiary relationship is required.");
                }

                InheritedAccountInformation inheritedInfo =
                        new InheritedAccountInformation(
                                originalOwnerDobPicker.getValue(),
                                originalOwnerDeathPicker.getValue(),
                                beneficiaryRelationshipCombo.getValue());

                return AccountFactory.createInherited(
                        type,
                        name,
                        ownership,
                        balance,
                        inheritedInfo);
            }

            return AccountFactory.create(
                    type,
                    name,
                    ownership,
                    balance);
        });
    }

    private boolean isInheritedType(
            AccountType type) {

        return type ==
                AccountType.INHERITED_TRADITIONAL_IRA
                ||
                type ==
                        AccountType.INHERITED_ROTH_IRA;
    }

    private void updateInheritedFieldsVisibility() {

        boolean visible =
                isInheritedType(
                        typeCombo.getValue());

        originalOwnerDobLabel.setVisible(visible);
        originalOwnerDobLabel.setManaged(visible);

        originalOwnerDobPicker.setVisible(visible);
        originalOwnerDobPicker.setManaged(visible);

        originalOwnerDeathLabel.setVisible(visible);
        originalOwnerDeathLabel.setManaged(visible);

        originalOwnerDeathPicker.setVisible(visible);
        originalOwnerDeathPicker.setManaged(visible);

        beneficiaryRelationshipLabel.setVisible(visible);
        beneficiaryRelationshipLabel.setManaged(visible);

        beneficiaryRelationshipCombo.setVisible(visible);
        beneficiaryRelationshipCombo.setManaged(visible);

        Platform.runLater(() -> {

            if (getDialogPane().getScene() != null &&
                    getDialogPane().getScene().getWindow() != null) {

                getDialogPane()
                        .getScene()
                        .getWindow()
                        .sizeToScene();
            }
        });
    }

    private InheritedAccountInformation getInheritedInformation(
            Account account) {

        if (account instanceof InheritedTraditionalIRA inherited) {

            return inherited
                    .getInheritedAccountInformation();
        }

        if (account instanceof InheritedRothIRA inherited) {

            return inherited
                    .getInheritedAccountInformation();
        }

        return null;
    }
}