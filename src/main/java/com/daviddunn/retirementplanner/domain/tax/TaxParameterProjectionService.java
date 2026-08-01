package com.daviddunn.retirementplanner.domain.tax;

import com.daviddunn.retirementplanner.domain.model.PlanningAssumptions;
import com.daviddunn.retirementplanner.domain.rules.GovernmentRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class TaxParameterProjectionService {

    public BigDecimal project(
            BigDecimal publishedValue,
            int publishedYear,
            int projectionYear,
            PlanningAssumptions planningAssumptions) {

        if (projectionYear <= publishedYear) {
            return publishedValue;
        }

        int years =
                projectionYear - publishedYear;

        BigDecimal multiplier =
                BigDecimal.ONE
                        .add(planningAssumptions.getExpectedAnnualInflationRate())
                        .pow(years);

        return publishedValue
                .multiply(multiplier)
                .setScale(0, RoundingMode.HALF_UP);
    }
}

/*public final class TaxParameterProjectionService {

    public BigDecimal project(
            BigDecimal publishedValue,
            int publishedYear,
            int projectionYear,
            PlanningAssumptions planningAssumptions) {

        Objects.requireNonNull(
                publishedValue,
                "Published value is required.");

        Objects.requireNonNull(
                planningAssumptions,
                "Planning assumptions are required.");

        if (projectionYear <= publishedYear) {
            return publishedValue;
        }

        int years =
                projectionYear - publishedYear;

        BigDecimal inflationRate =
                planningAssumptions
                        .getExpectedAnnualInflationRate();

        BigDecimal inflationMultiplier =
                BigDecimal.ONE
                        .add(inflationRate)
                        .pow(years);

        return publishedValue
                .multiply(inflationMultiplier)
                .setScale(0, RoundingMode.HALF_UP);
    }

    public BigDecimal project(
            BigDecimal publishedValue,
            GovernmentRules governmentRules,
            int projectionYear,
            PlanningAssumptions planningAssumptions) {

        return project(
                publishedValue,
                governmentRules.getTaxYear(),
                projectionYear,
                planningAssumptions);
    }
}

 */