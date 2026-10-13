package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.tax.state.StateTaxCalculationStatus;

import com.daviddunn.retirementplanner.domain.tax.state.michigan.MichiganTaxCalculation;
import com.daviddunn.retirementplanner.domain.tax.state.ProductionStateTaxResult;

import com.daviddunn.retirementplanner.domain.income.IncomeSourceAttribution;
import java.util.List;
import java.math.BigDecimal;
import java.util.Objects;

public final class TaxFundingResult {

    private final List<IncomeSourceAttribution> incomeSources;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<IncomeSourceAttribution> getIncomeSources() {
        return incomeSources;
    }

    private final BigDecimal additionalWithdrawal;

    private final FederalTaxCalculation
            federalTaxCalculation;

    private final ProductionStateTaxResult stateTaxCalculation;

    public TaxFundingResult(
            BigDecimal additionalWithdrawal,
            FederalTaxCalculation federalTaxCalculation,
            MichiganTaxCalculation michiganTaxCalculation) {
        this(additionalWithdrawal, federalTaxCalculation, michiganTaxCalculation, List.of());
    }

    public TaxFundingResult(
            BigDecimal additionalWithdrawal,
            FederalTaxCalculation federalTaxCalculation,
            MichiganTaxCalculation michiganTaxCalculation,
            List<IncomeSourceAttribution> incomeSources) {
        this(additionalWithdrawal, federalTaxCalculation,
                ProductionStateTaxResult.michigan(michiganTaxCalculation), incomeSources);
    }

    public TaxFundingResult(
            BigDecimal additionalWithdrawal,
            FederalTaxCalculation federalTaxCalculation,
            ProductionStateTaxResult stateTaxCalculation,
            List<IncomeSourceAttribution> incomeSources) {
        this.incomeSources = List.copyOf(Objects.requireNonNull(incomeSources));

        this.additionalWithdrawal =
                Objects.requireNonNull(
                        additionalWithdrawal,
                        "Additional withdrawal is required.");

        this.federalTaxCalculation =
                Objects.requireNonNull(
                        federalTaxCalculation,
                        "Federal tax calculation is required.");

        this.stateTaxCalculation =
                Objects.requireNonNull(
                        stateTaxCalculation,
                        "State tax calculation is required.");

        if (stateTaxCalculation.calculationStatus()
                != StateTaxCalculationStatus.CALCULATED) {
            throw new IllegalArgumentException("A successful funding result requires calculated state tax.");
        }

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
        return stateTaxCalculation.michiganDetails().orElseThrow(() ->
                new IllegalStateException("Michigan details are unavailable for " + stateTaxCalculation.jurisdiction()));
    }

    public ProductionStateTaxResult getStateTaxCalculation() {
        return stateTaxCalculation;
    }

    public BigDecimal getTotalIncomeTax() {

        return federalTaxCalculation
                .getFederalIncomeTax()
                .add(
                        stateTaxCalculation
                                .incomeTax());
    }
}
