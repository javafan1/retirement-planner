package com.daviddunn.retirementplanner.domain.withdrawal;

import com.daviddunn.retirementplanner.domain.projection.ProjectedPortfolio;

import java.util.Objects;

public final class RothConversionExecutor {

    public ProjectedPortfolio execute(
            ProjectedPortfolio portfolio,
            RothConversionPlan plan) {

        Objects.requireNonNull(
                portfolio,
                "Portfolio is required.");

        Objects.requireNonNull(
                plan,
                "Roth conversion plan is required.");

        if (plan.getConversionAmount().signum() == 0) {
            return portfolio;
        }

        /*
         * Execution logic will be added incrementally.
         */
        return portfolio;
    }
}