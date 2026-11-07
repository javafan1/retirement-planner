package com.daviddunn.retirementplanner.domain.financial;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.EffectiveHouseholdDeathView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Pure annual expense selection; annualized budget over inclusive eligible calendar months. */
public final class ContractualPremiums {
    private ContractualPremiums() { }

    public static boolean configured(RetirementPlan plan) {
        return plan.getHousehold().getExpenses().stream().anyMatch(Expense::isContractualInsurancePremium);
    }

    /** Whether the bounded original-input analyzer capability is required. */
    public static boolean requiresCapability(RetirementPlan plan) {
        return configured(plan) || !plan.getConditionalBenefits().isEmpty();
    }

    public static BigDecimal annual(Expense expense, LocalDate opening, int year, EffectiveHouseholdDeathView deaths) {
        if (!expense.isContractualInsurancePremium()) throw new IllegalArgumentException("A contractual premium is required.");
        if (deaths.state(expense.getContractualPremium().person(), year) == EffectiveHouseholdDeathView.PersonState.ABSENT)
            throw new IllegalArgumentException("The premium insured is absent from the modeled household.");
        if (!deaths.isAlive(expense.getContractualPremium().person(), year)) return BigDecimal.ZERO;
        LocalDate first = LocalDate.of(year, 1, 1);
        LocalDate last = LocalDate.of(year, 12, 31);
        if (opening.isAfter(first)) first = opening;
        if (expense.getStartDate().isAfter(first)) first = expense.getStartDate();
        if (expense.getEndDate() != null && expense.getEndDate().isBefore(last)) last = expense.getEndDate();
        if (last.isBefore(first)) return BigDecimal.ZERO;
        return expense.getAnnualAmount().multiply(BigDecimal.valueOf(last.getMonthValue() - first.getMonthValue() + 1))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }
}
