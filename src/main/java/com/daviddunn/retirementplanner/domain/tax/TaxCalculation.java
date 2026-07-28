package com.daviddunn.retirementplanner.domain.tax;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxCalculation {

    private final BigDecimal adjustedGrossIncome;
    private final BigDecimal taxableIncome;
    private final BigDecimal federalIncomeTax;

    public TaxCalculation(
            BigDecimal adjustedGrossIncome,
            BigDecimal taxableIncome,
            BigDecimal federalIncomeTax) {

        this.adjustedGrossIncome =
                Objects.requireNonNull(
                        adjustedGrossIncome,
                        "Adjusted gross income is required.");

        this.taxableIncome =
                Objects.requireNonNull(
                        taxableIncome,
                        "Taxable income is required.");

        this.federalIncomeTax =
                Objects.requireNonNull(
                        federalIncomeTax,
                        "Federal income tax is required.");

        if (adjustedGrossIncome.signum() < 0) {
            throw new IllegalArgumentException(
                    "Adjusted gross income cannot be negative.");
        }

        if (taxableIncome.signum() < 0) {
            throw new IllegalArgumentException(
                    "Taxable income cannot be negative.");
        }

        if (federalIncomeTax.signum() < 0) {
            throw new IllegalArgumentException(
                    "Federal income tax cannot be negative.");
        }
    }

    public BigDecimal getAdjustedGrossIncome() {
        return adjustedGrossIncome;
    }

    public BigDecimal getTaxableIncome() {
        return taxableIncome;
    }

    public BigDecimal getFederalIncomeTax() {
        return federalIncomeTax;
    }
}