package com.daviddunn.retirementplanner.domain.projection.statistics;

import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectionStatisticsServiceTest {

    private final ProjectionStatisticsService statisticsService =
            new ProjectionStatisticsService();

    @Test
    void calculateEndingInvestableAssets() {

        Projection projection = projection(
                projectionYear(100_000),
                projectionYear(125_000),
                projectionYear(150_000)
        );

        ProjectionStatistics statistics =
                statisticsService.calculate(projection);

        assertEquals(
                BigDecimal.valueOf(150_000),
                statistics.getEndingInvestableAssets());
    }

    @Test
    void calculateLowestInvestableAssets() {

        Projection projection = projection(
                projectionYear(900_000),
                projectionYear(750_000),
                projectionYear(800_000),
                projectionYear(950_000)
        );

        ProjectionStatistics statistics =
                statisticsService.calculate(projection);

        assertEquals(
                BigDecimal.valueOf(750_000),
                statistics.getLowestInvestableAssets());
    }

    @Test
    void singleProjectionYear() {

        Projection projection = projection(
                projectionYear(500_000)
        );

        ProjectionStatistics statistics =
                statisticsService.calculate(projection);

        assertEquals(
                BigDecimal.valueOf(500_000),
                statistics.getEndingInvestableAssets());

        assertEquals(
                BigDecimal.valueOf(500_000),
                statistics.getLowestInvestableAssets());
    }

    // -----------------------------------------------------------------
    // Test Helpers
    // -----------------------------------------------------------------

    private Projection projection(ProjectionYear... years) {

        Projection projection = new Projection();

        for (ProjectionYear year : years) {
            projection.addYear(year);
        }

        return projection;
    }

    private ProjectionYear projectionYear(long endingInvestableAssets) {

        return ProjectionYearBuilder.aProjectionYear()
                .withEndingInvestableAssets(endingInvestableAssets)
                .build();
    }
}