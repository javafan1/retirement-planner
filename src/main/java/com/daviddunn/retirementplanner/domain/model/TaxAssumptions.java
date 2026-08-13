package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.rules.FilingStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxAssumptions {

    private final BigDecimal federalTaxBracketGrowthRate;
    private final BigDecimal standardDeductionGrowthRate;

    private final BigDecimal stateIncomeTaxRate;
    private final BigDecimal localIncomeTaxRate;

    private final FilingStatus filingStatus;

    private final BigDecimal
            estimatedHeirTaxRateOnTaxDeferredAssets;

    public TaxAssumptions(
            BigDecimal federalTaxBracketGrowthRate,
            BigDecimal standardDeductionGrowthRate,
            BigDecimal stateIncomeTaxRate,
            BigDecimal localIncomeTaxRate) {

        this(
                federalTaxBracketGrowthRate,
                standardDeductionGrowthRate,
                stateIncomeTaxRate,
                localIncomeTaxRate,
                FilingStatus.MARRIED_FILING_JOINTLY,
                new BigDecimal("0.25"));
    }

    @JsonCreator
    public TaxAssumptions(

            @JsonProperty("federalTaxBracketGrowthRate")
            BigDecimal federalTaxBracketGrowthRate,

            @JsonProperty("standardDeductionGrowthRate")
            BigDecimal standardDeductionGrowthRate,

            @JsonProperty("stateIncomeTaxRate")
            BigDecimal stateIncomeTaxRate,

            @JsonProperty("localIncomeTaxRate")
            BigDecimal localIncomeTaxRate,

            @JsonProperty("filingStatus")
            FilingStatus filingStatus,

            @JsonProperty("estimatedHeirTaxRateOnTaxDeferredAssets")
            BigDecimal estimatedHeirTaxRateOnTaxDeferredAssets) {

        this.federalTaxBracketGrowthRate =
                Objects.requireNonNull(
                        federalTaxBracketGrowthRate,
                        "Federal tax bracket growth rate is required.");

        this.standardDeductionGrowthRate =
                Objects.requireNonNull(
                        standardDeductionGrowthRate,
                        "Standard deduction growth rate is required.");

        this.stateIncomeTaxRate =
                Objects.requireNonNull(
                        stateIncomeTaxRate,
                        "State income tax rate is required.");

        this.localIncomeTaxRate =
                Objects.requireNonNull(
                        localIncomeTaxRate,
                        "Local income tax rate is required.");
        this.filingStatus =
                filingStatus != null
                        ? filingStatus
                        : FilingStatus.MARRIED_FILING_JOINTLY;

        this.estimatedHeirTaxRateOnTaxDeferredAssets =
                estimatedHeirTaxRateOnTaxDeferredAssets != null
                        ? estimatedHeirTaxRateOnTaxDeferredAssets
                        : new BigDecimal("0.25");

        if (this.estimatedHeirTaxRateOnTaxDeferredAssets.signum() < 0
                || this.estimatedHeirTaxRateOnTaxDeferredAssets.compareTo(BigDecimal.ONE) > 0) {

            throw new IllegalArgumentException(
                    "Estimated heir tax rate must be between 0 and 1.");
        }

    }


    public BigDecimal getFederalTaxBracketGrowthRate() {
        return federalTaxBracketGrowthRate;
    }

    public BigDecimal getStandardDeductionGrowthRate() {
        return standardDeductionGrowthRate;
    }

    public BigDecimal getStateIncomeTaxRate() {
        return stateIncomeTaxRate;
    }

    public BigDecimal getLocalIncomeTaxRate() {
        return localIncomeTaxRate;
    }

    public FilingStatus getFilingStatus() {
        return filingStatus;
    }

    public BigDecimal getEstimatedHeirTaxRateOnTaxDeferredAssets() {
        return estimatedHeirTaxRateOnTaxDeferredAssets;
    }

    @Override
    public String toString() {

        return "TaxAssumptions{" +
                "federalTaxBracketGrowthRate=" +
                federalTaxBracketGrowthRate +
                ", standardDeductionGrowthRate=" +
                standardDeductionGrowthRate +
                ", stateIncomeTaxRate=" +
                stateIncomeTaxRate +
                ", localIncomeTaxRate=" +
                localIncomeTaxRate +
                ", filingStatus=" +
                filingStatus +
                ", estimatedHeirTaxRateOnTaxDeferredAssets=" +
                estimatedHeirTaxRateOnTaxDeferredAssets +
                '}';
    }
}