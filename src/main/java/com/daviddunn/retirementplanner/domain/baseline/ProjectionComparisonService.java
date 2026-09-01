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
            List<NonInvestableAssetProjection> baselineNonInvestableAssets,
            Projection currentProjection,
            List<NonInvestableAssetProjection> currentNonInvestableAssets) {

        Objects.requireNonNull(baselineProjection, "Baseline projection is required.");
        Objects.requireNonNull(currentProjection, "Current projection is required.");
        Objects.requireNonNull(baselineNonInvestableAssets, "Baseline non-investable assets are required.");
        Objects.requireNonNull(currentNonInvestableAssets, "Current non-investable assets are required.");

        SummaryMetrics baseline = calculate(baselineProjection, baselineNonInvestableAssets);
        SummaryMetrics current = calculate(currentProjection, currentNonInvestableAssets);

        return new ProjectionComparison(
                current.calendarYear(),
                baseline.investmentGrowth(), current.investmentGrowth(),
                baseline.totalIncome(), current.totalIncome(),
                baseline.totalTaxes(), current.totalTaxes(),
                baseline.peakAnnualTax(), current.peakAnnualTax(),
                baseline.endingInvestableAssets(), current.endingInvestableAssets(),
                baseline.netWorth(), current.netWorth(),
                baseline.afterTaxEstate(), current.afterTaxEstate());
    }

    private SummaryMetrics calculate(
            Projection projection,
            List<NonInvestableAssetProjection> nonInvestableAssets) {

        List<ProjectionYear> years = projection.getYears();
        BigDecimal investmentGrowth = sum(years, ProjectionYear::getInvestmentGrowth);
        BigDecimal totalIncome = sum(years, ProjectionYear::getGuaranteedIncome);
        BigDecimal totalTaxes = sum(years, ProjectionYear::getTotalIncomeTax);
        BigDecimal peakAnnualTax = years.stream()
                .map(ProjectionYear::getTotalIncomeTax)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        if (years.isEmpty()) {
            return new SummaryMetrics(
                    0, investmentGrowth, totalIncome, totalTaxes,
                    peakAnnualTax, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        ProjectionYear finalYear = years.getLast();
        BigDecimal nonInvestable = nonInvestableAssets.stream()
                .filter(asset -> asset.getCalendarYear() == finalYear.getCalendarYear())
                .findFirst()
                .map(NonInvestableAssetProjection::getTotalValue)
                .orElse(BigDecimal.ZERO);

        return new SummaryMetrics(
                finalYear.getCalendarYear(), investmentGrowth, totalIncome, totalTaxes,
                peakAnnualTax, finalYear.getEndingInvestableAssets(),
                finalYear.getEndingInvestableAssets().add(nonInvestable),
                finalYear.getAfterTaxEstateValue());
    }

    private BigDecimal sum(
            List<ProjectionYear> years,
            java.util.function.Function<ProjectionYear, BigDecimal> value) {

        return years.stream()
                .map(value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record SummaryMetrics(
            int calendarYear,
            BigDecimal investmentGrowth,
            BigDecimal totalIncome,
            BigDecimal totalTaxes,
            BigDecimal peakAnnualTax,
            BigDecimal endingInvestableAssets,
            BigDecimal netWorth,
            BigDecimal afterTaxEstate) {
    }
}
