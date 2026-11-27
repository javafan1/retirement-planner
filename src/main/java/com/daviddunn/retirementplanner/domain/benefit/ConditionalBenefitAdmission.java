package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction;
import com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode;
import com.daviddunn.retirementplanner.domain.model.Household;
import com.daviddunn.retirementplanner.domain.tax.NiitUnsupportedInputException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Separate verified deterministic execution from analyzer capabilities not yet implemented. */
public final class ConditionalBenefitAdmission {
    public static final String MESSAGE = "Conditional benefits are not supported yet by this analysis workflow. Definitions are preserved; use a supported deterministic projection. No simulation was run with benefits ignored.";
    private ConditionalBenefitAdmission() { }
    public static final class UnsupportedExecutionException extends UnsupportedOperationException {
        private final StateTaxJurisdiction unsupportedState;

        public UnsupportedExecutionException() { this(MESSAGE, null); }
        private UnsupportedExecutionException(String message) { this(message, null); }
        private UnsupportedExecutionException(String message, StateTaxJurisdiction unsupportedState) {
            super(message);
            this.unsupportedState = unsupportedState;
        }

        public Optional<StateTaxJurisdiction> unsupportedState() {
            return Optional.ofNullable(unsupportedState);
        }
    }
    public static void requireSupported(RetirementPlan plan) {
        if (com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.requiresCapability(Objects.requireNonNull(plan))) throw new UnsupportedExecutionException();
    }

    public static void requireDeterministicSupported(RetirementPlan plan) {
        Objects.requireNonNull(plan);
        if (usesArkansasRegularTaxOnly(plan) && plan.getFinancialCalculationMode() == FinancialCalculationMode.ADVANCED) {
            throw new NiitUnsupportedInputException("Arkansas Regular Tax Only is available only in Basic mode.");
        }
        boolean arkansas = hasArkansasConditionalBenefits(plan);
        if (arkansas) {
            requireArkansasLifePrincipal(plan.getHousehold(), plan.getConditionalBenefits(),
                    plan.getFinancialCalculationMode());
        }
        NonqualifiedAnnuityAdmission.requireSupported(plan);
        if (plan.getConditionalBenefits().isEmpty() || arkansas) return;
        plan.validateHouseholdReferences();
        var state = plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction();
        requireDeterministicState(state);
    }

    public static boolean hasArkansasConditionalBenefits(RetirementPlan plan) {
        return plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction() == StateTaxJurisdiction.AR
                && !plan.getConditionalBenefits().isEmpty();
    }

    /** The definition constructor enforces the death, lump sum, recipient and personal qualification invariants. */
    public static void requireArkansasLifePrincipal(Household household, List<ConditionalBenefitDefinition> definitions,
            FinancialCalculationMode mode) {
        if (mode == FinancialCalculationMode.ADVANCED) {
            throw new NiitUnsupportedInputException(
                    "Arkansas conditional benefits require Basic mode. Advanced NIIT treatment is unavailable for these inputs.");
        }
        for (var definition : ConditionalBenefits.validatedCopy(definitions, household)) {
            if (!definition.excludesLifePrincipal()
                    || definition.destination() != ConditionalBenefitDefinition.ReceiptDestination.HOUSEHOLD_RETAINED_NON_QUALIFIED_CASH) {
                throw new UnsupportedExecutionException("Arkansas supports only explicitly qualified personal life insurance "
                        + "death principal paid as a lump sum to the other modeled household member.", StateTaxJurisdiction.AR);
            }
        }
    }

    /** Arkansas life principal is approved for deterministic projection only. */
    public static void requireAnalyzerSupported(RetirementPlan plan) {
        com.daviddunn.retirementplanner.domain.roth.RothConversionRequest.requireExecutable(plan.getRothConversionRequest());
        if (usesArkansasRegularTaxOnly(plan)) {
            throw new UnsupportedExecutionException(
                    "Arkansas Regular Tax Only is available only for Basic deterministic projections.", null);
        }
        if (hasArkansasConditionalBenefits(plan)) {
            NonqualifiedAnnuityAdmission.requireSupported(plan);
            throw new UnsupportedExecutionException(
                    "Arkansas conditional benefits are available only in supported deterministic projections.", StateTaxJurisdiction.AR);
        }
        requireDeterministicSupported(plan);
    }

    public static boolean usesArkansasRegularTaxOnly(RetirementPlan plan) {
        return plan.getPlanningAssumptions().getStateTaxAssumptions().usesArkansasRegularTaxOnly();
    }

    public static void requireOptimizationSupported(RetirementPlan plan) {
        if (plan.getFinancialCalculationMode() == com.daviddunn.retirementplanner.domain.model.FinancialCalculationMode.ADVANCED
                || com.daviddunn.retirementplanner.domain.financial.ContractualPremiums.requiresCapability(Objects.requireNonNull(plan)))
            throw new UnsupportedExecutionException("Conditional benefits require independent full scenario evaluation. "
                    + "Prefix and strategy-equivalence reuse have not been verified for these inputs.");
    }

    public static void requireDeterministicState(com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction state) {
        if (state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.MI
                && state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.IL
                && state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.AZ
                && state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.NC
                && state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.KY
                && state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.GA
                && state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.VA
                && state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.OK
                && state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.MS
                && state != com.daviddunn.retirementplanner.domain.model.StateTaxJurisdiction.AL
                && !state.usesNoGeneralIndividualIncomeTaxModel())
            throw new UnsupportedExecutionException("Conditional non-pension income has no validated " + state.displayName()
                    + " tax classification in this release. The plan and historical results are preserved; no incomplete projection was accepted.", state);
    }
}
