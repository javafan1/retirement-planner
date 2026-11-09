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
            GovernmentRules projectedGovernmentRules) {

        Objects.requireNonNull(
                taxIncome,
                "Tax income is required.");

        Objects.requireNonNull(
                filingStatus,
                "Filing status is required.");

        Objects.requireNonNull(
                projectedGovernmentRules,
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
                                projectedGovernmentRules);

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

        BigDecimal standardDeduction =
                projectedGovernmentRules
                        .getFederalTaxRules(
                                filingStatus)
                        .getStandardDeduction();


        /*
         * Apply the (already projected)
         * standard deduction.
         */
        BigDecimal taxableIncome =
                taxableIncomeCalculator
                        .calculateTaxableIncome(
                                adjustedGrossIncome,
                                filingStatus,
                                projectedGovernmentRules);


        /*
         * Apply the (already projected)
         * progressive tax brackets.
         */
        BigDecimal federalIncomeTax =
                incomeTaxCalculator.calculateTax(
                        taxableIncome,
                        filingStatus,
                        projectedGovernmentRules);

        if (FinancialCalculationPolicy.modelsNiit(taxIncome.getIncomeSources())) {
            if (projectedGovernmentRules.getTaxYear() < NetInvestmentIncomeTaxRules.firstTaxYear())
                throw new NiitUnsupportedInputException("NIIT rules do not apply before their statutory effective tax year.");
            var niit = new NetInvestmentIncomeTaxCalculator().calculate(taxIncome, filingStatus, adjustedGrossIncome);
            return new FederalTaxCalculation(adjustedGrossIncome, taxableSocialSecurity, standardDeduction,
                    taxableIncome, federalIncomeTax.add(niit.tax()), java.util.Optional.of(niit));
        }
        return new FederalTaxCalculation(
                adjustedGrossIncome,
                taxableSocialSecurity,
                standardDeduction,
                taxableIncome,
                federalIncomeTax);
    }
}
