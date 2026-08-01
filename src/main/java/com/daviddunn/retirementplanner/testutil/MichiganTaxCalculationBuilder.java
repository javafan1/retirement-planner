package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;

import java.math.BigDecimal;

public final class MichiganTaxCalculationBuilder {

    private BigDecimal retirementIncome =
            BigDecimal.ZERO;

    private BigDecimal retirementDeduction =
            BigDecimal.ZERO;

    private BigDecimal taxableIncome =
            BigDecimal.ZERO;

    private BigDecimal incomeTax =
            BigDecimal.ZERO;

    public static MichiganTaxCalculationBuilder
    aMichiganTaxCalculation() {

        return new MichiganTaxCalculationBuilder();
    }

    private MichiganTaxCalculationBuilder() {
    }

    public MichiganTaxCalculationBuilder
    withRetirementIncome(long amount) {

        this.retirementIncome =
                BigDecimal.valueOf(amount);

        return this;
    }

    public MichiganTaxCalculationBuilder
    withRetirementDeduction(long amount) {

        this.retirementDeduction =
                BigDecimal.valueOf(amount);

        return this;
    }

    public MichiganTaxCalculationBuilder
    withTaxableIncome(long amount) {

        this.taxableIncome =
                BigDecimal.valueOf(amount);

        return this;
    }

    public MichiganTaxCalculationBuilder
    withIncomeTax(long amount) {

        this.incomeTax =
                BigDecimal.valueOf(amount);

        return this;
    }

    public MichiganTaxCalculation build() {

        return new MichiganTaxCalculation(
                retirementIncome,
                retirementDeduction,
                taxableIncome,
                incomeTax);
    }
}