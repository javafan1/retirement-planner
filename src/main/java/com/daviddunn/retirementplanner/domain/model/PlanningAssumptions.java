package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PlanningAssumptions {

    private final EconomicAssumptions economicAssumptions;
    private final TaxAssumptions taxAssumptions;
    private final WithdrawalAssumptions withdrawalAssumptions;

    private final int projectionLengthYears;
    private final LocalDate projectionStartDate;

    /*
     * Jackson constructor.
     *
     * Supports:
     *
     * 1. Current JSON format using EconomicAssumptions,
     *    TaxAssumptions, and WithdrawalAssumptions.
     *
     * 2. Legacy JSON format using the original flat
     *    investment-return and inflation properties.
     *
     * 3. Intermediate files created during the
     *    EconomicAssumptions migration.
     *
     * 4. Older files that do not contain
     *    WithdrawalAssumptions.
     */
    @JsonCreator
    public PlanningAssumptions(

            @JsonProperty("economicAssumptions")
            EconomicAssumptions economicAssumptions,

            @JsonProperty("taxAssumptions")
            TaxAssumptions taxAssumptions,

            @JsonProperty("withdrawalAssumptions")
            WithdrawalAssumptions withdrawalAssumptions,

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

        /*
         * Withdrawal assumptions.
         *
         * Older plans do not contain
         * WithdrawalAssumptions, so use defaults
         * when they are absent.
         */
        this.withdrawalAssumptions =
                withdrawalAssumptions != null
                        ? withdrawalAssumptions
                        : createDefaultWithdrawalAssumptions();

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
     * Allows all current assumption groups to be
     * supplied explicitly.
     */
    public PlanningAssumptions(
            EconomicAssumptions economicAssumptions,
            TaxAssumptions taxAssumptions,
            WithdrawalAssumptions withdrawalAssumptions,
            int projectionLengthYears,
            LocalDate projectionStartDate) {

        this(
                economicAssumptions,
                taxAssumptions,
                withdrawalAssumptions,
                null,
                null,
                projectionLengthYears,
                projectionStartDate);
    }

    /*
     * Compatibility constructor.
     *
     * Existing application code that supplies
     * EconomicAssumptions and TaxAssumptions but
     * does not yet supply WithdrawalAssumptions
     * can continue to work.
     *
     * WithdrawalAssumptions will use the default.
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

    @JsonProperty("withdrawalAssumptions")
    public WithdrawalAssumptions getWithdrawalAssumptions() {
        return withdrawalAssumptions;
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

    private static WithdrawalAssumptions
    createDefaultWithdrawalAssumptions() {

        return new WithdrawalAssumptions(
                WithdrawalStrategyType.TAXABLE_FIRST);
    }

    @JsonIgnore
    public BigDecimal getInflationRate() {

        return getExpectedAnnualInflationRate();
    }

    @JsonIgnore
    public BigDecimal getInvestmentReturnRate() {

        return getExpectedAnnualInvestmentReturn();
    }

    @Override
    public String toString() {

        return "PlanningAssumptions{" +
                "economicAssumptions=" +
                economicAssumptions +
                ", taxAssumptions=" +
                taxAssumptions +
                ", withdrawalAssumptions=" +
                withdrawalAssumptions +
                ", projectionLengthYears=" +
                projectionLengthYears +
                ", projectionStartDate=" +
                projectionStartDate +
                '}';
    }
}