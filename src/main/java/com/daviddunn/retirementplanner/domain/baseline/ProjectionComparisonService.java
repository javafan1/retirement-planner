package com.daviddunn.retirementplanner.domain.baseline;

import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class ProjectionComparisonService {

    public ProjectionComparison compare(
            Projection baselineProjection,
            List<NonInvestableAssetProjection>
                    baselineNonInvestableAssets,
            Projection currentProjection,
            List<NonInvestableAssetProjection>
                    currentNonInvestableAssets,
            int calendarYear) {

        Objects.requireNonNull(
                baselineProjection,
                "Baseline projection is required.");

        Objects.requireNonNull(
                currentProjection,
                "Current projection is required.");

        Objects.requireNonNull(
                baselineNonInvestableAssets,
                "Baseline non-investable assets are required.");

        Objects.requireNonNull(
                currentNonInvestableAssets,
                "Current non-investable assets are required.");

        ProjectionYear baselineYear =
                findYear(
                        baselineProjection,
                        calendarYear);

        ProjectionYear currentYear =
                findYear(
                        currentProjection,
                        calendarYear);

        BigDecimal baselineNonInvestable =
                getNonInvestableValue(
                        baselineNonInvestableAssets,
                        calendarYear);

        BigDecimal currentNonInvestable =
                getNonInvestableValue(
                        currentNonInvestableAssets,
                        calendarYear);

        BigDecimal baselineNetWorth =
                baselineYear
                        .getEndingInvestableAssets()
                        .add(baselineNonInvestable);

        BigDecimal currentNetWorth =
                currentYear
                        .getEndingInvestableAssets()
                        .add(currentNonInvestable);

        BigDecimal baselinePeakInvestable =
                getPeakInvestableAssets(
                        baselineProjection);

        BigDecimal currentPeakInvestable =
                getPeakInvestableAssets(
                        currentProjection);

        return new ProjectionComparison(
                calendarYear,

                baselineYear
                        .getEndingInvestableAssets(),

                currentYear
                        .getEndingInvestableAssets(),

                baselineNonInvestable,

                currentNonInvestable,

                baselineNetWorth,

                currentNetWorth,

                baselineYear
                        .getAfterTaxEstateValue(),

                currentYear
                        .getAfterTaxEstateValue(),

                baselineYear
                        .getCombinedEffectiveTaxRate(),

                currentYear
                        .getCombinedEffectiveTaxRate(),

                baselinePeakInvestable,

                currentPeakInvestable);
    }


    private ProjectionYear findYear(
            Projection projection,
            int calendarYear) {

        return projection.getYears()
                .stream()
                .filter(year ->
                        year.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Projection does not contain "
                                        + "calendar year "
                                        + calendarYear));
    }


    private BigDecimal getNonInvestableValue(
            List<NonInvestableAssetProjection>
                    projections,
            int calendarYear) {

        return projections.stream()
                .filter(projection ->
                        projection.getCalendarYear()
                                == calendarYear)
                .findFirst()
                .map(
                        NonInvestableAssetProjection::
                                getTotalValue)
                .orElse(
                        BigDecimal.ZERO);
    }


    private BigDecimal getPeakInvestableAssets(
            Projection projection) {

        return projection.getYears()
                .stream()
                .map(
                        ProjectionYear::
                                getEndingInvestableAssets)
                .max(
                        BigDecimal::compareTo)
                .orElse(
                        BigDecimal.ZERO);
    }
}