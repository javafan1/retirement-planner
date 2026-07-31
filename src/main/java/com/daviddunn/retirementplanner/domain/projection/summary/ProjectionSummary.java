package com.daviddunn.retirementplanner.domain.projection.summary;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatistics;

import java.util.Objects;

public class ProjectionSummary {

    private final Projection projection;
    private final ProjectionStatistics statistics;
    private final IncomeSummary incomeSummary;

    public ProjectionSummary(
            Projection projection,
            ProjectionStatistics statistics,
            IncomeSummary incomeSummary) {

        this.projection =
                Objects.requireNonNull(
                        projection,
                        "Projection is required.");

        this.statistics =
                Objects.requireNonNull(
                        statistics,
                        "Statistics are required.");

        this.incomeSummary =
                Objects.requireNonNull(
                        incomeSummary,
                        "Income summary is required.");
    }

    public Projection getProjection() {
        return projection;
    }

    public ProjectionStatistics getStatistics() {
        return statistics;
    }

    public IncomeSummary getIncomeSummary() {
        return incomeSummary;
    }
}