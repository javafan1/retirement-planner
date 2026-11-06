package com.daviddunn.retirementplanner.app.montecarlo;

import java.util.ArrayList;
import java.util.List;

/** Ordered immutable execution manifests; null is an explicit disabled side, never inferred sampling. */
public record PairedExpenseExecution(int version, ExpenseExecution current, ExpenseExecution baseline) {
    public static final int VERSION = 1;
    public static final String ALGORITHM = ExpenseExecution.ALGORITHM + "/paired-exact-definition-v1";

    public PairedExpenseExecution {
        if (version != VERSION || current == null && baseline == null)
            throw new IllegalArgumentException("An enabled paired expense capability is required.");
    }

    static PairedExpenseExecution capture(String originalCurrent, String originalBaseline) {
        return new PairedExpenseExecution(VERSION, side(originalCurrent), side(originalBaseline));
    }

    private static ExpenseExecution side(String document) {
        return ExpenseExecution.configured(MonteCarloPlanDocument.read(document))
                ? ExpenseExecution.fromDocument(document) : null;
    }

    public void validate(String originalCurrent, String originalBaseline) {
        if (!equals(capture(originalCurrent, originalBaseline)))
            throw new IllegalArgumentException("Paired expense capabilities differ from their ordered original requests.");
    }

    /** Original input disclosures only; no scenario, random draw or financial calculation is performed. */
    public List<String> disclosure(MonteCarloSnapshot.Request request) {
        validate(request.planA(), request.planB());
        var lines = new ArrayList<String>();
        lines.add("Stochastic recurring expenses: mean-preserving independent lognormal annual expense-level shocks. "
                + "Volatility is annual relative standard deviation, not a maximum variation.");
        lines.add("Expense model: " + MonteCarloExpenseGenerator.MODEL_VERSION
                + "; dimension 6/1; correspondence version 1; paired execution version " + version + ".");
        describe(lines, "Strategy A — " + request.labelA(), request.planA(), current);
        describe(lines, "Strategy B — " + request.labelB(), request.planB(), baseline);
        lines.add("Common expense shocks match only complete normalized type, annual amount, dates, escalation and volatility definitions "
                + "and duplicate ordinals. Different financial definitions are intentionally unmatched; names and list positions do not define correspondence.");
        lines.add("Expense shocks are independent across expenses and years and from market, inflation and mortality shocks; no shock compounds into the next year. "
                + "One-time expenses are unaffected. Zero-volatility sides and ordinary deterministic projections retain their original expense calculation.");
        lines.add(ExpenseExecution.LEVEL_INTERPRETATION);
        return List.copyOf(lines);
    }

    public List<String> disclosure(MonteCarloStrategyComparisonRequest request) {
        return disclosure(MonteCarloFingerprintService.comparison(request).request());
    }

    private static void describe(List<String> lines, String label, String document, ExpenseExecution capability) {
        if (capability == null) {
            lines.add(label + ": recurring-expense volatility disabled (all configured percentages 0%).");
            return;
        }
        lines.add(label + ": recurring-expense volatility active. " + capability.disclosure(document).get(2));
        var expenses = MonteCarloPlanDocument.read(document).getHousehold().getExpenses();
        for (var binding : capability.bindings()) {
            var expense = expenses.get(binding.expenseIndex());
            lines.add(label + " original expense " + (binding.expenseIndex() + 1) + ": expected annual amount "
                    + expense.getAnnualAmount() + "; escalation " + expense.getGrowthCategory()
                    + "; start " + (expense.getStartDate() == null ? "unbounded" : expense.getStartDate())
                    + "; end " + (expense.getEndDate() == null ? "unbounded" : expense.getEndDate()) + ".");
        }
    }
}
