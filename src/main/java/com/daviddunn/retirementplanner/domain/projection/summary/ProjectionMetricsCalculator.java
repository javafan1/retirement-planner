package com.daviddunn.retirementplanner.domain.projection.summary;

import com.daviddunn.retirementplanner.domain.model.RetirementPlan;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjection;
import com.daviddunn.retirementplanner.domain.noninvestable.NonInvestableAssetProjectionService;
import com.daviddunn.retirementplanner.domain.projection.Projection;
import com.daviddunn.retirementplanner.domain.projection.ProjectionYear;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Shared non-JavaFX aggregation for Results Summary-compatible metrics. */
public final class ProjectionMetricsCalculator {

    private final NonInvestableAssetProjectionService nonInvestableService =
            new NonInvestableAssetProjectionService();

    public ProjectionMetrics calculate(
            RetirementPlan plan,
            Projection projection) {
        Objects.requireNonNull(plan, "Retirement plan is required.");
        Objects.requireNonNull(projection, "Projection is required.");
        List<ProjectionYear> years = projection.getYears();
        if (years.isEmpty()) {
            return zero();
        }

        ProjectionYear last = years.getLast();
        List<NonInvestableAssetProjection> nonInvestable =
                nonInvestableService.project(plan.getNonInvestableAssets(),
                        years.getFirst().getCalendarYear(), last.getCalendarYear());
        BigDecimal finalNonInvestable = nonInvestable.stream()
                .filter(value -> value.getCalendarYear() == last.getCalendarYear())
                .findFirst()
                .map(NonInvestableAssetProjection::getTotalValue)
                .orElse(BigDecimal.ZERO);

        return new ProjectionMetrics(
                sum(years, ProjectionYear::getInvestmentGrowth),
                sum(years, ProjectionYear::getGuaranteedIncome),
                sum(years, ProjectionYear::getTotalIncomeTax),
                years.stream().map(ProjectionYear::getTotalIncomeTax)
                        .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
                last.getEndingInvestableAssets(),
                last.getEndingInvestableAssets().add(finalNonInvestable),
                last.getAfterTaxEstateValue(),
                sum(years, ProjectionYear::getPortfolioWithdrawal),
                sum(years, ProjectionYear::getRothConversion),
                sum(years, ProjectionYear::getRequiredMinimumDistribution),
                sum(years, ProjectionYear::getAnnualMedicarePremium),
                years.stream().map(year -> year.getSocialSecurityResult().householdBenefit())
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                years.stream().map(year -> year.getSocialSecurityResult().primarySelectedBenefit())
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                years.stream().map(year -> year.getSocialSecurityResult().spouseSelectedBenefit())
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal sum(
            List<ProjectionYear> years,
            Function<ProjectionYear, BigDecimal> value) {
        return years.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static ProjectionMetrics zero() {
        return new ProjectionMetrics(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
