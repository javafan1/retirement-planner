package com.daviddunn.retirementplanner.domain.projection;

import java.math.BigDecimal;
import java.util.Objects;

public final class CompoundGrowthService {

    public BigDecimal project(
            BigDecimal startingValue,
            BigDecimal annualGrowthRate,
            int years) {

        Objects.requireNonNull(
                startingValue,
                "Starting value is required.");

        Objects.requireNonNull(
                annualGrowthRate,
                "Annual growth rate is required.");

        if (years < 0) {
            throw new IllegalArgumentException(
                    "Years cannot be negative.");
        }

        if (years == 0) {
            return startingValue;
        }

        BigDecimal multiplier =
                BigDecimal.ONE
                        .add(annualGrowthRate)
                        .pow(years);

        return startingValue.multiply(multiplier);
    }
}