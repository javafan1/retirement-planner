package com.daviddunn.retirementplanner.domain.model;

import com.daviddunn.retirementplanner.domain.rules.TaxFilingStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class TaxAssumptions {

    private final BigDecimal federalTaxBracketGrowthRate;
    private final BigDecimal standardDeductionGrowthRate;

    private final BigDecimal stateIncomeTaxRate;
    private final BigDecimal localIncomeTaxRate;

    private final TaxFilingStatus filingStatus;

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
                TaxFilingStatus.MARRIED_FILING_JOINTLY);
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
            TaxFilingStatus filingStatus) {

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
                Objects.requireNonNull(
                        filingStatus,
                        "Tax filing status is required.");
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

    public TaxFilingStatus getFilingStatus() {
        return filingStatus;
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
                ", filingStatus=" + filingStatus +
                '}';
    }
}