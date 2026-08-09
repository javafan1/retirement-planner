
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

    @JsonCreator
    public RothConversionRequest(
            @JsonProperty("enabled")
            boolean enabled,

            @JsonProperty("startYear")
            int startYear,

            @JsonProperty("annualAmount")
            BigDecimal annualAmount,

            @JsonProperty("stopRule")
            RothConversionStopRule stopRule) {

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
}

/*package com.daviddunn.retirementplanner.domain.roth;

import java.math.BigDecimal;
import java.util.Objects;

public final class RothConversionRequest {

    private final boolean enabled;

    private final int startYear;

    private final BigDecimal annualAmount;

    private final RothConversionStopRule stopRule;

    public RothConversionRequest(
            boolean enabled,
            int startYear,
            BigDecimal annualAmount,
            RothConversionStopRule stopRule) {

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
}



 */