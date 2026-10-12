package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.List;

public record MichiganTaxCalculation(

        BigDecimal retirementIncome,

        BigDecimal retirementDeduction,

        BigDecimal taxableIncome,

        BigDecimal incomeTax,
        BigDecimal ordinaryIncome,
        BigDecimal personalExemption,
        BigDecimal eligibleRetirementIncome,
        List<MichiganRetirementEligibility.Decision> eligibilityDecisions,
        List<String> limitations) {

    public MichiganTaxCalculation(BigDecimal retirementIncome, BigDecimal retirementDeduction,
            BigDecimal taxableIncome, BigDecimal incomeTax) {
        this(retirementIncome, retirementDeduction, taxableIncome, incomeTax, retirementIncome,
                BigDecimal.ZERO, retirementDeduction, List.of(), List.of());
    }

    public boolean usedConservativeEligibility() {
        return eligibilityDecisions.stream().anyMatch(decision ->
                decision.qualification() == MichiganRetirementEligibility.Qualification.UNKNOWN);
    }

    public MichiganTaxCalculation {
        Objects.requireNonNull(ordinaryIncome);
        Objects.requireNonNull(personalExemption);
        Objects.requireNonNull(eligibleRetirementIncome);
        eligibilityDecisions = List.copyOf(eligibilityDecisions);
        limitations = List.copyOf(limitations);

        Objects.requireNonNull(
                retirementIncome,
                "Retirement income is required.");

        Objects.requireNonNull(
                retirementDeduction,
                "Retirement deduction is required.");

        Objects.requireNonNull(
                taxableIncome,
                "Taxable income is required.");

        Objects.requireNonNull(
                incomeTax,
                "Income tax is required.");
    }
}
