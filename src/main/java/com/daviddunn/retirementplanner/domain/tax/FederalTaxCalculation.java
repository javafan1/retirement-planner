package com.daviddunn.retirementplanner.domain.tax;

import java.math.BigDecimal;
import java.util.Objects;

public final class FederalTaxCalculation {

    private final BigDecimal adjustedGrossIncome;
    private final BigDecimal taxableSocialSecurity;
    private final BigDecimal taxableIncome;
    private final BigDecimal federalIncomeTax;

    public FederalTaxCalculation(
            BigDecimal adjustedGrossIncome,
            BigDecimal taxableSocialSecurity,
            BigDecimal taxableIncome,
            BigDecimal federalIncomeTax) {

        this.adjustedGrossIncome =
                requireNonNegative(
                        adjustedGrossIncome,
                        "Adjusted gross income");

        this.taxableSocialSecurity =
                requireNonNegative(
                        taxableSocialSecurity,
                        "Taxable Social Security");

        this.taxableIncome =
                requireNonNegative(
                        taxableIncome,
                        "Taxable income");

        this.federalIncomeTax =
                requireNonNegative(
                        federalIncomeTax,
                        "Federal income tax");
    }

    public BigDecimal getAdjustedGrossIncome() {
        return adjustedGrossIncome;
    }

    public BigDecimal getTaxableSocialSecurity() {
        return taxableSocialSecurity;
    }

    public BigDecimal getTaxableIncome() {
        return taxableIncome;
    }

    public BigDecimal getFederalIncomeTax() {
        return federalIncomeTax;
    }

    private static BigDecimal requireNonNegative(
            BigDecimal amount,
            String description) {

        Objects.requireNonNull(
                amount,
                description + " is required.");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    description + " cannot be negative.");
        }

        return amount;
    }
}