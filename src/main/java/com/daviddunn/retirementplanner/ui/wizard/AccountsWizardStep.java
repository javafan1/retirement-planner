package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.financial.Account;
import com.daviddunn.retirementplanner.ui.dialogs.AccountDialog;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;
import javafx.scene.control.Button;

public final class AccountsWizardStep extends WizardListStep<Account> {

    public AccountsWizardStep(NewPlanDraft draft) {
        super(draft, "Accounts are optional. Enter opening balances in dollars as of the plan's projection start. "
                        + "Account names are optional; choose the actual type and owner. A retirement account belongs to one person, not jointly. "
                        + "Account labels do not establish retirement tax eligibility. You can edit details later.",
                "No accounts yet. Adding accounts is optional.");
        table.setId("wizard-accounts");
        column("Name", Account::getName);
        column("Type", account -> account.getType().toString());
        column("Owner", account -> displayOwner(account.getOwnership()));
        column("Opening balance", account -> CurrencyFormatter.format(account.getCurrentBalance()));
        details("wizard-account-details", account -> "Name: " + account.getName() + "\nType: " + account.getType()
                + "\nOwner: " + displayOwner(account.getOwnership()) + "\nOpening balance: $" + account.getCurrentBalance().toPlainString()
                + "\nAs of projection start: " + draft.getPlan().getPlanningAssumptions().getProjectionStartDate()
                + "\nOpening RMD history: " + (account.getOpeningRmdAccountData() == null ? "Not entered; Review shows applicable reminders" : "Recorded"));
        Button add = action("Add Account", "wizard-add-account", () -> edit(null));
        Button edit = action("Edit", "wizard-edit-account", () -> edit(table.getSelectionModel().getSelectedItem()));
        Button remove = action("Remove", "wizard-remove-account", () -> {
            draft.getPlan().getAccountPortfolio().removeAccount(table.getSelectionModel().getSelectedItem());
            refresh();
        });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.setAccessibleText("Edit selected account");
        remove.setAccessibleText("Remove selected account from draft");
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        actions(add, edit, remove);
        refresh();
    }

    @Override
    public StepKey key() { return StepKey.ACCOUNTS; }

    @Override
    public String title() { return "Accounts"; }

    @Override
    public void onEntering() { refresh(); }

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
        var selected = table.getSelectionModel().getSelectedItem();
        table.getItems().setAll(draft.getPlan().getAccountPortfolio().getAccounts());
        if (selected != null && table.getItems().contains(selected)) table.getSelectionModel().select(selected);
    }

    static String displayOwner(com.daviddunn.retirementplanner.domain.model.AccountOwnership owner) {
        return switch (owner) {
            case PRIMARY -> "Primary";
            case SPOUSE -> "Spouse";
            case JOINT -> "Joint";
        };
    }
}
