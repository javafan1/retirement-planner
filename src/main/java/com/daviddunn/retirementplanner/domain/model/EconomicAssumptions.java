package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class EconomicAssumptions {

    private final BigDecimal expectedAnnualInvestmentReturn;

    /*
     * General inflation used for most expenses and
     * government parameter projections.
     */
    private final BigDecimal generalInflationRate;

    /*
     * Healthcare inflation used for healthcare
     * expenses and eventually Medicare premiums.
     */
    private final BigDecimal healthcareInflationRate;

    /*
     * Annual Social Security cost-of-living
     * adjustment assumption.
     */
    private final BigDecimal socialSecurityColaRate;

    @JsonCreator
    public EconomicAssumptions(

            @JsonProperty("expectedAnnualInvestmentReturn")
            BigDecimal expectedAnnualInvestmentReturn,

            /*
             * Legacy property name retained for
             * backward compatibility.
             */
            @JsonProperty("expectedAnnualInflationRate")
            BigDecimal legacyInflationRate,

            @JsonProperty("generalInflationRate")
            BigDecimal generalInflationRate,

            @JsonProperty("healthcareInflationRate")
            BigDecimal healthcareInflationRate,

            @JsonProperty("socialSecurityColaRate")
            BigDecimal socialSecurityColaRate) {

        this.expectedAnnualInvestmentReturn =
                Objects.requireNonNull(
                        expectedAnnualInvestmentReturn,
                        "Expected annual investment return is required.");

        /*
         * Support older JSON files that only contain
         * expectedAnnualInflationRate.
         */
        BigDecimal inflation =
                generalInflationRate != null
                        ? generalInflationRate
                        : legacyInflationRate;

        this.generalInflationRate =
                Objects.requireNonNull(
                        inflation,
                        "General inflation rate is required.");

        /*
         * Older plans will simply use the general
         * inflation rate until the user changes it.
         */
        this.healthcareInflationRate =
                healthcareInflationRate != null
                        ? healthcareInflationRate
                        : this.generalInflationRate;

        /*
         * Likewise, default Social Security COLA to
         * the general inflation assumption.
         */
        this.socialSecurityColaRate =
                socialSecurityColaRate != null
                        ? socialSecurityColaRate
                        : this.generalInflationRate;
    }

    /*
     * Convenience constructor for current code.
     */
    public EconomicAssumptions(
            BigDecimal expectedAnnualInvestmentReturn,
            BigDecimal generalInflationRate,
            BigDecimal healthcareInflationRate,
            BigDecimal socialSecurityColaRate) {

        this(
                expectedAnnualInvestmentReturn,
                null,
                generalInflationRate,
                healthcareInflationRate,
                socialSecurityColaRate);
    }

    /*
     * Compatibility constructor used throughout the
     * current application.
     */
    public EconomicAssumptions(
            BigDecimal expectedAnnualInvestmentReturn,
            BigDecimal generalInflationRate) {

        this(
                expectedAnnualInvestmentReturn,
                null,
                generalInflationRate,
                generalInflationRate,
                generalInflationRate);
    }

    public BigDecimal getExpectedAnnualInvestmentReturn() {
        return expectedAnnualInvestmentReturn;
    }

    /*
     * Legacy getter retained temporarily while the
     * application is migrated.
     */
    public BigDecimal getExpectedAnnualInflationRate() {
        return generalInflationRate;
    }

    public BigDecimal getGeneralInflationRate() {
        return generalInflationRate;
    }

    public BigDecimal getHealthcareInflationRate() {
        return healthcareInflationRate;
    }

    public BigDecimal getSocialSecurityColaRate() {
        return socialSecurityColaRate;
    }

    @Override
    public String toString() {

        return "EconomicAssumptions{" +
                "expectedAnnualInvestmentReturn=" +
                expectedAnnualInvestmentReturn +
                ", generalInflationRate=" +
                generalInflationRate +
                ", healthcareInflationRate=" +
                healthcareInflationRate +
                ", socialSecurityColaRate=" +
                socialSecurityColaRate +
                '}';
    }
}