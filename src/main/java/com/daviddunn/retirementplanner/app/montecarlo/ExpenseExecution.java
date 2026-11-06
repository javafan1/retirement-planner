package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExpensePath;
import com.daviddunn.retirementplanner.persistence.AnalysisSnapshotCodec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable enabled capability, bound to the original request; no sampled paths or live plan references. */
public record ExpenseExecution(int version, String model, int dimension, int samplingVersion,
        int correspondenceVersion, String planDigest, List<Binding> bindings) {
    public static final int VERSION = 1;
    public static final String ALGORITHM = MonteCarloExpenseGenerator.MODEL_VERSION + "/dimension-6-v1/correspondence-v1";
    static final String LEVEL_INTERPRETATION = "Expense volatility describes the annual level around its expected escalated amount. "
            + "Actual spending can deviate by more than the configured percentage; the lognormal model has an unbounded positive tail. "
            + "The expected multiplier is one before financial rounding, and annual expense shocks do not compound.";

    public record Binding(int expenseIndex, String correspondenceKey, int duplicateIndex) {
        public Binding {
            if (expenseIndex < 0 || expenseIndex >= ProjectionExpensePath.MAX_OCCURRENCES)
                throw new IllegalArgumentException("Expense binding index exceeds its limit.");
            new ProjectionExpensePath.Occurrence(correspondenceKey, duplicateIndex);
        }
    }

    public ExpenseExecution {
        if (version != VERSION || !MonteCarloExpenseGenerator.MODEL_VERSION.equals(model)
                || dimension != MonteCarloRandomStreams.RECURRING_EXPENSE
                || samplingVersion != MonteCarloRandomStreams.RECURRING_EXPENSE_V1
                || correspondenceVersion != MonteCarloExpenseGenerator.CORRESPONDENCE_VERSION)
            throw new IllegalArgumentException("Unsupported stochastic expense capability.");
        if (planDigest == null || !planDigest.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid expense input binding digest.");
        bindings = List.copyOf(bindings);
        if (bindings.isEmpty() || bindings.size() > ProjectionExpensePath.MAX_OCCURRENCES)
            throw new IllegalArgumentException("Enabled expense bindings are required within the occurrence limit.");
        int previous = -1;
        var occurrences = new java.util.HashSet<ProjectionExpensePath.Occurrence>();
        for (var binding : bindings) {
            if (binding.expenseIndex() <= previous || !occurrences.add(new ProjectionExpensePath.Occurrence(
                    binding.correspondenceKey(), binding.duplicateIndex())))
                throw new IllegalArgumentException("Expense bindings must be ordered and unique.");
            previous = binding.expenseIndex();
        }
    }

    public static boolean configured(RetirementPlan plan) {
        return Objects.requireNonNull(plan).getHousehold().getExpenses().stream()
                .anyMatch(expense -> expense.getEffectiveAnnualExpenseVolatility().signum() > 0);
    }

    static ExpenseExecution capture(RetirementPlan plan) {
        return fromDocument(MonteCarloPlanDocument.capture(plan));
    }

    static ExpenseExecution fromDocument(String document) {
        var plan = MonteCarloPlanDocument.read(document);
        var specifications = plan.getHousehold().getExpenses().stream()
                .map(e -> ExpenseSamplingSpecification.from(e, e.getEffectiveAnnualExpenseVolatility())).toList();
        var generator = new MonteCarloExpenseGenerator(specifications);
        var bindings = new ArrayList<Binding>();
        var occurrences = generator.occurrencesInInputOrder();
        for (int index = 0; index < occurrences.size(); index++) {
            var occurrence = occurrences.get(index);
            if (occurrence.isPresent()) bindings.add(new Binding(index, occurrence.get().correspondenceKey(),
                    occurrence.get().duplicateIndex()));
        }
        return new ExpenseExecution(VERSION, MonteCarloExpenseGenerator.MODEL_VERSION,
                MonteCarloRandomStreams.RECURRING_EXPENSE, MonteCarloRandomStreams.RECURRING_EXPENSE_V1,
                MonteCarloExpenseGenerator.CORRESPONDENCE_VERSION,
                AnalysisSnapshotCodec.sha256(document.getBytes(StandardCharsets.UTF_8)), bindings);
    }

    public void validate(String originalPlanDocument) {
        if (!equals(fromDocument(originalPlanDocument)))
            throw new IllegalArgumentException("Expense capability differs from the original frozen request.");
    }

    /** Original-context disclosure; no generator draws or financial execution. */
    public List<String> disclosure(String originalPlanDocument) {
        validate(originalPlanDocument);
        var expenses = MonteCarloPlanDocument.read(originalPlanDocument).getHousehold().getExpenses();
        var lines = new ArrayList<String>();
        lines.add("Stochastic recurring expenses: independent annual, mean-preserving lognormal expense-level multipliers. "
                + "Volatility is annual relative standard deviation, not a maximum expense increase.");
        lines.add("Expense model: " + model + "; dimension " + dimension + "/" + samplingVersion
                + "; correspondence version " + correspondenceVersion + ".");
        lines.add("Original configured expense volatilities: " + bindings.stream().map(b -> "expense "
                + (b.expenseIndex() + 1) + " = " + percentage(expenses.get(b.expenseIndex()).getEffectiveAnnualExpenseVolatility())
                + "%").collect(java.util.stream.Collectors.joining("; ")) + ".");
        lines.add("Expense shocks are independent across expenses and years and from market, inflation and mortality shocks. "
                + "One-time expenses and ordinary deterministic projections are unaffected.");
        lines.add(LEVEL_INTERPRETATION);
        return List.copyOf(lines);
    }

    public List<String> disclosure(RetirementPlan originalPlan) {
        return disclosure(MonteCarloPlanDocument.capture(originalPlan));
    }

    private static String percentage(java.math.BigDecimal rate) {
        var value = rate.movePointRight(2).stripTrailingZeros();
        // Ordinary percentages remain familiar; extreme small exponents never expand into giant labels.
        return value.scale() <= 32 ? value.toPlainString() : value.toString();
    }
}
