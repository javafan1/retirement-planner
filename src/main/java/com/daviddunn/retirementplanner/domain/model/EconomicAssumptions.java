package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class EconomicAssumptions {

    private final BigDecimal expectedAnnualInvestmentReturn;
    private final BigDecimal expectedAnnualInflationRate;

    @JsonCreator
    public EconomicAssumptions(

            @JsonProperty("expectedAnnualInvestmentReturn")
            BigDecimal expectedAnnualInvestmentReturn,

            @JsonProperty("expectedAnnualInflationRate")
            BigDecimal expectedAnnualInflationRate) {

        this.expectedAnnualInvestmentReturn =
                Objects.requireNonNull(
                        expectedAnnualInvestmentReturn,
                        "Expected annual investment return is required.");

        this.expectedAnnualInflationRate =
                Objects.requireNonNull(
                        expectedAnnualInflationRate,
                        "Expected annual inflation rate is required.");
    }

    public BigDecimal getExpectedAnnualInvestmentReturn() {
        return expectedAnnualInvestmentReturn;
    }

    public BigDecimal getExpectedAnnualInflationRate() {
        return expectedAnnualInflationRate;
    }

    @Override
    public String toString() {

        return "EconomicAssumptions{" +
                "expectedAnnualInvestmentReturn=" +
                expectedAnnualInvestmentReturn +
                ", expectedAnnualInflationRate=" +
                expectedAnnualInflationRate +
                '}';
    }
}