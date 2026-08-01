package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxCalculator {

    private final FederalTaxableIncomeCalculator
            federalTaxableIncomeCalculator;

    private final FederalIncomeTaxCalculator
            federalIncomeTaxCalculator;

    public TaxCalculator() {

        this.federalTaxableIncomeCalculator =
                new FederalTaxableIncomeCalculator();

        this.federalIncomeTaxCalculator =
                new FederalIncomeTaxCalculator();
    }

    public TaxCalculation calculate(
            BigDecimal adjustedGrossIncome,
            FilingStatus filingStatus,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                adjustedGrossIncome,
                "Adjusted gross income is required.");

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        if (adjustedGrossIncome.signum() < 0) {
            throw new IllegalArgumentException(
                    "Adjusted gross income cannot be negative.");
        }

        BigDecimal taxableIncome =
                federalTaxableIncomeCalculator
                        .calculateTaxableIncome(
                                adjustedGrossIncome,
                                filingStatus,
                                governmentRules);

        BigDecimal federalIncomeTax =
                federalIncomeTaxCalculator
                        .calculateTax(
                                taxableIncome,
                                filingStatus,
                                governmentRules);

        return new TaxCalculation(
                adjustedGrossIncome,
                taxableIncome,
                federalIncomeTax);
    }
}