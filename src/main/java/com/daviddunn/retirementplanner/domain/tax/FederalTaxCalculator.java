package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;
import com.daviddunn.retirementplanner.domain.rules.TaxFilingStatus;

import java.math.BigDecimal;
import java.util.Objects;

public final class FederalTaxCalculator {

    private final SocialSecurityTaxCalculator
            socialSecurityTaxCalculator;

    private final FederalTaxableIncomeCalculator
            taxableIncomeCalculator;

    private final FederalIncomeTaxCalculator
            incomeTaxCalculator;

    public FederalTaxCalculator() {

        this.socialSecurityTaxCalculator =
                new SocialSecurityTaxCalculator();

        this.taxableIncomeCalculator =
                new FederalTaxableIncomeCalculator();

        this.incomeTaxCalculator =
                new FederalIncomeTaxCalculator();
    }

    public FederalTaxCalculation calculate(
            TaxIncome taxIncome,
            TaxFilingStatus filingStatus,
            GovernmentRules governmentRules) {

        Objects.requireNonNull(
                taxIncome,
                "Tax income is required.");

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                governmentRules,
                "Government rules are required.");

        /*
         * Determine how much of Social Security
         * becomes federally taxable.
         */
        BigDecimal taxableSocialSecurity =
                socialSecurityTaxCalculator
                        .calculateTaxableBenefits(
                                taxIncome,
                                filingStatus,
                                governmentRules);

        /*
         * Current MVP AGI:
         *
         * Pension income
         * + tax-deferred withdrawals
         * + taxable Social Security.
         */
        BigDecimal adjustedGrossIncome =
                taxIncome
                        .getOrdinaryIncomeBeforeSocialSecurity()
                        .add(
                                taxableSocialSecurity);

        /*
         * Apply the standard deduction.
         */
        BigDecimal taxableIncome =
                taxableIncomeCalculator
                        .calculateTaxableIncome(
                                adjustedGrossIncome,
                                filingStatus,
                                governmentRules);

        /*
         * Apply the progressive federal
         * income-tax brackets.
         */
        BigDecimal federalIncomeTax =
                incomeTaxCalculator.calculateTax(
                        taxableIncome,
                        filingStatus,
                        governmentRules);

        return new FederalTaxCalculation(
                adjustedGrossIncome,
                taxableSocialSecurity,
                taxableIncome,
                federalIncomeTax);
    }
}