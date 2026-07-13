package com.daviddunn.retirementplanner.domain.model;

import java.math.BigDecimal;

public class PlanningAssumptions {

    private BigDecimal expectedAnnualInflationRate;

    private BigDecimal expectedAnnualInvestmentReturn;

    public PlanningAssumptions(BigDecimal annualInflationRate,
                               BigDecimal annualInvestmentReturn) {

        this.expectedAnnualInflationRate = annualInflationRate;
        this.expectedAnnualInvestmentReturn = annualInvestmentReturn;
    }

    public BigDecimal getExpectedAnnualInflationRate() {
        return expectedAnnualInflationRate;
    }

    public BigDecimal getExpectedAnnualInvestmentReturn() {
        return expectedAnnualInvestmentReturn;
    }
}