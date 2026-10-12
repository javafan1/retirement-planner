package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;

import java.math.BigDecimal;
import java.util.Objects;

public final class MichiganTaxableIncomeCalculator {

    private final MichiganRetirementDeductionCalculator
            retirementDeductionCalculator;

    public MichiganTaxableIncomeCalculator() {

        retirementDeductionCalculator =
                new MichiganRetirementDeductionCalculator();
    }

    public BigDecimal calculateTaxableIncome(
            BigDecimal ordinaryIncome,
            BigDecimal eligibleRetirementDeduction,
            BigDecimal personalExemption) {
        Objects.requireNonNull(ordinaryIncome);
        Objects.requireNonNull(eligibleRetirementDeduction);
        Objects.requireNonNull(personalExemption);
        if (ordinaryIncome.signum() < 0 || eligibleRetirementDeduction.signum() < 0
                || personalExemption.signum() < 0) {
            throw new IllegalArgumentException("Michigan income and deductions cannot be negative.");
        }
        return ordinaryIncome.subtract(eligibleRetirementDeduction)
                .subtract(personalExemption).max(BigDecimal.ZERO);
    }
    public BigDecimal calculateTaxableIncome(
            BigDecimal ordinaryIncome,
            FilingStatus filingStatus,
            MichiganTaxRules michiganTaxRules) {

        Objects.requireNonNull(
                ordinaryIncome,
                "Ordinary income is required.");

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                michiganTaxRules,
                "Michigan tax rules are required.");

        BigDecimal retirementDeduction =
                retirementDeductionCalculator.calculate(
                        ordinaryIncome,
                        filingStatus,
                        michiganTaxRules);

        return ordinaryIncome
                .subtract(retirementDeduction)
                .max(BigDecimal.ZERO);
    }
}