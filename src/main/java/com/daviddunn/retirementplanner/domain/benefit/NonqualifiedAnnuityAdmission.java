package com.daviddunn.retirementplanner.domain.benefit;

import com.daviddunn.retirementplanner.domain.model.*;
import com.daviddunn.retirementplanner.domain.income.Pension;
import com.daviddunn.retirementplanner.domain.tax.NiitUnsupportedInputException;
import java.util.List;

/** Explicit bounded NIIT/source admission; has no effect on preexisting plans. */
public final class NonqualifiedAnnuityAdmission {
    private NonqualifiedAnnuityAdmission() { }
    public static void requireSupported(RetirementPlan plan) {
        boolean annuities = ConditionalBenefits.hasAnnuity(plan.getConditionalBenefits());
        if (annuities) {
        var opening = plan.getPlanningAssumptions().getProjectionStartDate();
        requireOpening(opening);
        var state = plan.getPlanningAssumptions().getStateTaxAssumptions().jurisdiction();
        requireState(state);
        for (var definition : plan.getConditionalBenefits()) {
            if (definition.annuityTerms() != null) {
                if (plan.getFinancialCalculationMode() == null && definition.annuityTerms().qualification()
                        != NonqualifiedAnnuityTerms.Qualification.DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1)
                    throw new NiitUnsupportedInputException("Select an explicit calculation mode for the new annuity qualification.");
                if (!definition.annuityTerms().basisAsOf().equals(opening))
                    throw new NiitUnsupportedInputException("Verify remaining annuity investment as of the current projection opening.");
            }
        }
        }
        if (!com.daviddunn.retirementplanner.domain.tax.FinancialCalculationPolicy.modelsNiit(plan)) return;
        requireOpening(plan.getPlanningAssumptions().getProjectionStartDate());
        for (var definition : plan.getConditionalBenefits()) {
            if (definition.annuityTerms() != null && definition.annuityTerms().qualification()
                    != NonqualifiedAnnuityTerms.Qualification.DOMESTIC_STANDARD_DEDUCTION_GENERAL_RULE_FIXED_SINGLE_LIFE_V1)
                throw new NiitUnsupportedInputException("Reopen the annuity and verify the bounded domestic NIIT assumptions before using Advanced mode. The saved payment and basis were not changed.");
            if (definition.annuityTerms() == null && !definition.excludesLifePrincipal())
                throw new NiitUnsupportedInputException("Other taxable conditional income has no supported NIIT source qualification.");
        }
        if (plan.getHousehold().members().stream().flatMap(p -> p.getIncomeSources().stream()).anyMatch(Pension.class::isInstance))
            throw new NiitUnsupportedInputException("Generic pension inputs cannot establish federal qualified-plan exclusion from NIIT.");
        if (plan.getAccountPortfolio().getAccounts().stream().anyMatch(a -> a.getType() == AccountType.BROKERAGE && a.getCurrentBalance().signum() > 0))
            throw new NiitUnsupportedInputException("Brokerage assets have no supported realized-gain/basis attribution in this annuity release.");
    }
    public static void requireState(StateTaxJurisdiction state) {
        if (state != StateTaxJurisdiction.IL && !state.usesNoGeneralIndividualIncomeTaxModel())
            throw new NiitUnsupportedInputException("Nonqualified commercial annuity treatment is not implemented for " + state.displayName() + ".");
    }
    public static void requireOpening(java.time.LocalDate opening) {
        if (opening.getYear() < 2026)
            throw new NiitUnsupportedInputException("The initial annuity model uses the validated 2026-and-later planning baseline, not historical tax returns.");
    }
    public static boolean hasOpeningNonqualifiedCash(com.daviddunn.retirementplanner.domain.financial.AccountPortfolio accounts) {
        return accounts.getAccounts().stream().anyMatch(a -> a.getType().getTaxTreatment() == TaxTreatment.CASH && a.getCurrentBalance().signum() > 0);
    }
    public static void requireGrowthSupported(boolean openingCash, java.math.BigDecimal retainedOpening, java.math.BigDecimal annualReturn) {
        if (annualReturn.signum() != 0 && (openingCash || retainedOpening.signum() > 0))
            throw new NiitUnsupportedInputException("Nonqualified cash/retained-asset growth has no recognized-interest or realized-gain split. "
                    + "The guarded NIIT model requires no unclassified nonqualified asset growth. Qualified-account market growth remains supported; inputs were not changed.");
    }
}
