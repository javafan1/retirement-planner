package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.ui.dialogs.ExpenseDialog;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;
import javafx.scene.control.Button;

public final class ExpensesWizardStep extends WizardListStep<Expense> {

    public ExpensesWizardStep(NewPlanDraft draft) {
        super(draft, "Set your initial household retirement spending. Add recurring annual expenses or one-time purchases. More detail can be added later in Expenses.",
                "No expenses yet. Review will remind you to check your spending plan.");
        table.setId("wizard-expenses");
        column("Description", Expense::getDescription);
        column("Type", expense -> expense.isOneTimeExpense() ? "One-time" : "Recurring");
        column("Annual / Purchase", expense -> CurrencyFormatter.format(expense.getAnnualAmount()));
        column("Inflation", expense -> expense.isOneTimeExpense() ? "General" :
                expense.isHealthcareExpense() ? "Healthcare" : "General");
        column("Starts", expense -> expense.getStartDate() == null ? "Unrestricted" : expense.getStartDate().toString());
        Button add = action("Add Expense", "wizard-add-expense", () -> edit(null));
        Button edit = action("Edit", "wizard-edit-expense", () -> edit(table.getSelectionModel().getSelectedItem()));
        Button remove = action("Remove", "wizard-remove-expense", () -> {
            draft.getPlan().getHousehold().removeExpense(table.getSelectionModel().getSelectedItem());
            refresh();
        });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        actions(add, edit, remove);
        refresh();
    }

    @Override
    public String title() { return "Expenses"; }

    private void edit(Expense original) {
        editDialog(new ExpenseDialog(original, true), updated -> {
            if (original == null) draft.getPlan().getHousehold().addExpense(updated);
            else draft.getPlan().getHousehold().replaceExpense(original, updated);
            refresh();
        });
    }

    private void refresh() {
        table.getItems().setAll(draft.getPlan().getHousehold().getExpenses());
    }
}
