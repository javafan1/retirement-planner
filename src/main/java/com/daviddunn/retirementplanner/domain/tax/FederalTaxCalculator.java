package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;

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
            FilingStatus filingStatus,
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
         * Apply the (already projected)
         * standard deduction.
         */
        BigDecimal taxableIncome =
                taxableIncomeCalculator
                        .calculateTaxableIncome(
                                adjustedGrossIncome,
                                filingStatus,
                                governmentRules);

        /*
         * Apply the (already projected)
         * progressive tax brackets.
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