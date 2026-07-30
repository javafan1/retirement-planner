package com.daviddunn.retirementplanner.testutil;

import com.daviddunn.retirementplanner.domain.tax.FederalTaxCalculation;

import java.math.BigDecimal;

public class FederalTaxCalculationBuilder {

    private BigDecimal adjustedGrossIncome = BigDecimal.ZERO;
    private BigDecimal taxableSocialSecurity = BigDecimal.ZERO;
    private BigDecimal taxableIncome = BigDecimal.ZERO;
    private BigDecimal federalIncomeTax = BigDecimal.ZERO;

    public static FederalTaxCalculationBuilder aFederalTaxCalculation() {
        return new FederalTaxCalculationBuilder();
    }

    private FederalTaxCalculationBuilder() {
    }

    public FederalTaxCalculationBuilder withAdjustedGrossIncome(long amount) {
        adjustedGrossIncome = BigDecimal.valueOf(amount);
        return this;
    }

    public FederalTaxCalculationBuilder withTaxableSocialSecurity(long amount) {
        taxableSocialSecurity = BigDecimal.valueOf(amount);
        return this;
    }

    public FederalTaxCalculationBuilder withTaxableIncome(long amount) {
        taxableIncome = BigDecimal.valueOf(amount);
        return this;
    }

    public FederalTaxCalculationBuilder withFederalIncomeTax(long amount) {
        federalIncomeTax = BigDecimal.valueOf(amount);
        return this;
    }

    public FederalTaxCalculation build() {

        return new FederalTaxCalculation(
                adjustedGrossIncome,
                taxableSocialSecurity,
                taxableIncome,
                federalIncomeTax);
    }
}