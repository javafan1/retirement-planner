package com.daviddunn.retirementplanner.ui.charts;

import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.*;
import com.daviddunn.retirementplanner.testutil.ProjectionYearBuilder;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.daviddunn.retirementplanner.ui.charts.ProjectionChartFixtures.*;

class ProjectionChartModelTest {
    @Test void metricsAreExactExistingValuesWithoutInflationConversion() {
        var year = year(2027, 75, 40);
        var non = new NonInvestableAssetProjection(2027, List.of(), BigDecimal.valueOf(500));
        var model = ProjectionChartFixtures.model(List.of(year), List.of(non), people());
        var values = model.years().getFirst().values();
        long[] expected = {1000, 1500, 900, 100, 80, 12, 60, 40, 20, 500, 0, 75, 40};
        for (var metric : ProjectionChartMetric.values()) assertEquals(0,
                BigDecimal.valueOf(expected[metric.ordinal()]).compareTo(values.get(metric)), metric.toString());
        assertEquals(BigDecimal.valueOf(1000), year.getEndingInvestableAssets());
        assertThrows(UnsupportedOperationException.class, () -> values.clear());
    }

    @Test void classificationsIncludeCashAndRetainedAssetsExactlyOnce() {
        var point = ProjectionChartFixtures.model(List.of(year(2027, 0, 0)), List.of(), people()).years().getFirst();
        assertTrue(point.compositionComplete());
        assertEquals(BigDecimal.valueOf(300), point.composition().get(ProjectionAssetType.TAXABLE));
        assertEquals(BigDecimal.valueOf(400), point.composition().get(ProjectionAssetType.TAX_DEFERRED));
        assertEquals(BigDecimal.valueOf(300), point.composition().get(ProjectionAssetType.ROTH));
        assertEquals(BigDecimal.valueOf(1000), point.composition().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(0, BigDecimal.valueOf(30).compareTo(point.percentage(ProjectionAssetType.TAXABLE)));
        var zero = ProjectionChartFixtures.model(List.of(ProjectionYearBuilder.aProjectionYear().build()), List.of(), null).years().getFirst();
        for (var type : ProjectionAssetType.values()) assertEquals(BigDecimal.ZERO, zero.percentage(type));
        assertTrue(zero.compositionComplete());
        var incomplete = ProjectionChartFixtures.model(List.of(ProjectionYearBuilder.aProjectionYear().withEndingInvestableAssets(100).build()), List.of(), null);
        assertFalse(incomplete.years().getFirst().compositionComplete());
    }

    @Test void eventsUseExactCapturedStartDatesAndExcludeOutsideHorizon() {
        var model = ProjectionChartFixtures.model(List.of(year(2027, 0, 0), year(2035, 0, 0)), List.of(), people());
        assertEquals(List.of(2029, 2033), model.claims().stream().map(ProjectionChartModel.Claim::year).toList());
        assertEquals("Sam", model.claims().getFirst().person());
        assertEquals(62, model.claims().getFirst().age());
        assertEquals(people().spouse().retirementClaimDate(), model.claims().getFirst().date());
        assertTrue(ProjectionChartFixtures.model(List.of(year(2034, 0, 0)), List.of(), people()).claims().isEmpty());
    }

    @Test void actualTransactionsDetermineGroupedPeriodsAndKeepOverlap() {
        var model = ProjectionChartFixtures.model(List.of(year(2031, 75, 0), year(2032, 75, 30),
                year(2033, 75, 30), year(2034, 0, 30), year(2036, 75, 30), year(2037, 75, 0)), List.of(), people());
        assertEquals(List.of(new ProjectionChartModel.Period(2031, 2033), new ProjectionChartModel.Period(2036, 2037)), model.rothPeriods());
        assertEquals(List.of(new ProjectionChartModel.Period(2032, 2034), new ProjectionChartModel.Period(2036, 2036)), model.rmdPeriods());
        var overlap = model.years().get(1);
        assertEquals(BigDecimal.valueOf(75), overlap.values().get(ProjectionChartMetric.ROTH_CONVERSIONS));
        assertEquals(BigDecimal.valueOf(30), overlap.values().get(ProjectionChartMetric.RMD));
        // Fixture always has age 80, statutory RMD 999, preprojection RMD 100 and requested conversion 75000.
        var inactive = ProjectionChartFixtures.model(List.of(year(2031, 0, 0)), List.of(), people());
        assertTrue(inactive.rothPeriods().isEmpty());
        assertTrue(inactive.rmdPeriods().isEmpty());
    }

    @Test void emptyAndSingleYearAndDefensiveCopies() {
        assertEquals(ProjectionChartModel.empty(), ProjectionChartFixtures.model(List.of(), List.of(), people()));
        var one = ProjectionChartFixtures.model(List.of(year(2027, 1, 1)), List.of(), people());
        assertEquals(new ProjectionChartModel.Period(2027, 2027), one.rothPeriods().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> one.years().clear());
        assertThrows(IllegalArgumentException.class, () -> ProjectionChartFixtures.model(List.of(year(2027, 0, 0), year(2027, 0, 0)), List.of(), people()));
    }
}
