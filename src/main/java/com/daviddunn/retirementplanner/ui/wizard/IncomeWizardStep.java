package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.income.IncomeSource;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.income.SocialSecurityIncome;
import com.daviddunn.retirementplanner.domain.model.AccountOwnership;
import com.daviddunn.retirementplanner.ui.dialogs.PensionDialog;
import com.daviddunn.retirementplanner.ui.dialogs.SocialSecurityDialog;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;
import javafx.scene.control.Button;

import java.util.List;

public final class IncomeWizardStep extends WizardListStep<IncomeSource> {

    private final Button addSocialSecurity;

    public IncomeWizardStep(NewPlanDraft draft) {
        super(draft, "Add Social Security for each person who expects it, and any pensions. Benefits are optional; you can edit details later in Income. Social Security uses an FRA monthly benefit and claiming age, not an independently entered start date.",
                "No income entered. You can plan to fund spending from your accounts.");
        table.setId("wizard-income");
        column("Source", income -> income instanceof Pension ? "Pension" : "Social Security");
        column("Name", IncomeSource::getName);
        column("Owner", income -> AccountsWizardStep.displayOwner(income.getOwnership()));
        column("Monthly / FRA", income -> CurrencyFormatter.format(income instanceof Pension pension
                ? pension.getMonthlyBenefit() : ((SocialSecurityIncome) income).getFullRetirementMonthlyBenefit()));
        column("Starts", income -> income.getStartDate().toString());
        addSocialSecurity = action("Add Social Security", "wizard-add-social-security", () -> editSocialSecurity(null));
        Button addPension = action("Add Pension", "wizard-add-pension", () -> editPension(null));
        Button edit = action("Edit", "wizard-edit-income", () -> {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected instanceof Pension pension) editPension(pension);
            else if (selected instanceof SocialSecurityIncome ss) editSocialSecurity(ss);
        });
        Button remove = action("Remove", "wizard-remove-income", () -> {
            var selected = table.getSelectionModel().getSelectedItem();
            owner(selected.getOwnership()).removeIncomeSource(selected);
            refresh();
        });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        actions(addSocialSecurity, addPension, edit, remove);
        refresh();
    }

    @Override
    public String title() { return "Income"; }

    @Override
    public void onEntering() { refresh(); }

    private List<AccountOwnership> availableSocialSecurityOwners(SocialSecurityIncome original) {
        return draft.getPlan().getHousehold().peopleByOwner().entrySet().stream()
                .filter(entry -> entry.getValue().getIncomeSources().stream()
                        .noneMatch(income -> income instanceof SocialSecurityIncome && income != original))
                .map(java.util.Map.Entry::getKey).toList();
    }

    private void editSocialSecurity(SocialSecurityIncome original) {
        var owners = availableSocialSecurityOwners(original);
        if (owners.isEmpty()) return;
        editDialog(new SocialSecurityDialog(original,
                draft.getPlan().getPlanningAssumptions().getProjectionStartDate().getYear(),
                draft.getPlan().getHousehold(), owners), updated -> replace(original, updated));
    }

    private void editPension(Pension original) {
        editDialog(new PensionDialog(original, draft.getPlan().getHousehold(), true), updated -> replace(original, updated));
    }

    private void replace(IncomeSource original, IncomeSource updated) {
        if (original == null) owner(updated.getOwnership()).addIncomeSource(updated);
        else if (original.getOwnership() == updated.getOwnership()) {
            owner(original.getOwnership()).replaceIncomeSource(original, updated);
        }
        else {
            owner(original.getOwnership()).removeIncomeSource(original);
            owner(updated.getOwnership()).addIncomeSource(updated);
        }
        refresh();
    }

    private com.daviddunn.retirementplanner.domain.model.Person owner(AccountOwnership owner) {
        return java.util.Objects.requireNonNull(draft.getPlan().getHousehold().peopleByOwner().get(owner));
    }

    private void refresh() {
        table.getItems().setAll(draft.getPlan().getHousehold().members().stream()
                .flatMap(person -> person.getIncomeSources().stream()).toList());
        addSocialSecurity.setDisable(availableSocialSecurityOwners(null).isEmpty());
    }
}
