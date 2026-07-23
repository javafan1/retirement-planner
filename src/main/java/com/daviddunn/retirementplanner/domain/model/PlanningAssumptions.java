package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public final class PlanningAssumptions {

    private final BigDecimal expectedAnnualInvestmentReturn;
    private final BigDecimal expectedAnnualInflationRate;
    private final int projectionLengthYears;

    private final LocalDate projectionStartDate;

    @JsonCreator
    public PlanningAssumptions(

            @JsonProperty("expectedAnnualInvestmentReturn")
            BigDecimal expectedAnnualInvestmentReturn,

            @JsonProperty("expectedAnnualInflationRate")
            BigDecimal expectedAnnualInflationRate,

            @JsonProperty("projectionLengthYears")
            int projectionLengthYears,

            @JsonProperty("projectionStartDate")
            LocalDate projectionStartDate) {

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

        this.projectionLengthYears =
                projectionLengthYears;

        /*
         * Backward compatibility with plans saved
         * before projectionStartDate was added.
         */
        this.projectionStartDate =
                projectionStartDate != null
                        ? projectionStartDate
                        : LocalDate.now();
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

    public LocalDate getProjectionStartDate() {
        return projectionStartDate;
    }

    @Override
    public String toString() {

        return "PlanningAssumptions{" +
                "expectedAnnualInvestmentReturn=" +
                expectedAnnualInvestmentReturn +
                ", expectedAnnualInflationRate=" +
                expectedAnnualInflationRate +
                ", projectionLengthYears=" +
                projectionLengthYears +
                ", projectionStartDate=" +
                projectionStartDate +
                '}';
    }
}

/*package com.daviddunn.retirementplanner.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class PlanningAssumptions {

    private final BigDecimal expectedAnnualInvestmentReturn;
    private final BigDecimal expectedAnnualInflationRate;
    private final int projectionLengthYears;

@JsonCreator
public PlanningAssumptions(

        @JsonProperty("expectedAnnualInvestmentReturn")
        BigDecimal expectedAnnualInvestmentReturn,

        @JsonProperty("expectedAnnualInflationRate")
        BigDecimal expectedAnnualInflationRate,

        @JsonProperty("projectionLengthYears")
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

*/