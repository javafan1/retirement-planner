package com.daviddunn.retirementplanner.app.projection;

import com.daviddunn.retirementplanner.domain.model.Household;
import java.util.List;

/** Read-only original input descriptions; never projects or evaluates a premium. */
public final class ContractualPremiumDisclosures {
    public static final String SCOPE = "This premium remains at its modeled contractual amount while the insured is alive and payments are due. "
            + "It stops when the insured dies and is not reduced by household survivor-spending assumptions. "
            + "The annualized rate is prorated over inclusive eligible calendar months, including partial months. "
            + "Fixed nominal amount; no stochastic volatility, asset, tax deduction or automatic death benefit.";
    private ContractualPremiumDisclosures() { }
    public static List<String> lines(Household household, String side) {
        return household.getExpenses().stream().filter(e -> e.isContractualInsurancePremium()).map(e -> side + " — Contractual insurance premium: "
                + e.getDescription() + "; annual amount " + e.getAnnualAmount().toPlainString()
                + "; insured " + e.getContractualPremium().insured() + "; payments " + e.getStartDate()
                + (e.getEndDate() == null ? "; no scheduled end" : " through " + e.getEndDate()) + ". " + SCOPE).toList();
    }
}
