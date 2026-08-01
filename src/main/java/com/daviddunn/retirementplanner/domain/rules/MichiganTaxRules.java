package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class MichiganTaxRules {

    private final BigDecimal incomeTaxRate;

    private final BigDecimal retirementDeductionSingle;

    private final BigDecimal retirementDeductionMarried;

    @JsonCreator
    public MichiganTaxRules(

            @JsonProperty("incomeTaxRate")
            BigDecimal incomeTaxRate,

            @JsonProperty("retirementDeductionSingle")
            BigDecimal retirementDeductionSingle,

            @JsonProperty("retirementDeductionMarried")
            BigDecimal retirementDeductionMarried) {

        this.incomeTaxRate =
                Objects.requireNonNull(
                        incomeTaxRate,
                        "Income tax rate is required.");

        this.retirementDeductionSingle =
                Objects.requireNonNull(
                        retirementDeductionSingle,
                        "Single retirement deduction is required.");

        this.retirementDeductionMarried =
                Objects.requireNonNull(
                        retirementDeductionMarried,
                        "Married retirement deduction is required.");
    }

    public BigDecimal getIncomeTaxRate() {
        return incomeTaxRate;
    }

    public BigDecimal getRetirementDeductionSingle() {
        return retirementDeductionSingle;
    }

    public BigDecimal getRetirementDeductionMarried() {
        return retirementDeductionMarried;
    }

    @Override
    public String toString() {

        return "MichiganTaxRules{" +
                "incomeTaxRate=" + incomeTaxRate +
                ", retirementDeductionSingle=" + retirementDeductionSingle +
                ", retirementDeductionMarried=" + retirementDeductionMarried +
                '}';
    }
}