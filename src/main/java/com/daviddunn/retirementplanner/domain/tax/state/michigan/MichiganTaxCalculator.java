package com.daviddunn.retirementplanner.domain.tax.state.michigan;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.MichiganTaxRules;

import java.math.BigDecimal;
import java.util.Objects;

public final class MichiganTaxCalculator {

    private final MichiganRetirementDeductionCalculator
            retirementDeductionCalculator;

    private final MichiganTaxableIncomeCalculator
            taxableIncomeCalculator;

    private final MichiganIncomeTaxCalculator
            incomeTaxCalculator;

    public MichiganTaxCalculator() {

        this.retirementDeductionCalculator =
                new MichiganRetirementDeductionCalculator();

        this.taxableIncomeCalculator =
                new MichiganTaxableIncomeCalculator();

        this.incomeTaxCalculator =
                new MichiganIncomeTaxCalculator();
    }

    public MichiganTaxCalculation calculate(
            BigDecimal retirementIncome,
            FilingStatus filingStatus,
            MichiganTaxRules michiganTaxRules) {

        Objects.requireNonNull(
                retirementIncome,
                "Retirement income is required.");

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                michiganTaxRules,
                "Michigan tax rules are required.");

        BigDecimal retirementDeduction =
                retirementDeductionCalculator.calculate(
                        retirementIncome,
                        filingStatus,
                        michiganTaxRules);

        BigDecimal taxableIncome =
                taxableIncomeCalculator.calculateTaxableIncome(
                        retirementIncome,
                        filingStatus,
                        michiganTaxRules);

        BigDecimal incomeTax =
                incomeTaxCalculator.calculate(
                        taxableIncome,
                        michiganTaxRules);

        return new MichiganTaxCalculation(
                retirementIncome,
                retirementDeduction,
                taxableIncome,
                incomeTax);
    }
}