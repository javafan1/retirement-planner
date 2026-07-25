package com.daviddunn.retirementplanner.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Objects;

public final class FederalTaxBracket {

    private final BigDecimal lowerBound;
    private final BigDecimal upperBound;
    private final BigDecimal taxRate;

    @JsonCreator
    public FederalTaxBracket(

            @JsonProperty("lowerBound")
            BigDecimal lowerBound,

            @JsonProperty("upperBound")
            BigDecimal upperBound,

            @JsonProperty("taxRate")
            BigDecimal taxRate) {

        this.lowerBound =
                Objects.requireNonNull(
                        lowerBound,
                        "Lower bound is required.");

        /*
         * Upper bound may be null for the highest
         * tax bracket because it has no upper limit.
         */
        this.upperBound =
                upperBound;

        this.taxRate =
                Objects.requireNonNull(
                        taxRate,
                        "Tax rate is required.");

        if (lowerBound.signum() < 0) {
            throw new IllegalArgumentException(
                    "Lower bound cannot be negative.");
        }

        if (upperBound != null &&
                upperBound.compareTo(lowerBound) <= 0) {

            throw new IllegalArgumentException(
                    "Upper bound must be greater than lower bound.");
        }

        if (taxRate.signum() < 0 ||
                taxRate.compareTo(BigDecimal.ONE) > 0) {

            throw new IllegalArgumentException(
                    "Tax rate must be between 0 and 1.");
        }
    }

    public BigDecimal getLowerBound() {
        return lowerBound;
    }

    public BigDecimal getUpperBound() {
        return upperBound;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public boolean hasUpperBound() {
        return upperBound != null;
    }

    @Override
    public String toString() {

        return "FederalTaxBracket{" +
                "lowerBound=" + lowerBound +
                ", upperBound=" + upperBound +
                ", taxRate=" + taxRate +
                '}';
    }
}