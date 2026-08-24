
package com.daviddunn.retirementplanner.domain.roth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class RothConversionRequest {

    private final boolean enabled;
    private final int startYear;
    private final BigDecimal annualAmount;
    private final RothConversionStopRule stopRule;
    private final RothConversionFrequency frequency;
    private final RothConversionStrategy strategy;

    private final BigDecimal customTargetTaxableIncome;

    @JsonCreator
    public RothConversionRequest(
            @JsonProperty("enabled")
            boolean enabled,

            @JsonProperty("startYear")
            int startYear,

            @JsonProperty("annualAmount")
            BigDecimal annualAmount,

            @JsonProperty("stopRule")
            RothConversionStopRule stopRule,

            @JsonProperty("strategy")
            RothConversionStrategy strategy,

            @JsonProperty("frequency")
            RothConversionFrequency frequency,

            @JsonProperty("customTargetTaxableIncome")
            BigDecimal customTargetTaxableIncome) {

        if (startYear < 1900) {
            throw new IllegalArgumentException(
                    "Start year is invalid.");
        }

        Objects.requireNonNull(
                annualAmount,
                "Annual amount is required.");

        if (annualAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Annual amount cannot be negative.");
        }

        this.enabled =
                enabled;

        this.startYear =
                startYear;

        this.annualAmount =
                annualAmount;

        this.stopRule =
                stopRule;

        this.frequency =
                Objects.requireNonNull(
                        frequency,
                        "Frequency is required.");

        this.strategy =
                strategy == null
                        ? RothConversionStrategy.FIXED_AMOUNT
                        : strategy;

        validateCustomTargetTaxableIncome(
                this.strategy,
                customTargetTaxableIncome);

        this.customTargetTaxableIncome =
                customTargetTaxableIncome;

    }

    public RothConversionRequest(
            boolean enabled,
            int startYear,
            BigDecimal annualAmount,
            RothConversionStopRule stopRule,
            RothConversionStrategy strategy,
            RothConversionFrequency frequency) {

        this(
                enabled,
                startYear,
                annualAmount,
                stopRule,
                strategy,
                frequency,
                null);
    }

    public RothConversionStrategy getStrategy() {
        return strategy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getStartYear() {
        return startYear;
    }

    public BigDecimal getAnnualAmount() {
        return annualAmount;
    }

    public RothConversionStopRule getStopRule() {
        return stopRule;
    }

    public RothConversionFrequency getFrequency() {
        return frequency;
    }

    public BigDecimal getCustomTargetTaxableIncome() {
        return customTargetTaxableIncome;
    }

    private static void validateCustomTargetTaxableIncome(
            RothConversionStrategy strategy,
            BigDecimal customTargetTaxableIncome) {

        if (strategy ==
                RothConversionStrategy
                        .CUSTOM_TAXABLE_INCOME_TARGET) {

            Objects.requireNonNull(
                    customTargetTaxableIncome,
                    "Custom target taxable income is required.");

            if (customTargetTaxableIncome.signum() < 0) {
                throw new IllegalArgumentException(
                        "Custom target taxable income cannot be negative.");
            }

            return;
        }

        if (customTargetTaxableIncome != null) {
            throw new IllegalArgumentException(
                    "Custom target taxable income is only valid "
                            + "for the custom taxable-income strategy.");
        }
    }
}
