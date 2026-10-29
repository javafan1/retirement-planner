package com.daviddunn.retirementplanner.app.montecarlo;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.ProjectionEvaluationContext;
import com.daviddunn.retirementplanner.domain.projection.ProjectionExpensePath.FinancialDefinition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Phase 14B2 internal execution inputs only. No public/menu, snapshot, PDF or supplied-world API.
 * Frozen exact expense definitions authorize bindings; B1 canonical keys only pair random draws.
 */
final class InternalExpenseRun {
    private final List<FinancialDefinition> definitions;
    private final MonteCarloExpenseGenerator generator;
    private final boolean enabled;

    private InternalExpenseRun(RetirementPlan plan, List<BigDecimal> volatilities) {
        var expenses = Objects.requireNonNull(plan).getHousehold().getExpenses();
        if (Objects.requireNonNull(volatilities).size() != expenses.size()) {
            throw new IllegalArgumentException("Volatility configuration must cover the frozen expense list.");
        }
        definitions = expenses.stream().map(FinancialDefinition::capture).toList();
        var specifications = new ArrayList<ExpenseSamplingSpecification>();
        for (int index = 0; index < expenses.size(); index++) {
            specifications.add(ExpenseSamplingSpecification.from(expenses.get(index), volatilities.get(index)));
        }
        enabled = specifications.stream().anyMatch(ExpenseSamplingSpecification::enabled);
        generator = enabled ? new MonteCarloExpenseGenerator(specifications) : null;
    }

    static InternalExpenseRun capture(RetirementPlan plan, List<BigDecimal> volatilities) {
        return new InternalExpenseRun(plan, volatilities);
    }

    void validate(RetirementPlan plan) {
        if (!definitions.equals(plan.getHousehold().getExpenses().stream().map(FinancialDefinition::capture).toList())) {
            throw new IllegalArgumentException("Run-only volatility inputs differ from the frozen financial expense list.");
        }
    }

    boolean enabled() { return enabled; }

    ProjectionEvaluationContext apply(RetirementPlan plan, ProjectionEvaluationContext context,
            long seed, int scenario, int first, int last) {
        validate(plan);
        if (!enabled) return context;
        var path = generator.generate(seed, scenario, first, last).orElseThrow()
                .bind(plan.getHousehold().getExpenses(), generator.occurrencesInInputOrder());
        return context.withExpensePath(path);
    }

    static void requireGeneratedWorlds(Object supplied) {
        if (supplied != null) {
            throw new UnsupportedOperationException("Enabled expense supplied-world execution is unavailable until Phase 14B3 provenance support.");
        }
    }
}
