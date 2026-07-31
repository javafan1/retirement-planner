package com.daviddunn.retirementplanner.domain.projection.summary;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatistics;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatisticsService;

import java.util.Objects;

public class ProjectionSummaryService {

    private final ProjectionStatisticsService statisticsService;
    private final IncomeSummaryService incomeSummaryService;

    public ProjectionSummaryService(
            ProjectionStatisticsService statisticsService,
            IncomeSummaryService incomeSummaryService) {

        this.statisticsService =
                Objects.requireNonNull(statisticsService);

        this.incomeSummaryService =
                Objects.requireNonNull(incomeSummaryService);
    }

    public ProjectionSummary summarize(
            RetirementPlan retirementPlan,
            Projection projection) {

        ProjectionStatistics statistics =
                statisticsService.calculate(
                        projection);

        IncomeSummary incomeSummary =
                incomeSummaryService.summarize(
                        retirementPlan);

        return new ProjectionSummary(
                projection,
                statistics,
                incomeSummary);
    }
}