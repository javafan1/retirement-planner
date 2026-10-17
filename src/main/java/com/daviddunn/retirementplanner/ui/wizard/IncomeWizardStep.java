package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.ui.dialogs.PensionDialog;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;
import javafx.scene.control.Button;

/** Pension-only presentation over the draft's existing person-owned income collections. */
public final class IncomeWizardStep extends WizardListStep<Pension> {

    public IncomeWizardStep(NewPlanDraft draft) {
        super(draft, "Pensions are optional. Enter gross monthly payments. Each pension has its own commencement date, "
                + "not a universal retirement date. Survivor pension payments are different from Social Security survivor benefits.",
                "No pensions entered (optional).");
        table.setId("wizard-pensions");
        column("Name", Pension::getName);
        column("Owner", pension -> AccountsWizardStep.displayOwner(pension.getOwnership()));
        column("Gross $/month", pension -> CurrencyFormatter.format(pension.getMonthlyBenefit()));
        column("Start date", pension -> pension.getStartDate().toString());
        details("wizard-pension-details", pension -> "Name: " + pension.getName()
                + "\nOwner: " + AccountsWizardStep.displayOwner(pension.getOwnership())
                + "\nGross monthly payment: $" + pension.getMonthlyBenefit().toPlainString()
                + "\nStart: " + pension.getStartDate() + "; End: " + (pension.getEndDate() == null ? "Not configured" : pension.getEndDate())
                + "\nAnnual COLA (decimal fraction): " + pension.getAnnualColaRate().toPlainString() + " (0.02 = 2% annually)"
                + "\nSurvivor monthly payment: " + (pension.getSurvivorMonthlyBenefit() == null ? "Not configured"
                : "$" + pension.getSurvivorMonthlyBenefit().toPlainString()));
        Button add = action("Add Pension", "wizard-add-pension", () -> edit(null));
        Button edit = action("Edit Pension", "wizard-edit-income", () -> edit(table.getSelectionModel().getSelectedItem()));
        Button remove = action("Remove Pension", "wizard-remove-income", () -> {
            draft.removeIncomeSource(table.getSelectionModel().getSelectedItem());
            refresh();
        });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        actions(add, edit, remove);
        refresh();
    }

    @Override public StepKey key() { return StepKey.PENSIONS; }
    @Override public String title() { return "Pensions"; }
    @Override public void onEntering() { refresh(); }

    private void edit(Pension original) {
        editDialog(new PensionDialog(original, draft.getPlan().getHousehold(), true), updated -> {
            draft.replaceIncomeSource(original, updated);
            refresh();
        });
    }

    private void refresh() {
        table.getItems().setAll(draft.getPlan().getHousehold().members().stream()
                .flatMap(person -> person.getIncomeSources().stream()).filter(Pension.class::isInstance)
                .map(Pension.class::cast).toList());
    }
}
