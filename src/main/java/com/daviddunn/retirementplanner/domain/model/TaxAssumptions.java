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

    private final BigDecimal
            futureFederalMarginalRateAdjustment;

    private final Integer
            futureFederalMarginalRateEffectiveYear;

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
                new BigDecimal("0.25"),
                BigDecimal.ZERO,
                null);
    }

    public TaxAssumptions(
            BigDecimal federalTaxBracketGrowthRate,
            BigDecimal standardDeductionGrowthRate,
            BigDecimal stateIncomeTaxRate,
            BigDecimal localIncomeTaxRate,
            FilingStatus filingStatus,
            BigDecimal estimatedHeirTaxRateOnTaxDeferredAssets) {

        this(
                federalTaxBracketGrowthRate,
                standardDeductionGrowthRate,
                stateIncomeTaxRate,
                localIncomeTaxRate,
                filingStatus,
                estimatedHeirTaxRateOnTaxDeferredAssets,
                BigDecimal.ZERO,
                null);
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
            BigDecimal estimatedHeirTaxRateOnTaxDeferredAssets,

            @JsonProperty("futureFederalMarginalRateAdjustment")
            BigDecimal futureFederalMarginalRateAdjustment,

            @JsonProperty("futureFederalMarginalRateEffectiveYear")
            Integer futureFederalMarginalRateEffectiveYear) {

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

        this.futureFederalMarginalRateAdjustment =
                futureFederalMarginalRateAdjustment != null
                        ? futureFederalMarginalRateAdjustment
                        : BigDecimal.ZERO;

        this.futureFederalMarginalRateEffectiveYear =
                futureFederalMarginalRateEffectiveYear;

        if (this.futureFederalMarginalRateAdjustment.signum() != 0
                && this.futureFederalMarginalRateEffectiveYear == null) {

            throw new IllegalArgumentException(
                    "An effective year is required for a future federal marginal rate adjustment.");
        }

        if (this.futureFederalMarginalRateEffectiveYear != null
                && this.futureFederalMarginalRateEffectiveYear <= 0) {

            throw new IllegalArgumentException(
                    "Future federal marginal rate effective year must be positive.");
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

    public BigDecimal getFutureFederalMarginalRateAdjustment() {
        return futureFederalMarginalRateAdjustment;
    }

    public Integer getFutureFederalMarginalRateEffectiveYear() {
        return futureFederalMarginalRateEffectiveYear;
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
                ", futureFederalMarginalRateAdjustment=" +
                futureFederalMarginalRateAdjustment +
                ", futureFederalMarginalRateEffectiveYear=" +
                futureFederalMarginalRateEffectiveYear +
                '}';
    }
}
