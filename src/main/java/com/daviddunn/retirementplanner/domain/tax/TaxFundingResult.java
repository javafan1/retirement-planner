package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxFundingResult {

    private final BigDecimal additionalWithdrawal;

    private final FederalTaxCalculation
            federalTaxCalculation;

    private final MichiganTaxCalculation
            michiganTaxCalculation;

    public TaxFundingResult(
            BigDecimal additionalWithdrawal,
            FederalTaxCalculation federalTaxCalculation,
            MichiganTaxCalculation michiganTaxCalculation) {

        this.additionalWithdrawal =
                Objects.requireNonNull(
                        additionalWithdrawal,
                        "Additional withdrawal is required.");

        this.federalTaxCalculation =
                Objects.requireNonNull(
                        federalTaxCalculation,
                        "Federal tax calculation is required.");

        this.michiganTaxCalculation =
                Objects.requireNonNull(
                        michiganTaxCalculation,
                        "Michigan tax calculation is required.");

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

    public MichiganTaxCalculation getMichiganTaxCalculation() {
        return michiganTaxCalculation;
    }

    public BigDecimal getTotalIncomeTax() {

        return federalTaxCalculation
                .getFederalIncomeTax()
                .add(
                        michiganTaxCalculation
                                .incomeTax());
    }
}