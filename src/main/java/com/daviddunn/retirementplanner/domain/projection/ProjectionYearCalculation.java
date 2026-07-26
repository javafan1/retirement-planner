package com.daviddunn.retirementplanner.domain.projection;

import java.util.Objects;

final class ProjectionYearCalculation {

    private final ProjectionYear projectionYear;
    private final ProjectedPortfolio endingPortfolio;

    ProjectionYearCalculation(
            ProjectionYear projectionYear,
            ProjectedPortfolio endingPortfolio) {

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

    ProjectedPortfolio getEndingPortfolio() {
        return endingPortfolio;
    }
}