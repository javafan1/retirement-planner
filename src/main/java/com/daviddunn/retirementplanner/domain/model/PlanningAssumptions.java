package com.daviddunn.retirementplanner.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public final class PlanningAssumptions {

    private final BigDecimal expectedAnnualInvestmentReturn;

    private final BigDecimal expectedAnnualInflationRate;

    private final int projectionLengthYears;

    public PlanningAssumptions(
            BigDecimal expectedAnnualInvestmentReturn,
            BigDecimal expectedAnnualInflationRate,
            int projectionLengthYears) {

        this.expectedAnnualInvestmentReturn =
                Objects.requireNonNull(
                        expectedAnnualInvestmentReturn,
                        "Expected annual investment return is required.");

        this.expectedAnnualInflationRate =
                Objects.requireNonNull(
                        expectedAnnualInflationRate,
                        "Expected annual inflation rate is required.");

        if (projectionLengthYears <= 0) {
            throw new IllegalArgumentException(
                    "Projection length must be greater than zero.");
        }

        this.projectionLengthYears = projectionLengthYears;
    }

    public BigDecimal getExpectedAnnualInvestmentReturn() {
        return expectedAnnualInvestmentReturn;
    }

    public BigDecimal getExpectedAnnualInflationRate() {
        return expectedAnnualInflationRate;
    }

    public int getProjectionLengthYears() {
        return projectionLengthYears;
    }

    @Override
    public String toString() {
        return "PlanningAssumptions{" +
                "expectedAnnualInvestmentReturn=" + expectedAnnualInvestmentReturn +
                ", expectedAnnualInflationRate=" + expectedAnnualInflationRate +
                ", projectionLengthYears=" + projectionLengthYears +
                '}';
    }
}

