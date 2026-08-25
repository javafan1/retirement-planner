package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.projection.CompoundGrowthService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class TaxParameterProjectionService {

    private final CompoundGrowthService
            compoundGrowthService;

    public TaxParameterProjectionService() {

        this.compoundGrowthService =
                new CompoundGrowthService();
    }

    public BigDecimal project(
            BigDecimal publishedValue,
            int publishedYear,
            int projectionYear,
            BigDecimal annualGrowthRate) {

        Objects.requireNonNull(
                publishedValue,
                "Published value is required.");

        Objects.requireNonNull(
                annualGrowthRate,
                "Annual growth rate is required.");

        if (projectionYear <= publishedYear) {
            return publishedValue;
        }

        int years =
                projectionYear - publishedYear;

        BigDecimal projectedValue =
                compoundGrowthService.project(
                        publishedValue,
                        annualGrowthRate,
                        years);

        return projectedValue.setScale(
                0,
                RoundingMode.HALF_UP);
    }
}
