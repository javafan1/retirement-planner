package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class PlanningAssumptions {

    private BigDecimal expectedAnnualInflationRate;

    private BigDecimal expectedAnnualInvestmentReturn;

    @JsonCreator
    public PlanningAssumptions(
            @JsonProperty("expectedAnnualInflationRate")
            BigDecimal expectedAnnualInflationRate,

            @JsonProperty("expectedAnnualInvestmentReturn")
            BigDecimal expectedAnnualInvestmentReturn) {

        this.expectedAnnualInflationRate = expectedAnnualInflationRate;
        this.expectedAnnualInvestmentReturn = expectedAnnualInvestmentReturn;
    }

    public BigDecimal getExpectedAnnualInflationRate() {
        return expectedAnnualInflationRate;
    }

    public BigDecimal getExpectedAnnualInvestmentReturn() {
        return expectedAnnualInvestmentReturn;
    }
}