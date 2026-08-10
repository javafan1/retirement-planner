
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
            RothConversionFrequency frequency) {

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
                Objects.requireNonNull(
                        strategy,
                        "Strategy is required.");

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
}
