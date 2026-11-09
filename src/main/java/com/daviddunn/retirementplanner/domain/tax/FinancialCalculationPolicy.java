package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.benefit.ConditionalBenefits;
import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import java.util.List;

/** Immutable run policy carried through annual attribution, never global/UI state. */
public final class FinancialCalculationPolicy {
    private FinancialCalculationPolicy() { }

    public static FinancialCalculationMode resolved(RetirementPlan plan) {
        return plan.getFinancialCalculationMode() != null ? plan.getFinancialCalculationMode()
                : ConditionalBenefits.hasAnnuity(plan.getConditionalBenefits())
                    ? FinancialCalculationMode.ADVANCED : FinancialCalculationMode.BASIC;
    }

    public static boolean modelsNiit(RetirementPlan plan) {
        return resolved(plan) == FinancialCalculationMode.ADVANCED;
    }

    public static boolean requiresResultCapability(RetirementPlan plan) {
        return plan.getFinancialCalculationMode() != null
                || com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.requiresCapability(plan);
    }

    /** Null preserves the identical legacy no-annuity/no-NIIT pair contract. */
    public static FinancialCalculationMode comparisonMode(RetirementPlan current, RetirementPlan baseline) {
        return current.getFinancialCalculationMode() != null || baseline.getFinancialCalculationMode() != null
                || ConditionalBenefits.hasAnnuity(current.getConditionalBenefits()) || ConditionalBenefits.hasAnnuity(baseline.getConditionalBenefits())
                ? resolved(current) : null;
    }

    public static boolean modelsNiit(List<IncomeSourceAttribution> sources) {
        var policies = sources.stream().flatMap(s -> s.calculationMode().stream()).toList();
        if (policies.size() > 1) throw new IllegalArgumentException("Duplicate annual calculation policy.");
        return policies.isEmpty() ? sources.stream().anyMatch(s -> s.kind() == IncomeSourceAttribution.Kind.NONQUALIFIED_ANNUITY)
                : policies.getFirst() == FinancialCalculationMode.ADVANCED;
    }

    public static String disclosure(List<IncomeSourceAttribution> sources) {
        var explicit = sources.stream().flatMap(s -> s.calculationMode().stream()).findFirst();
        if (explicit.isPresent()) return "Financial Calculation Mode: " + explicit.get().label() + ". " + explicit.get().disclosure();
        return sources.stream().anyMatch(s -> s.niit().isPresent())
                ? "Legacy Phase 23D guarded NIIT calculation (Advanced-equivalent)."
                : "Legacy financial calculation: NIIT Not Modeled.";
    }

    public static FinancialCalculationMode completedMode(com.daviddunn.retirementplanner.domain.projection.Projection projection) {
        if (projection.isEmpty()) throw new IllegalArgumentException("Completed financial years are required.");
        return completedMode(projection.getYearAt(0).getIncomeSources());
    }

    public static FinancialCalculationMode completedMode(List<IncomeSourceAttribution> sources) {
        return sources.stream().flatMap(s -> s.calculationMode().stream()).findFirst().orElseGet(() ->
                sources.stream().anyMatch(s -> s.niit().isPresent()) ? FinancialCalculationMode.ADVANCED : FinancialCalculationMode.BASIC);
    }

    public static void requireComparable(com.daviddunn.retirementplanner.domain.projection.Projection baseline,
            com.daviddunn.retirementplanner.domain.projection.Projection current) {
        if (completedMode(baseline) != completedMode(current))
            throw new UnsupportedOperationException("These saved results use different financial calculation modes. Run a new comparison using the Current plan's mode; original historical results remain unchanged.");
    }
}
