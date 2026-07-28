package com.daviddunn.retirementplanner.domain.tax;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxFundingResult {

    private final BigDecimal additionalWithdrawal;
    private final FederalTaxCalculation federalTaxCalculation;

    public TaxFundingResult(
            BigDecimal additionalWithdrawal,
            FederalTaxCalculation federalTaxCalculation) {

        this.additionalWithdrawal =
                Objects.requireNonNull(
                        additionalWithdrawal,
                        "Additional withdrawal is required.");

        this.federalTaxCalculation =
                Objects.requireNonNull(
                        federalTaxCalculation,
                        "Federal tax calculation is required.");

        if (additionalWithdrawal.signum() < 0) {
            throw new IllegalArgumentException(
                    "Additional withdrawal cannot be negative.");
        }
    }

    public BigDecimal getAdditionalWithdrawal() {
        return additionalWithdrawal;
    }

    public FederalTaxCalculation getFederalTaxCalculation() {
        return federalTaxCalculation;
    }
}