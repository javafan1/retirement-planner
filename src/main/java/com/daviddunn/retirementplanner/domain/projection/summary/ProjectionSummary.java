package com.daviddunn.retirementplanner.domain.projection.summary;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatistics;

import java.util.Objects;

public final class ProjectionSummary {

    private final Projection projection;
    private final ProjectionStatistics statistics;

    public ProjectionSummary(
            Projection projection,
            ProjectionStatistics statistics) {

        this.projection =
                Objects.requireNonNull(projection);

        this.statistics =
                Objects.requireNonNull(statistics);
    }

    public Projection getProjection() {
        return projection;
    }

    public ProjectionStatistics getStatistics() {
        return statistics;
    }
}