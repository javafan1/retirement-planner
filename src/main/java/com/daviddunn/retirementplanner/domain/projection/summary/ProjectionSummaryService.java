package com.daviddunn.retirementplanner.domain.projection.summary;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatistics;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatisticsService;

import java.util.Objects;

public class ProjectionSummaryService {

    private final ProjectionStatisticsService statisticsService;

    public ProjectionSummaryService(
            ProjectionStatisticsService statisticsService) {

        this.statisticsService =
                Objects.requireNonNull(statisticsService);
    }

    public ProjectionSummary summarize(
            Projection projection) {

        ProjectionStatistics statistics =
                statisticsService.calculate(projection);

        return new ProjectionSummary(
                projection,
                statistics);
    }
}