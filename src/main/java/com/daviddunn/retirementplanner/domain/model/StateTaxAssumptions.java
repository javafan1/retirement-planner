package com.daviddunn.retirementplanner.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Objects;

/** One selected full-year state for every projection year. No derived transactions are persisted. */
public record StateTaxAssumptions(
        StateTaxJurisdiction jurisdiction,
        CalculationMethod calculationMethod,
        BigDecimal effectiveRateOverride) {

    public enum CalculationMethod {
        SIMPLIFIED_STATE_ESTIMATE,
        EFFECTIVE_RATE_OVERRIDE;

        @JsonCreator
        public static CalculationMethod fromCode(String code) {
            return valueOf(Objects.requireNonNull(code, "State tax calculation method is required."));
        }
    }

    @JsonCreator
    public StateTaxAssumptions(
            @JsonProperty("jurisdiction") StateTaxJurisdiction jurisdiction,
            @JsonProperty("calculationMethod") CalculationMethod calculationMethod,
            @JsonProperty("effectiveRateOverride") BigDecimal effectiveRateOverride) {
        this.jurisdiction = Objects.requireNonNull(jurisdiction, "State of residence is required.");
        this.calculationMethod = Objects.requireNonNull(calculationMethod, "State tax calculation method is required.");
        if (calculationMethod != CalculationMethod.SIMPLIFIED_STATE_ESTIMATE) {
            throw new IllegalArgumentException("Effective state tax rate override is not enabled in this release.");
        }
        if (effectiveRateOverride != null) {
            throw new IllegalArgumentException("Simplified state estimates must not contain an override rate.");
        }
        this.effectiveRateOverride = null;
    }

    public static StateTaxAssumptions michigan() {
        return simplified(StateTaxJurisdiction.MI);
    }

    public static StateTaxAssumptions simplified(StateTaxJurisdiction jurisdiction) {
        return new StateTaxAssumptions(jurisdiction, CalculationMethod.SIMPLIFIED_STATE_ESTIMATE, null);
    }
}
