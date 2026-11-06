package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import java.util.Objects;

/** Separate verified deterministic execution from analyzer capabilities not yet implemented. */
public final class ConditionalBenefitAdmission {
    public static final String MESSAGE = "Conditional benefits are not supported yet by this analysis workflow. Definitions are preserved; use a supported deterministic projection. No simulation was run with benefits ignored.";
    private ConditionalBenefitAdmission() { }
    public static final class UnsupportedExecutionException extends UnsupportedOperationException {
        public UnsupportedExecutionException() { super(MESSAGE); }
        private UnsupportedExecutionException(String message) { super(message); }
    }
    public static void requireSupported(RetirementPlan plan) {
        if (!Objects.requireNonNull(plan).getConditionalBenefits().isEmpty()) throw new UnsupportedExecutionException();
    }

    public static void requireDeterministicSupported(RetirementPlan plan) {
        if (Objects.requireNonNull(plan).getConditionalBenefits().isEmpty()) return;
        plan.validateHouseholdReferences();
        var state = plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction();
        requireDeterministicState(state);
    }

    /** Approved B3 full-plan analyzers share the exact B2 payment/tax subset. */
    public static void requireAnalyzerSupported(RetirementPlan plan) {
        requireDeterministicSupported(plan);
    }

    public static void requireOptimizationSupported(RetirementPlan plan) {
        if (!Objects.requireNonNull(plan).getConditionalBenefits().isEmpty())
            throw new UnsupportedExecutionException("Conditional benefits require independent full scenario evaluation. "
                    + "Prefix and strategy-equivalence reuse have not been verified for these inputs.");
    }

    public static void requireDeterministicState(com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction state) {
        if (state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.MI
                && state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.IL
                && !state.usesNoGeneralIndividualIncomeTaxModel())
            throw new UnsupportedExecutionException("Conditional non-pension income has no validated " + state.displayName()
                    + " tax classification in this release. The plan and historical results are preserved; no incomplete projection was accepted.");
    }
}
