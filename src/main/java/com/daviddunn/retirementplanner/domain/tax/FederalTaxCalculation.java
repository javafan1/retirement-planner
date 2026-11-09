package com.daviddunn.retirementplanner.domain.tax;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.util.Objects;

public final class FederalTaxCalculation {
    public enum NiitModelingStatus { NOT_MODELED, CALCULATED }
    public NiitModelingStatus niitModelingStatus() {
        return niit().isPresent() ? NiitModelingStatus.CALCULATED : NiitModelingStatus.NOT_MODELED;
    }

    private final BigDecimal adjustedGrossIncome;
    private final BigDecimal taxableSocialSecurity;
    private final BigDecimal taxableIncome;
    private final BigDecimal federalIncomeTax;
    private final BigDecimal standardDeduction;
    private final java.util.Optional<NetInvestmentIncomeTaxCalculation> niit;

    public FederalTaxCalculation(
            BigDecimal adjustedGrossIncome,
            BigDecimal taxableSocialSecurity,
            BigDecimal standardDeduction,
            BigDecimal taxableIncome,
            BigDecimal federalIncomeTax) {
        this(adjustedGrossIncome, taxableSocialSecurity, standardDeduction, taxableIncome, federalIncomeTax, java.util.Optional.empty());
    }

    public FederalTaxCalculation(BigDecimal adjustedGrossIncome, BigDecimal taxableSocialSecurity,
            BigDecimal standardDeduction, BigDecimal taxableIncome, BigDecimal federalIncomeTax,
            java.util.Optional<NetInvestmentIncomeTaxCalculation> niit) {
        this.niit = Objects.requireNonNull(niit);
        niit.ifPresent(value -> {
            if (value.magi().compareTo(adjustedGrossIncome) != 0 || value.tax().compareTo(federalIncomeTax) > 0)
                throw new IllegalArgumentException("NIIT evidence does not match the federal total.");
        });

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

        this.standardDeduction =
                requireNonNegative(
                        standardDeduction,
                        "Standard Deduction");

        this.federalIncomeTax =
                requireNonNegative(
                        federalIncomeTax,
                        "Federal income tax");
    }

    public BigDecimal getAdjustedGrossIncome() {
        return adjustedGrossIncome;
    }

    @JsonIgnore
    public java.util.Optional<NetInvestmentIncomeTaxCalculation> niit() { return niit; }

    public BigDecimal getTaxableSocialSecurity() {
        return taxableSocialSecurity;
    }

    public BigDecimal getStandardDeduction() {
        return standardDeduction;
    }

    public BigDecimal getTaxableIncome() {
        return taxableIncome;
    }

    public BigDecimal getFederalIncomeTax() {
        return federalIncomeTax;
    }

    @JsonIgnore
    public FederalTaxBaseBreakdown getModeledBaseBreakdown() {
        return FederalTaxBaseBreakdown.fromModeledResult(this);
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
