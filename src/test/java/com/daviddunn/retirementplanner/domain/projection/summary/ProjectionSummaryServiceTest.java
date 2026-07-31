package com.daviddunn.retirementplanner.domain.projection.summary;

import com.daviddunn.retirementplanner.domain.factory.RetirementPlanFactory;
import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatistics;
import com.daviddunn.retirementplanner.domain.projection.statistics.ProjectionStatisticsService;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProjectionSummaryServiceTest {

    private final ProjectionSummaryService summaryService =
            new ProjectionSummaryService(
                    new ProjectionStatisticsService(),
                    new IncomeSummaryService());

    @Test
    void summarizeReturnsOriginalProjection() {

        Projection projection = projection(
                projectionYear(100_000),
                projectionYear(150_000));

        ProjectionSummary summary =
                summaryService.summarize(
                        retirementPlan(),
                        projection);

        assertSame(
                projection,
                summary.getProjection());
    }

    @Test
    void summarizeCalculatesStatistics() {

        Projection projection = projection(
                projectionYear(900_000),
                projectionYear(750_000),
                projectionYear(800_000));

        ProjectionSummary summary =
                summaryService.summarize(
                        retirementPlan(),
                        projection);

        ProjectionStatistics statistics =
                summary.getStatistics();

        assertEquals(
                BigDecimal.valueOf(800_000),
                statistics.getEndingInvestableAssets());

        assertEquals(
                BigDecimal.valueOf(750_000),
                statistics.getLowestInvestableAssets());
    }

    @Test
    void summarizeCreatesIncomeSummary() {

        Projection projection = projection(
                projectionYear(100_000));

        ProjectionSummary summary =
                summaryService.summarize(
                        retirementPlan(),
                        projection);

        assertNotNull(
                summary.getIncomeSummary());
    }

    // -----------------------------------------------------------------
    // Test Helpers
    // -----------------------------------------------------------------

    private RetirementPlan retirementPlan() {
        return RetirementPlanFactory.createEmptyPlan();
    }

    private Projection projection(ProjectionYear... years) {

        Projection projection = new Projection();

        for (ProjectionYear year : years) {
            projection.addYear(year);
        }

        return projection;
    }

    private ProjectionYear projectionYear(
            long endingInvestableAssets) {

        return ProjectionYearBuilder.aProjectionYear()
                .withEndingInvestableAssets(
                        endingInvestableAssets)
                .build();
    }
}