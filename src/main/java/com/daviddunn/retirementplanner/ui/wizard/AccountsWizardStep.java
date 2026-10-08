package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.ui.dialogs.AccountDialog;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;
import javafx.scene.control.Button;

public final class AccountsWizardStep extends WizardListStep<Account> {

    public AccountsWizardStep(NewPlanDraft draft) {
        super(draft, "Add the retirement, investment and cash accounts you want included in this plan. You can add or edit more accounts later.",
                "No accounts yet. Adding accounts is optional.");
        table.setId("wizard-accounts");
        column("Name", Account::getName);
        column("Type", account -> account.getType().toString());
        column("Owner", account -> displayOwner(account.getOwnership()));
        column("Balance", account -> CurrencyFormatter.format(account.getCurrentBalance()));
        Button add = action("Add Account", "wizard-add-account", () -> edit(null));
        Button edit = action("Edit", "wizard-edit-account", () -> edit(table.getSelectionModel().getSelectedItem()));
        Button remove = action("Remove", "wizard-remove-account", () -> {
            draft.getPlan().getAccountPortfolio().removeAccount(table.getSelectionModel().getSelectedItem());
            refresh();
        });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        actions(add, edit, remove);
        refresh();
    }

    @Override
    public String title() { return "Accounts"; }

    private void edit(Account original) {
        editDialog(new AccountDialog(original, draft.getPlan().getHousehold(), true), updated -> {
            if (original != null && original.getType() == updated.getType()
                    && original.getOwnership() == updated.getOwnership()) {
                updated.setOpeningRmdAccountData(original.getOpeningRmdAccountData());
            }
            if (original == null) draft.getPlan().getAccountPortfolio().addAccount(updated);
            else draft.getPlan().getAccountPortfolio().replaceAccount(original, updated);
            refresh();
        });
    }

    private void refresh() {
        table.getItems().setAll(draft.getPlan().getAccountPortfolio().getAccounts());
    }

    static String displayOwner(com.daviddunn.retirementplanner.domain.model.AccountOwnership owner) {
        return switch (owner) {
            case PRIMARY -> "Primary";
            case SPOUSE -> "Spouse";
            case JOINT -> "Joint";
        };
    }
}
