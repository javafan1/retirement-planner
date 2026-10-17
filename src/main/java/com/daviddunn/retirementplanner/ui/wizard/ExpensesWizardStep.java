package com.daviddunn.retirementplanner.ui.wizard;

import com.daviddunn.retirementplanner.domain.financial.Expense;
import com.daviddunn.retirementplanner.ui.dialogs.ExpenseDialog;
import com.daviddunn.retirementplanner.util.CurrencyFormatter;
import javafx.scene.control.Button;

public final class ExpensesWizardStep extends WizardListStep<Expense> {

    public ExpensesWizardStep(NewPlanDraft draft) {
        super(draft, "Spending funds the retirement cash-flow projection and is optional. Recurring amounts are dollars/year; "
                        + "one-time amounts are a single purchase, not monthly spending. Enter amounts in the projection's opening-year spending base, "
                        + "even for a later start. Future amounts grow from that base using the configured inflation category. "
                        + "The one-time editor uses a purchase date and General inflation. Medicare premiums are modeled separately; review healthcare entries to avoid double counting.",
                "No expenses yet. Review will remind you to check your spending plan.");
        table.setId("wizard-expenses");
        column("Description", Expense::getDescription);
        column("Type", expense -> expense.isOneTimeExpense() ? "One-time" : "Recurring");
        column("Entered dollars", expense -> CurrencyFormatter.format(expense.getAnnualAmount()));
        column("Inflation", expense -> expense.isHealthcareExpense() ? "Healthcare" : "General");
        column("Starts", expense -> expense.getStartDate() == null ? "Unrestricted" : expense.getStartDate().toString());
        details("wizard-spending-details", ExpensesWizardStep::describe);
        Button add = action("Add Expense", "wizard-add-expense", () -> edit(null));
        Button edit = action("Edit", "wizard-edit-expense", () -> edit(table.getSelectionModel().getSelectedItem()));
        Button remove = action("Remove", "wizard-remove-expense", () -> {
            draft.getPlan().getHousehold().removeExpense(table.getSelectionModel().getSelectedItem());
            refresh();
        });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.setAccessibleText("Edit selected spending item");
        remove.setAccessibleText("Remove selected spending item");
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        actions(add, edit, remove);
        refresh();
    }

    @Override
    public StepKey key() { return StepKey.EXPENSES; }

    @Override
    public String title() { return "Spending"; }

    @Override public void onEntering() { refresh(); }

    static String describe(Expense expense) {
        return expense.getDescription() + ": $" + expense.getAnnualAmount().toPlainString()
                + (expense.isOneTimeExpense() ? " one-time purchase" : "/year, recurring")
                + "\nProjection opening-year spending base; future amounts grow from that base."
                + (expense.isOneTimeExpense() ? "\nPurchase date: " + expense.getStartDate()
                : "\nFirst active date: " + (expense.getStartDate() == null ? "Unrestricted" : expense.getStartDate())
                + "; Last active date: " + (expense.getEndDate() == null ? "No scheduled end" : expense.getEndDate())
                + ". Active calendar-year overlap applies; the opening year may be prorated.")
                + "\nInflation category: " + (expense.isHealthcareExpense() ? "Healthcare" : "General");
    }

    private void edit(Expense original) {
        editDialog(new ExpenseDialog(original, true), updated -> {
            if (original == null) draft.getPlan().getHousehold().addExpense(updated);
            else draft.getPlan().getHousehold().replaceExpense(original, updated);
            refresh();
        });
    }

    private void refresh() {
        var selected = table.getSelectionModel().getSelectedItem();
        table.getItems().setAll(draft.getPlan().getHousehold().getExpenses());
        if (selected != null && table.getItems().contains(selected)) table.getSelectionModel().select(selected);
    }
}
