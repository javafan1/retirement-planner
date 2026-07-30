package com.daviddunn.retirementplanner.domain.projection.statistics;

import com.daviddunn.retirementplanner.domain.projection.Projection;

import java.util.Objects;

public class ProjectionStatisticsService {

    public ProjectionStatistics calculate(Projection projection) {

        Objects.requireNonNull(
                projection,
                "Projection is required.");

        return new ProjectionStatistics(
                projection.getFinalInvestableAssets(),
                projection.getLowestInvestableAssets()
        );
    }
}