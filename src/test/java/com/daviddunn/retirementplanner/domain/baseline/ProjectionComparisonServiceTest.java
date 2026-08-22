package com.daviddunn.retirementplanner.domain.baseline;

import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectionComparisonServiceTest {

    @Test
    void comparesProjectionsForRequestedYear() {

        Projection baselineProjection =
                new Projection();

        baselineProjection.addYear(
                new ProjectionYear(
                        1,
                        2040,
                        new BigDecimal("5000000"),
                        new BigDecimal("200000"),
                        new BigDecimal("100000"),
                        new BigDecimal("150000"),
                        new BigDecimal("50000"),
                        new BigDecimal("50000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("5000000"),
                        77));

        Projection currentProjection =
                new Projection();

        currentProjection.addYear(
                new ProjectionYear(
                        1,
                        2040,
                        new BigDecimal("5000000"),
                        new BigDecimal("250000"),
                        new BigDecimal("100000"),
                        new BigDecimal("150000"),
                        new BigDecimal("50000"),
                        new BigDecimal("50000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal("5300000"),
                        77));

        List<NonInvestableAssetProjection>
                baselineNonInvestableAssets =
                List.of(
                        new NonInvestableAssetProjection(
                                2040,
                                List.of(),
                                new BigDecimal("1000000")));

        List<NonInvestableAssetProjection>
                currentNonInvestableAssets =
                List.of(
                        new NonInvestableAssetProjection(
                                2040,
                                List.of(),
                                new BigDecimal("1100000")));

        ProjectionComparisonService service =
                new ProjectionComparisonService();

        ProjectionComparison comparison =
                service.compare(
                        baselineProjection,
                        baselineNonInvestableAssets,
                        currentProjection,
                        currentNonInvestableAssets,
                        2040);

        assertEquals(
                new BigDecimal("300000"),
                comparison
                        .getEndingInvestableAssetsChange());

        assertEquals(
                new BigDecimal("100000"),
                comparison
                        .getNonInvestableAssetsChange());

        assertEquals(
                new BigDecimal("400000"),
                comparison
                        .getNetWorthChange());

        assertEquals(
                2040,
                comparison.getCalendarYear());
    }
}