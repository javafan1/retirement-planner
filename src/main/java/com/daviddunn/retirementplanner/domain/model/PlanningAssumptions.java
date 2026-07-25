package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PlanningAssumptions {

    private final EconomicAssumptions economicAssumptions;
    private final TaxAssumptions taxAssumptions;

    private final int projectionLengthYears;
    private final LocalDate projectionStartDate;

    /*
     * Jackson constructor.
     *
     * Supports:
     *
     * 1. Current JSON format using EconomicAssumptions
     *    and TaxAssumptions.
     *
     * 2. Legacy JSON format using the original flat
     *    investment-return and inflation properties.
     *
     * 3. Intermediate files created during the
     *    EconomicAssumptions migration.
     */
    @JsonCreator
    public PlanningAssumptions(

            @JsonProperty("economicAssumptions")
            EconomicAssumptions economicAssumptions,

            @JsonProperty("taxAssumptions")
            TaxAssumptions taxAssumptions,

            @JsonProperty("expectedAnnualInvestmentReturn")
            BigDecimal legacyInvestmentReturn,

            @JsonProperty("expectedAnnualInflationRate")
            BigDecimal legacyInflationRate,

            @JsonProperty("projectionLengthYears")
            int projectionLengthYears,

            @JsonProperty("projectionStartDate")
            LocalDate projectionStartDate) {

        /*
         * Economic assumptions.
         */
        if (economicAssumptions != null) {

            // Current JSON format.
            this.economicAssumptions =
                    economicAssumptions;

        } else if (legacyInvestmentReturn != null &&
                legacyInflationRate != null) {

            // Original JSON format.
            this.economicAssumptions =
                    new EconomicAssumptions(
                            legacyInvestmentReturn,
                            legacyInflationRate);

        } else {

            /*
             * Compatibility with files created during
             * the EconomicAssumptions migration.
             */
            this.economicAssumptions =
                    createDefaultEconomicAssumptions();
        }

        /*
         * Tax assumptions.
         *
         * Older plans do not contain TaxAssumptions,
         * so use defaults when they are absent.
         */
        this.taxAssumptions =
                taxAssumptions != null
                        ? taxAssumptions
                        : createDefaultTaxAssumptions();

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

    /*
     * Primary constructor for current application code.
     *
     * UI and domain code should normally use this
     * constructor rather than the Jackson constructor.
     */
    public PlanningAssumptions(
            EconomicAssumptions economicAssumptions,
            TaxAssumptions taxAssumptions,
            int projectionLengthYears,
            LocalDate projectionStartDate) {

        this(
                economicAssumptions,
                taxAssumptions,
                null,
                null,
                projectionLengthYears,
                projectionStartDate);
    }

    /*
     * Legacy compatibility constructor.
     *
     * Existing code and tests that still construct
     * PlanningAssumptions using the original economic
     * parameters can continue to work.
     */
    public PlanningAssumptions(
            BigDecimal expectedAnnualInvestmentReturn,
            BigDecimal expectedAnnualInflationRate,
            int projectionLengthYears,
            LocalDate projectionStartDate) {

        this(
                new EconomicAssumptions(
                        expectedAnnualInvestmentReturn,
                        expectedAnnualInflationRate),
                null,
                null,
                null,
                projectionLengthYears,
                projectionStartDate);
    }

    @JsonProperty("economicAssumptions")
    public EconomicAssumptions getEconomicAssumptions() {
        return economicAssumptions;
    }

    @JsonProperty("taxAssumptions")
    public TaxAssumptions getTaxAssumptions() {
        return taxAssumptions;
    }

    /*
     * Compatibility getters.
     *
     * Existing ProjectionEngine and other application
     * code can continue using these methods.
     *
     * JsonIgnore prevents Jackson from writing the
     * old flat properties into new JSON files.
     */
    @JsonIgnore
    public BigDecimal getExpectedAnnualInvestmentReturn() {

        return economicAssumptions
                .getExpectedAnnualInvestmentReturn();
    }

    @JsonIgnore
    public BigDecimal getExpectedAnnualInflationRate() {

        return economicAssumptions
                .getExpectedAnnualInflationRate();
    }

    public int getProjectionLengthYears() {
        return projectionLengthYears;
    }

    public LocalDate getProjectionStartDate() {
        return projectionStartDate;
    }

    private static EconomicAssumptions
    createDefaultEconomicAssumptions() {

        return new EconomicAssumptions(
                new BigDecimal("0.070"),
                new BigDecimal("0.025"));
    }

    private static TaxAssumptions
    createDefaultTaxAssumptions() {

        return new TaxAssumptions(
                new BigDecimal("0.025"),
                new BigDecimal("0.025"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    @Override
    public String toString() {

        return "PlanningAssumptions{" +
                "economicAssumptions=" +
                economicAssumptions +
                ", taxAssumptions=" +
                taxAssumptions +
                ", projectionLengthYears=" +
                projectionLengthYears +
                ", projectionStartDate=" +
                projectionStartDate +
                '}';
    }
}