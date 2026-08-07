package com.daviddunn.retirementplanner.domain.withdrawal;

import java.math.BigDecimal;
import java.util.Objects;

public final class RothConversionPlan {

    private final BigDecimal conversionAmount;

    public RothConversionPlan(
            BigDecimal conversionAmount) {

        this.conversionAmount =
                Objects.requireNonNull(
                        conversionAmount,
                        "Conversion amount is required.");
    }

    public BigDecimal getConversionAmount() {
        return conversionAmount;
    }

    public boolean hasConversion() {
        return conversionAmount.signum() > 0;
    }

    @Override
    public String toString() {

        return "RothConversionResult{" +
                "conversionAmount=" + conversionAmount +
                '}';
    }
}