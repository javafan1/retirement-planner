package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
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

    private final BigDecimal socialSecurityBenefitReductionPercent;

    private final int socialSecurityBenefitReductionEffectiveYear;

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
            BigDecimal socialSecurityColaRate,

            @JsonProperty("socialSecurityBenefitReductionPercent")
            BigDecimal socialSecurityBenefitReductionPercent,

            @JsonProperty("socialSecurityBenefitReductionEffectiveYear")
            Integer socialSecurityBenefitReductionEffectiveYear) {

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

        this.socialSecurityBenefitReductionPercent =
                socialSecurityBenefitReductionPercent != null
                        ? socialSecurityBenefitReductionPercent
                        : BigDecimal.ZERO;

        if (this.socialSecurityBenefitReductionPercent.compareTo(BigDecimal.ZERO) < 0
                || this.socialSecurityBenefitReductionPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                    "Social Security benefit reduction must be between 0 and 100 percent.");
        }

        this.socialSecurityBenefitReductionEffectiveYear =
                socialSecurityBenefitReductionEffectiveYear != null
                        ? socialSecurityBenefitReductionEffectiveYear
                        : 2032;

        if (this.socialSecurityBenefitReductionEffectiveYear < 1000
                || this.socialSecurityBenefitReductionEffectiveYear > 9999) {
            throw new IllegalArgumentException(
                    "Social Security benefit reduction effective year must be a four-digit calendar year.");
        }
    }

    /** Compatibility constructor retaining the pre-reduction JSON/value shape. */
    public EconomicAssumptions(
            BigDecimal expectedAnnualInvestmentReturn,
            BigDecimal legacyInflationRate,
            BigDecimal generalInflationRate,
            BigDecimal healthcareInflationRate,
            BigDecimal socialSecurityColaRate) {
        this(expectedAnnualInvestmentReturn, legacyInflationRate, generalInflationRate,
                healthcareInflationRate, socialSecurityColaRate, null, null);
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
                socialSecurityColaRate,
                null,
                null);
    }

    /** Full current economic-assumption constructor. */
    public EconomicAssumptions(
            BigDecimal expectedAnnualInvestmentReturn,
            BigDecimal generalInflationRate,
            BigDecimal healthcareInflationRate,
            BigDecimal socialSecurityColaRate,
            BigDecimal socialSecurityBenefitReductionPercent,
            int socialSecurityBenefitReductionEffectiveYear) {

        this(expectedAnnualInvestmentReturn, null, generalInflationRate,
                healthcareInflationRate, socialSecurityColaRate,
                socialSecurityBenefitReductionPercent,
                socialSecurityBenefitReductionEffectiveYear);
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

    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = ZeroPercentFilter.class)
    public BigDecimal getSocialSecurityBenefitReductionPercent() {
        return socialSecurityBenefitReductionPercent;
    }

    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = DefaultReductionYearFilter.class)
    public int getSocialSecurityBenefitReductionEffectiveYear() {
        return socialSecurityBenefitReductionEffectiveYear;
    }

    /** Omits the new default value from serialized legacy-shaped plans and fingerprints. */
    public static final class ZeroPercentFilter {
        @Override
        public boolean equals(Object other) {
            return other instanceof BigDecimal value && value.compareTo(BigDecimal.ZERO) == 0;
        }

        @Override
        public int hashCode() {
            return BigDecimal.ZERO.hashCode();
        }
    }

    /** Omits the new default value from serialized legacy-shaped plans and fingerprints. */
    public static final class DefaultReductionYearFilter {
        @Override
        public boolean equals(Object other) {
            return other instanceof Integer value && value == 2032;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(2032);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EconomicAssumptions that)) return false;
        return socialSecurityBenefitReductionEffectiveYear == that.socialSecurityBenefitReductionEffectiveYear
                && expectedAnnualInvestmentReturn.equals(that.expectedAnnualInvestmentReturn)
                && generalInflationRate.equals(that.generalInflationRate)
                && healthcareInflationRate.equals(that.healthcareInflationRate)
                && socialSecurityColaRate.equals(that.socialSecurityColaRate)
                && socialSecurityBenefitReductionPercent.equals(that.socialSecurityBenefitReductionPercent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expectedAnnualInvestmentReturn, generalInflationRate,
                healthcareInflationRate, socialSecurityColaRate,
                socialSecurityBenefitReductionPercent,
                socialSecurityBenefitReductionEffectiveYear);
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
                ", socialSecurityBenefitReductionPercent=" +
                socialSecurityBenefitReductionPercent +
                ", socialSecurityBenefitReductionEffectiveYear=" +
                socialSecurityBenefitReductionEffectiveYear +
                '}';
    }
}
