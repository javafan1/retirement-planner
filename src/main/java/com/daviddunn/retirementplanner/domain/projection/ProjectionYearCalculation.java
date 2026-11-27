package com.daviddunn.retirementplanner.domain.projection;

import java.util.Objects;

final class ProjectionYearCalculation {

    private final ProjectionYear projectionYear;
    private final ProjectedPortfolio endingPortfolio;
    private final boolean positiveMandatoryRmd;

    ProjectionYearCalculation(
            ProjectionYear projectionYear,
            ProjectedPortfolio endingPortfolio, boolean positiveMandatoryRmd) {
        this.positiveMandatoryRmd = positiveMandatoryRmd;

        this.projectionYear =
                Objects.requireNonNull(
                        projectionYear,
                        "Projection year is required.");

        this.endingPortfolio =
                Objects.requireNonNull(
                        endingPortfolio,
                        "Ending projected portfolio is required.");
    }

    ProjectionYear getProjectionYear() {
        return projectionYear;
    }

    boolean positiveMandatoryRmd() { return positiveMandatoryRmd; }

    ProjectedPortfolio getEndingPortfolio() {
        return endingPortfolio;
    }
}